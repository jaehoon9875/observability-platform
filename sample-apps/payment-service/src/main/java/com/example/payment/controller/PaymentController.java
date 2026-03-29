// CI 파이프라인 동작 검증용 커밋 (payment-service)
package com.example.payment.controller;

import com.example.payment.dto.PaymentResponse;
import com.example.payment.dto.ProcessPaymentRequest;
import com.example.payment.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 결제 REST API 컨트롤러.
 *
 * <p>모든 엔드포인트는 /api/payments 경로 하위에 위치한다.
 * 입력값 검증(@Valid)은 컨트롤러에서 수행하고, 비즈니스 로직은 PaymentService에 위임한다.</p>
 *
 * <p>Swagger UI 접근: /swagger-ui.html</p>
 */
@Tag(name = "Payment", description = "결제 API")
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    /**
     * 결제를 처리한다.
     *
     * <p>POST /api/payments<br>
     * order-service에서 주문 생성 시 동기적으로 호출된다.
     * 요청 본문의 유효성 검증 실패 시 400 Bad Request를 반환한다.
     * 정상 처리 시 201 Created와 함께 처리된 결제 정보를 반환한다.</p>
     *
     * @param request 결제 처리 요청 DTO (@Valid로 Bean Validation 수행)
     * @return 처리된 결제 정보
     */
    @Operation(summary = "결제 처리")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentResponse processPayment(@Valid @RequestBody ProcessPaymentRequest request) {
        return paymentService.processPayment(request);
    }

    /**
     * 단건 결제를 조회한다.
     *
     * <p>GET /api/payments/{id}<br>
     * 존재하지 않는 ID 요청 시 404 Not Found를 반환한다.</p>
     *
     * @param id 조회할 결제 ID (경로 변수)
     * @return 결제 상세 정보
     */
    @Operation(summary = "결제 단건 조회")
    @GetMapping("/{id}")
    public PaymentResponse getPayment(@PathVariable Long id) {
        return paymentService.getPayment(id);
    }

    /**
     * 전체 결제 목록을 조회한다.
     *
     * <p>GET /api/payments<br>
     * 결제가 없으면 빈 배열을 반환한다.</p>
     *
     * @return 결제 목록
     */
    @Operation(summary = "결제 목록 조회")
    @GetMapping
    public List<PaymentResponse> getPayments() {
        return paymentService.getPayments();
    }
}
