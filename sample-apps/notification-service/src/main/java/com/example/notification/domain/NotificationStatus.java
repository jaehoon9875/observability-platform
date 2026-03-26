package com.example.notification.domain;

/**
 * 알림 처리 상태를 나타내는 열거형.
 *
 * <p>SENT: 알림이 성공적으로 처리됨</p>
 * <p>FAILED: 알림 처리 중 오류 발생</p>
 */
public enum NotificationStatus {
    SENT,
    FAILED
}
