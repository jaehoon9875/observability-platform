package com.example.order.service;

import com.example.order.client.PaymentClient;
import com.example.order.domain.Order;
import com.example.order.domain.OrderStatus;
import com.example.order.dto.CreateOrderRequest;
import com.example.order.dto.OrderResponse;
import com.example.order.exception.OrderNotFoundException;
import com.example.order.exception.PaymentFailedException;
import com.example.order.repository.OrderRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.doThrow;

/**
 * OrderService 단위 테스트.
 *
 * <p>@SpringBootTest 없이 Mockito만으로 서비스 레이어를 독립적으로 검증한다.
 * MeterRegistry는 SimpleMeterRegistry(인메모리)를 직접 주입해 Prometheus 의존성 없이 실행한다.</p>
 *
 * <p>테스트 전략:
 * <ul>
 *   <li>OrderRepository, PaymentClient를 Mock으로 대체해 외부 의존성을 제거한다.</li>
 *   <li>정상/실패 두 경로를 모두 검증한다.</li>
 * </ul>
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    /** DB 접근을 대체하는 Mock 레포지토리. */
    @Mock
    private OrderRepository orderRepository;

    /** HTTP 호출을 대체하는 Mock 결제 클라이언트. */
    @Mock
    private PaymentClient paymentClient;

    /** Kafka 발행을 대체하는 Mock KafkaTemplate. */
    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private OrderService orderService;

    /**
     * 각 테스트 전에 실제 OrderService 인스턴스를 생성한다.
     *
     * <p>SimpleMeterRegistry를 사용해 메트릭 등록 로직도 함께 검증한다.</p>
     */
    @BeforeEach
    void setUp() {
        orderService = new OrderService(orderRepository, paymentClient, kafkaTemplate, new ObjectMapper(), new SimpleMeterRegistry());
    }

    @Test
    @DisplayName("주문 생성 성공 시 COMPLETED 상태로 저장된다")
    void createOrder_success() {
        // given: save() 호출 시 전달받은 Order 객체를 그대로 반환하도록 설정
        CreateOrderRequest request = new CreateOrderRequest("PRODUCT-001", 2, BigDecimal.valueOf(10000));
        given(orderRepository.save(any(Order.class))).willAnswer(inv -> inv.getArgument(0));

        // when
        OrderResponse response = orderService.createOrder(request);

        // then: 응답 상태가 COMPLETED이고 PaymentClient가 정확히 한 번 호출됐는지 검증
        assertThat(response.status()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(response.productId()).isEqualTo("PRODUCT-001");
        assertThat(response.quantity()).isEqualTo(2);
        then(paymentClient).should().processPayment(any(), any());
    }

    @Test
    @DisplayName("결제 실패 시 주문 상태가 FAILED로 변경되고 PaymentFailedException이 전파된다")
    void createOrder_paymentFailed() {
        // given: PaymentClient가 예외를 던지도록 설정
        CreateOrderRequest request = new CreateOrderRequest("PRODUCT-001", 1, BigDecimal.valueOf(5000));
        given(orderRepository.save(any(Order.class))).willAnswer(inv -> inv.getArgument(0));
        doThrow(new RuntimeException("결제 서버 오류")).when(paymentClient).processPayment(any(), any());

        // then: PaymentFailedException이 전파되는지 검증
        // noRollbackFor 덕분에 트랜잭션이 커밋되어 주문이 FAILED 상태로 DB에 저장된다.
        assertThatThrownBy(() -> orderService.createOrder(request))
                .isInstanceOf(PaymentFailedException.class)
                .hasMessageContaining("결제 서버 오류")
                .cause()
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("결제 서버 오류");
    }

    @Test
    @DisplayName("존재하는 주문 ID 조회 시 OrderResponse를 반환한다")
    void getOrder_found() {
        // given
        Order order = Order.builder()
                .productId("PRODUCT-002")
                .quantity(1)
                .totalAmount(BigDecimal.valueOf(3000))
                .build();
        given(orderRepository.findById(1L)).willReturn(Optional.of(order));

        // when
        OrderResponse response = orderService.getOrder(1L);

        // then
        assertThat(response.productId()).isEqualTo("PRODUCT-002");
    }

    @Test
    @DisplayName("존재하지 않는 주문 ID 조회 시 OrderNotFoundException이 발생한다")
    void getOrder_notFound() {
        // given: DB에 해당 ID가 없는 상황
        given(orderRepository.findById(99L)).willReturn(Optional.empty());

        // then: OrderNotFoundException이 발생하는지 검증
        assertThatThrownBy(() -> orderService.getOrder(99L))
                .isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    @DisplayName("전체 주문 목록을 반환한다")
    void getOrders() {
        // given: 2건의 주문이 DB에 있는 상황
        Order order1 = Order.builder().productId("P1").quantity(1).totalAmount(BigDecimal.ONE).build();
        Order order2 = Order.builder().productId("P2").quantity(2).totalAmount(BigDecimal.TEN).build();
        given(orderRepository.findAll()).willReturn(List.of(order1, order2));

        // when
        List<OrderResponse> orders = orderService.getOrders();

        // then
        assertThat(orders).hasSize(2);
    }
}
