package com.example.order.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/**
 * 주문 생성 요청 DTO.
 *
 * <p>Java Record로 선언해 불변 객체를 보장한다.
 * Bean Validation 어노테이션으로 컨트롤러 진입 전에 입력값을 검증한다.</p>
 *
 * @param productId   주문할 상품 ID (공백 불가)
 * @param quantity    주문 수량 (1 이상)
 * @param totalAmount 주문 총 금액 (양수)
 */
public record CreateOrderRequest(
        @NotBlank(message = "productId는 필수입니다.")
        String productId,

        @NotNull(message = "quantity는 필수입니다.")
        @Min(value = 1, message = "quantity는 1 이상이어야 합니다.")
        Integer quantity,

        @NotNull(message = "totalAmount는 필수입니다.")
        @Positive(message = "totalAmount는 양수여야 합니다.")
        BigDecimal totalAmount
) {}
