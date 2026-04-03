package com.example.order.client;

import io.micrometer.core.annotation.Timed;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.Map;

/**
 * payment-service 호출 클라이언트.
 *
 * <p>RestTemplate을 사용해 payment-service의 POST /api/payments 엔드포인트를
 * 동기 HTTP 호출로 요청한다.</p>
 *
 * <p>호출 URL은 application.yml의 {@code app.payment-service.url} 설정값에서 주입된다.
 * K8s 환경에서는 ConfigMap의 PAYMENT_SERVICE_URL 환경변수로 덮어쓴다.</p>
 *
 * <p>OrderService에서 이 클라이언트 호출 시간을 {@code payment_call_duration_seconds}
 * 타이머로 측정한다.</p>
 */
@Slf4j
@Component
public class PaymentClient {

    private final RestTemplate restTemplate;
    private final String paymentServiceUrl;

    /**
     * PaymentClient 생성자.
     *
     * @param restTemplate      HTTP 호출에 사용할 RestTemplate
     * @param paymentServiceUrl payment-service 기본 URL (application.yml에서 주입)
     */
    public PaymentClient(RestTemplate restTemplate,
                         @Value("${app.payment-service.url}") String paymentServiceUrl) {
        this.restTemplate = restTemplate;
        this.paymentServiceUrl = paymentServiceUrl;
    }

    /**
     * payment-service에 결제를 요청한다.
     *
     * <p>POST {paymentServiceUrl}/api/payments 를 호출하고,
     * 결제 성공 시 정상 반환, 실패 시 예외를 던진다.</p>
     *
     * @param orderId 결제 대상 주문 ID
     * @param amount  결제 금액
     * @throws org.springframework.web.client.RestClientException HTTP 호출 실패 또는 4xx/5xx 응답 시
     */
    @Timed(value = "payment_call_duration_seconds", description = "payment-service 호출 시간", histogram = true)
    public void processPayment(Long orderId, BigDecimal amount) {
        String url = paymentServiceUrl + "/api/payments";
        Map<String, Object> request = Map.of("orderId", orderId, "amount", amount);

        log.info("결제 요청: url={}, orderId={}, amount={}", url, orderId, amount);
        restTemplate.postForObject(url, request, Object.class);
        log.info("결제 완료: orderId={}", orderId);
    }
}
