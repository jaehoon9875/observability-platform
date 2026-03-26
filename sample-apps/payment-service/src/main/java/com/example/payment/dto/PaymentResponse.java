package com.example.payment.dto;

import com.example.payment.domain.Payment;
import com.example.payment.domain.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 결제 응답 DTO.
 *
 * <p>단건 조회 및 결제 처리 결과 응답에 사용된다.
 * 정적 팩토리 메서드 {@code from(Payment)}으로 엔티티를 DTO로 변환한다.</p>
 *
 * @param id            결제 ID
 * @param orderId       결제 대상 주문 ID
 * @param amount        결제 금액
 * @param status        결제 상태 (PENDING / COMPLETED / FAILED)
 * @param failureReason 결제 실패 사유 (성공 시 null)
 * @param createdAt     결제 생성 시각
 * @param updatedAt     결제 최종 수정 시각
 */
public record PaymentResponse(
        Long id,
        Long orderId,
        BigDecimal amount,
        PaymentStatus status,
        String failureReason,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    /**
     * Payment 엔티티를 PaymentResponse DTO로 변환하는 정적 팩토리 메서드.
     *
     * @param payment 변환 대상 Payment 엔티티
     * @return PaymentResponse DTO
     */
    public static PaymentResponse from(Payment payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getOrderId(),
                payment.getAmount(),
                payment.getStatus(),
                payment.getFailureReason(),
                payment.getCreatedAt(),
                payment.getUpdatedAt()
        );
    }
}
