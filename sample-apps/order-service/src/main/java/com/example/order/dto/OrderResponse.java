package com.example.order.dto;

import com.example.order.domain.Order;
import com.example.order.domain.OrderStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 주문 조회/생성 응답 DTO.
 *
 * <p>Java Record로 선언해 불변 객체를 보장한다.
 * 엔티티를 직접 직렬화하지 않고 DTO로 변환해 API 계약과 내부 도메인을 분리한다.</p>
 *
 * @param id          주문 ID
 * @param productId   상품 ID
 * @param quantity    수량
 * @param totalAmount 총 금액
 * @param status      현재 주문 상태
 * @param createdAt   주문 생성 시각
 * @param updatedAt   마지막 상태 변경 시각
 */
public record OrderResponse(
        Long id,
        String productId,
        Integer quantity,
        BigDecimal totalAmount,
        OrderStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    /**
     * Order 엔티티를 OrderResponse DTO로 변환하는 정적 팩토리 메서드.
     *
     * @param order 변환할 주문 엔티티
     * @return 변환된 응답 DTO
     */
    public static OrderResponse from(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getProductId(),
                order.getQuantity(),
                order.getTotalAmount(),
                order.getStatus(),
                order.getCreatedAt(),
                order.getUpdatedAt()
        );
    }
}
