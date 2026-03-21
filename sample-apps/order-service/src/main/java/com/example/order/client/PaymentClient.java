package com.example.order.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * payment-service 호출 클라이언트.
 *
 * <p>현재는 1단계 구현으로 stub 상태다.
 * 2단계에서 payment-service를 구현한 후 RestTemplate/WebClient 기반의
 * 실제 HTTP 호출로 교체한다.</p>
 *
 * <p>OrderService에서 이 클라이언트 호출 시간을 {@code payment_call_duration_seconds}
 * 타이머로 측정한다.</p>
 */
@Slf4j
@Component
public class PaymentClient {

    /**
     * payment-service에 결제를 요청한다.
     *
     * <p>현재는 로그만 출력하는 stub이다.
     * 2단계에서 실제 REST 호출로 교체할 예정이다.</p>
     *
     * @param orderId 결제 대상 주문 ID
     * @param amount  결제 금액
     */
    public void processPayment(Long orderId, BigDecimal amount) {
        log.info("결제 요청 (stub): orderId={}, amount={}", orderId, amount);
    }
}
