package com.example.payment.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/**
 * 결제 처리 요청 DTO.
 *
 * <p>order-service에서 POST /api/payments 호출 시 전달하는 요청 본문이다.
 * Bean Validation 어노테이션으로 입력값을 검증한다.</p>
 *
 * @param orderId 결제 대상 주문 ID (null 불가)
 * @param amount  결제 금액 (null 불가, 0보다 커야 함)
 */
public record ProcessPaymentRequest(

        @NotNull(message = "주문 ID는 필수입니다.")
        Long orderId,

        @NotNull(message = "결제 금액은 필수입니다.")
        @Positive(message = "결제 금액은 0보다 커야 합니다.")
        BigDecimal amount
) {}
