package com.example.payment.service;

import com.example.payment.domain.Payment;
import com.example.payment.domain.PaymentStatus;
import com.example.payment.dto.PaymentResponse;
import com.example.payment.dto.ProcessPaymentRequest;
import com.example.payment.exception.PaymentNotFoundException;
import com.example.payment.repository.PaymentRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

/**
 * PaymentService 단위 테스트.
 *
 * <p>@SpringBootTest 없이 Mockito만으로 서비스 레이어를 독립적으로 검증한다.
 * MeterRegistry는 SimpleMeterRegistry(인메모리)를 직접 주입해 Prometheus 의존성 없이 실행한다.</p>
 *
 * <p>테스트 전략:
 * <ul>
 *   <li>PaymentRepository를 Mock으로 대체해 DB 의존성을 제거한다.</li>
 *   <li>SimpleMeterRegistry를 활용해 메트릭 카운터 증감도 함께 검증한다.</li>
 *   <li>정상/예외 두 경로를 모두 검증한다.</li>
 * </ul>
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    /** DB 접근을 대체하는 Mock 레포지토리. */
    @Mock
    private PaymentRepository paymentRepository;

    private PaymentService paymentService;

    /** 메트릭 검증에 사용할 인메모리 레지스트리. */
    private SimpleMeterRegistry meterRegistry;

    /**
     * 각 테스트 전에 실제 PaymentService 인스턴스를 생성한다.
     *
     * <p>SimpleMeterRegistry를 사용해 메트릭 등록 로직도 함께 검증한다.</p>
     */
    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        paymentService = new PaymentService(paymentRepository, meterRegistry);
    }

    @Test
    @DisplayName("결제 처리 성공 시 COMPLETED 상태로 저장되고 성공 카운터가 증가한다")
    void processPayment_success() {
        // given: save() 호출 시 전달받은 Payment 객체를 그대로 반환하도록 설정
        ProcessPaymentRequest request = new ProcessPaymentRequest(1L, BigDecimal.valueOf(10000));
        given(paymentRepository.save(any(Payment.class))).willAnswer(inv -> inv.getArgument(0));

        // when
        PaymentResponse response = paymentService.processPayment(request);

        // then: 응답 상태가 COMPLETED이고 주문 ID/금액이 일치하는지 검증
        assertThat(response.status()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(response.orderId()).isEqualTo(1L);
        assertThat(response.amount()).isEqualByComparingTo(BigDecimal.valueOf(10000));
        assertThat(response.failureReason()).isNull();

        // 레포지토리에 저장이 한 번 호출됐는지 검증
        then(paymentRepository).should().save(any(Payment.class));

        // payment_processed_total 카운터가 1 증가했는지 검증
        Counter processedCounter = meterRegistry.find("payment_processed_total").counter();
        assertThat(processedCounter).isNotNull();
        assertThat(processedCounter.count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("결제 처리 성공 시 실패 카운터는 증가하지 않는다")
    void processPayment_success_failedCounterNotIncremented() {
        // given
        ProcessPaymentRequest request = new ProcessPaymentRequest(2L, BigDecimal.valueOf(5000));
        given(paymentRepository.save(any(Payment.class))).willAnswer(inv -> inv.getArgument(0));

        // when
        paymentService.processPayment(request);

        // then: payment_failed_total 카운터는 0으로 유지돼야 한다
        Counter failedCounter = meterRegistry.find("payment_failed_total").counter();
        assertThat(failedCounter).isNotNull();
        assertThat(failedCounter.count()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("존재하는 결제 ID 조회 시 PaymentResponse를 반환한다")
    void getPayment_found() {
        // given
        Payment payment = Payment.builder()
                .orderId(10L)
                .amount(BigDecimal.valueOf(20000))
                .build();
        given(paymentRepository.findById(1L)).willReturn(Optional.of(payment));

        // when
        PaymentResponse response = paymentService.getPayment(1L);

        // then
        assertThat(response.orderId()).isEqualTo(10L);
        assertThat(response.amount()).isEqualByComparingTo(BigDecimal.valueOf(20000));
        assertThat(response.status()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    @DisplayName("존재하지 않는 결제 ID 조회 시 PaymentNotFoundException이 발생한다")
    void getPayment_notFound() {
        // given: DB에 해당 ID가 없는 상황
        given(paymentRepository.findById(99L)).willReturn(Optional.empty());

        // then: PaymentNotFoundException이 발생하는지 검증
        assertThatThrownBy(() -> paymentService.getPayment(99L))
                .isInstanceOf(PaymentNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    @DisplayName("전체 결제 목록을 반환한다")
    void getPayments() {
        // given: 2건의 결제가 DB에 있는 상황
        Payment payment1 = Payment.builder().orderId(1L).amount(BigDecimal.valueOf(1000)).build();
        Payment payment2 = Payment.builder().orderId(2L).amount(BigDecimal.valueOf(2000)).build();
        given(paymentRepository.findAll()).willReturn(List.of(payment1, payment2));

        // when
        List<PaymentResponse> payments = paymentService.getPayments();

        // then
        assertThat(payments).hasSize(2);
        assertThat(payments).extracting(PaymentResponse::orderId)
                .containsExactly(1L, 2L);
    }

    @Test
    @DisplayName("결제 목록이 없을 경우 빈 리스트를 반환한다")
    void getPayments_empty() {
        // given
        given(paymentRepository.findAll()).willReturn(List.of());

        // when
        List<PaymentResponse> payments = paymentService.getPayments();

        // then
        assertThat(payments).isEmpty();
    }
}
