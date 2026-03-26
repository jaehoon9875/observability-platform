package com.example.notification.dto;

import java.math.BigDecimal;

/**
 * order-service가 Kafka order-completed 토픽으로 발행하는 이벤트 DTO.
 *
 * <p>JSON 문자열로 직렬화된 메시지를 역직렬화하여 사용한다.
 * 필드 이름은 order-service의 발행 형식과 반드시 일치해야 한다.</p>
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
