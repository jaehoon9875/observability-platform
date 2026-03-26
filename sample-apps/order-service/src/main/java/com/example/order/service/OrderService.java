package com.example.order.service;

import com.example.order.client.PaymentClient;
import com.example.order.domain.Order;
import com.example.order.dto.CreateOrderRequest;
import com.example.order.dto.OrderCompletedEvent;
import com.example.order.dto.OrderResponse;
import com.example.order.exception.OrderNotFoundException;
import com.example.order.repository.OrderRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 주문 비즈니스 로직을 담당하는 서비스.
 *
 * <p>주문 생성 흐름:
 * <ol>
 *   <li>주문 엔티티를 PENDING 상태로 DB에 저장한다.</li>
 *   <li>상태를 PROCESSING으로 전이한다.</li>
 *   <li>payment-service를 호출해 결제를 처리한다.</li>
 *   <li>결제 성공 시 COMPLETED, 실패 시 FAILED로 전이한다.</li>
 * </ol>
 * </p>
 *
 * <p>Micrometer 커스텀 메트릭을 생성자에서 직접 등록한다.
 * {@code /actuator/prometheus}에서 Prometheus 포맷으로 노출된다.</p>
 */
@Slf4j
@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final PaymentClient paymentClient;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    /** Kafka 이벤트를 발행할 토픽명. */
    private static final String ORDER_COMPLETED_TOPIC = "order-completed";

    /** 성공적으로 완료된 주문 수 카운터. 메트릭명: order_created_total */
    private final Counter orderCreatedCounter;

    /** 처리 중 실패한 주문 수 카운터. 메트릭명: order_failed_total */
    private final Counter orderFailedCounter;

    /**
     * 주문 처리 전체 소요 시간 타이머.
     * 메트릭명: order_processing_duration_seconds
     * P50/P95/P99 퍼센타일을 함께 노출한다.
     */
    private final Timer orderProcessingTimer;

    /**
     * payment-service 호출 소요 시간 타이머.
     * 메트릭명: payment_call_duration_seconds
     * P50/P95/P99 퍼센타일을 함께 노출한다.
     */
    private final Timer paymentCallTimer;

    /**
     * 생성자에서 Micrometer 메트릭을 MeterRegistry에 등록한다.
     *
     * <p>Counter/Timer를 빈 필드에 직접 보관해 매 호출마다 lookup 비용 없이 재사용한다.</p>
     *
     * @param orderRepository 주문 JPA 레포지토리
     * @param paymentClient   payment-service 호출 클라이언트
     * @param meterRegistry   Micrometer 메트릭 레지스트리 (Prometheus 구현체가 자동 주입됨)
     */
    public OrderService(OrderRepository orderRepository,
                        PaymentClient paymentClient,
                        KafkaTemplate<String, String> kafkaTemplate,
                        ObjectMapper objectMapper,
                        MeterRegistry meterRegistry) {
        this.orderRepository = orderRepository;
        this.paymentClient = paymentClient;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;

        this.orderCreatedCounter = Counter.builder("order_created_total")
                .description("생성된 주문 수")
                .register(meterRegistry);

        this.orderFailedCounter = Counter.builder("order_failed_total")
                .description("실패한 주문 수")
                .register(meterRegistry);

        this.orderProcessingTimer = Timer.builder("order_processing_duration_seconds")
                .description("주문 처리 소요 시간")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(meterRegistry);

        this.paymentCallTimer = Timer.builder("payment_call_duration_seconds")
                .description("payment-service 호출 시간")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(meterRegistry);
    }

    /**
     * 주문을 생성하고 결제를 처리한다.
     *
     * <p>전체 처리 시간은 {@code order_processing_duration_seconds} 타이머로,
     * 결제 호출 시간은 {@code payment_call_duration_seconds} 타이머로 각각 측정된다.</p>
     *
     * @param request 주문 생성 요청 DTO
     * @return 저장된 주문 정보
     * @throws RuntimeException 결제 처리 중 예외 발생 시 (주문은 FAILED 상태로 남음)
     */
    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request) {
        // orderProcessingTimer.record()로 감싸 주문 처리 전체 시간을 측정한다.
        return orderProcessingTimer.record(() -> {
            Order order = Order.builder()
                    .productId(request.productId())
                    .quantity(request.quantity())
                    .totalAmount(request.totalAmount())
                    .build();
            orderRepository.save(order);
            order.processing(); // 결제 요청 직전에 PROCESSING으로 전이

            try {
                // paymentCallTimer.record()로 감싸 결제 호출 시간만 별도로 측정한다.
                paymentCallTimer.record(() ->
                        paymentClient.processPayment(order.getId(), order.getTotalAmount())
                );
                order.complete();
                orderCreatedCounter.increment(); // 성공 카운터 증가
                publishOrderCompletedEvent(order);
                log.info("주문 생성 완료: orderId={}", order.getId());
            } catch (Exception e) {
                order.fail();
                orderFailedCounter.increment(); // 실패 카운터 증가
                log.error("주문 실패: orderId={}, error={}", order.getId(), e.getMessage());
                throw e; // 예외를 다시 던져 트랜잭션 롤백을 유도한다.
            }

            return OrderResponse.from(order);
        });
    }

    /**
     * 주문 완료 이벤트를 Kafka order-completed 토픽으로 발행한다.
     *
     * <p>이벤트 발행 실패는 주문 처리 결과에 영향을 주지 않는다.
     * 발행 실패 시 에러 로그를 남기고 계속 진행한다.</p>
     *
     * @param order 완료된 주문 엔티티
     */
    private void publishOrderCompletedEvent(Order order) {
        try {
            OrderCompletedEvent event = new OrderCompletedEvent(
                    order.getId(),
                    order.getProductId(),
                    order.getTotalAmount()
            );
            String payload = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(ORDER_COMPLETED_TOPIC, String.valueOf(order.getId()), payload);
            log.info("주문 완료 이벤트 발행: orderId={}", order.getId());
        } catch (JsonProcessingException e) {
            log.error("주문 완료 이벤트 직렬화 실패: orderId={}, error={}", order.getId(), e.getMessage());
        }
    }

    /**
     * 단건 주문을 조회한다.
     *
     * @param id 주문 ID
     * @return 주문 응답 DTO
     * @throws OrderNotFoundException 해당 ID의 주문이 존재하지 않을 때
     */
    @Transactional(readOnly = true)
    public OrderResponse getOrder(Long id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new OrderNotFoundException(id));
        return OrderResponse.from(order);
    }

    /**
     * 전체 주문 목록을 조회한다.
     *
     * @return 주문 응답 DTO 리스트 (주문이 없으면 빈 리스트)
     */
    @Transactional(readOnly = true)
    public List<OrderResponse> getOrders() {
        return orderRepository.findAll().stream()
                .map(OrderResponse::from)
                .toList();
    }
}
