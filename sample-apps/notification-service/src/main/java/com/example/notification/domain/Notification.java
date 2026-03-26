package com.example.notification.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 알림 이력 도메인 엔티티.
 *
 * <p>JPA 엔티티로 notifications 테이블에 매핑된다.
 * Kafka로부터 수신한 주문 완료 이벤트를 처리한 이력을 저장한다.
 * setter를 열지 않고 상태 전이 메서드(markSent/markFailed)만 공개한다.</p>
 */
@Entity
@Table(name = "notifications")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification {

    /** 알림 식별자. DB AUTO_INCREMENT로 생성된다. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 알림 대상 주문 ID. order-service의 주문 ID와 대응된다. */
    @Column(nullable = false)
    private Long orderId;

    /** 알림 내용 메시지. */
    @Column(nullable = false)
    private String message;

    /** 알림 처리 상태. DB에는 문자열(ENUM 이름)로 저장된다. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationStatus status;

    /** 알림 최초 생성 시각. 한 번 저장된 후 변경하지 않는다(updatable=false). */
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * 알림 생성 빌더.
     *
     * <p>초기 상태는 항상 SENT이며, 생성 시각을 현재 시각으로 설정한다.</p>
     *
     * @param orderId 알림 대상 주문 ID
     * @param message 알림 내용 메시지
     * @param status  알림 처리 상태
     */
    @Builder
    public Notification(Long orderId, String message, NotificationStatus status) {
        this.orderId = orderId;
        this.message = message;
        this.status = status;
        this.createdAt = LocalDateTime.now();
    }
}
