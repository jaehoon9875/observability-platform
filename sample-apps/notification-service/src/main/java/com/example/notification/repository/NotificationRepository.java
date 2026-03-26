package com.example.notification.repository;

import com.example.notification.domain.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 알림 이력 JPA 레포지토리.
 *
 * <p>JpaRepository가 제공하는 기본 CRUD 메서드를 사용한다.</p>
 */
public interface NotificationRepository extends JpaRepository<Notification, Long> {
}
