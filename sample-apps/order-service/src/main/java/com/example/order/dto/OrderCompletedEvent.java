package com.example.order.dto;

import java.math.BigDecimal;

/**
 * Kafka order-completed 토픽으로 발행하는 주문 완료 이벤트 DTO.
 *
 * <p>notification-service가 이 이벤트를 구독해 알림을 처리한다.
 * 필드 이름은 notification-service의 OrderCompletedEvent와 반드시 일치해야 한다.</p>
 *
 * @param orderId     완료된 주문 ID
 * @param productId   주문 상품 ID
 * @param totalAmount 주문 총 금액
 */
public record OrderCompletedEvent(
        Long orderId,
        String productId,
        BigDecimal totalAmount
) {
}
