package com.example.notification.dto;

import com.example.notification.domain.Notification;
import com.example.notification.domain.NotificationStatus;

import java.time.LocalDateTime;

/**
 * 알림 응답 DTO.
 *
 * <p>단건 조회 및 목록 조회 응답에 사용된다.
 * 정적 팩토리 메서드 {@code from(Notification)}으로 엔티티를 DTO로 변환한다.</p>
 *
 * @param id        알림 ID
 * @param orderId   알림 대상 주문 ID
 * @param message   알림 내용
 * @param status    알림 처리 상태 (SENT / FAILED)
 * @param createdAt 알림 생성 시각
 */
public record NotificationResponse(
        Long id,
        Long orderId,
        String message,
        NotificationStatus status,
        LocalDateTime createdAt
) {

    /**
     * Notification 엔티티를 NotificationResponse DTO로 변환하는 정적 팩토리 메서드.
     *
     * @param notification 변환 대상 Notification 엔티티
     * @return NotificationResponse DTO
     */
    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getOrderId(),
                notification.getMessage(),
                notification.getStatus(),
                notification.getCreatedAt()
        );
    }
}
