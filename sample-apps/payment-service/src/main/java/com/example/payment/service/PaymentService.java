package com.example.payment.service;

import com.example.payment.domain.Payment;
import com.example.payment.dto.PaymentResponse;
import com.example.payment.dto.ProcessPaymentRequest;
import com.example.payment.exception.PaymentNotFoundException;
import com.example.payment.repository.PaymentRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 결제 비즈니스 로직을 담당하는 서비스.
 *
 * <p>결제 처리 흐름:
 * <ol>
 *   <li>결제 엔티티를 PENDING 상태로 DB에 저장한다.</li>
 *   <li>결제 처리 로직을 수행한다.</li>
 *   <li>성공 시 COMPLETED, 실패 시 FAILED로 상태를 전이한다.</li>
 * </ol>
 * </p>
 *
 * <p>Micrometer 커스텀 메트릭을 생성자에서 직접 등록한다.
 * {@code /actuator/prometheus}에서 Prometheus 포맷으로 노출된다.</p>
 */
@Slf4j
@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;

    /** 결제 성공 카운터. 메트릭명: payment_processed_total */
    private final Counter paymentProcessedCounter;

    /** 결제 실패 카운터. 메트릭명: payment_failed_total */
    private final Counter paymentFailedCounter;

    /**
     * 결제 처리 전체 소요 시간 타이머.
     * 메트릭명: payment_processing_duration_seconds
     * P50/P95/P99 퍼센타일을 함께 노출한다.
     */
    private final Timer paymentProcessingTimer;

    /**
     * 생성자에서 Micrometer 메트릭을 MeterRegistry에 등록한다.
     *
     * <p>Counter/Timer를 빈 필드에 직접 보관해 매 호출마다 lookup 비용 없이 재사용한다.</p>
     *
     * @param paymentRepository 결제 JPA 레포지토리
     * @param meterRegistry     Micrometer 메트릭 레지스트리 (Prometheus 구현체가 자동 주입됨)
     */
    public PaymentService(PaymentRepository paymentRepository, MeterRegistry meterRegistry) {
        this.paymentRepository = paymentRepository;

        this.paymentProcessedCounter = Counter.builder("payment_processed_total")
                .description("성공적으로 처리된 결제 수")
                .register(meterRegistry);

        this.paymentFailedCounter = Counter.builder("payment_failed_total")
                .description("실패한 결제 수")
                .register(meterRegistry);

        this.paymentProcessingTimer = Timer.builder("payment_processing_duration_seconds")
                .description("결제 처리 소요 시간")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(meterRegistry);
    }

    /**
     * 결제를 처리한다.
     *
     * <p>결제 처리 시간은 {@code payment_processing_duration_seconds} 타이머로 측정된다.
     * 성공/실패 여부는 각각 {@code payment_processed_total}, {@code payment_failed_total}
     * 카운터로 집계된다.</p>
     *
     * @param request 결제 처리 요청 DTO (orderId, amount)
     * @return 처리된 결제 정보
     */
    @Transactional
    public PaymentResponse processPayment(ProcessPaymentRequest request) {
        return paymentProcessingTimer.record(() -> {
            Payment payment = Payment.builder()
                    .orderId(request.orderId())
                    .amount(request.amount())
                    .build();
            paymentRepository.save(payment);

            try {
                // 결제 처리 로직 수행
                executePayment(payment);
                payment.complete();
                paymentProcessedCounter.increment();
                log.info("결제 처리 완료: paymentId={}, orderId={}, amount={}",
                        payment.getId(), payment.getOrderId(), payment.getAmount());
            } catch (Exception e) {
                payment.fail(e.getMessage());
                paymentFailedCounter.increment();
                log.error("결제 처리 실패: paymentId={}, orderId={}, error={}",
                        payment.getId(), payment.getOrderId(), e.getMessage());
                throw e;
            }

            return PaymentResponse.from(payment);
        });
    }

    /**
     * 단건 결제를 조회한다.
     *
     * @param id 결제 ID
     * @return 결제 응답 DTO
     * @throws PaymentNotFoundException 해당 ID의 결제가 존재하지 않을 때
     */
    @Transactional(readOnly = true)
    public PaymentResponse getPayment(Long id) {
        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new PaymentNotFoundException(id));
        return PaymentResponse.from(payment);
    }

    /**
     * 전체 결제 목록을 조회한다.
     *
     * @return 결제 응답 DTO 리스트 (결제가 없으면 빈 리스트)
     */
    @Transactional(readOnly = true)
    public List<PaymentResponse> getPayments() {
        return paymentRepository.findAll().stream()
                .map(PaymentResponse::from)
                .toList();
    }

    /**
     * 실제 결제 처리를 수행하는 내부 메서드.
     *
     * <p>현재는 항상 성공하는 구현이다.
     * 3단계에서 외부 PG사 연동 또는 실패 시뮬레이션 로직으로 교체할 수 있다.</p>
     *
     * @param payment 처리할 결제 엔티티
     */
    private void executePayment(Payment payment) {
        log.debug("결제 처리 중: orderId={}, amount={}", payment.getOrderId(), payment.getAmount());
        // 실제 PG사 연동 또는 비즈니스 검증 로직이 여기에 추가된다.
    }
}
