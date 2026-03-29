// CI 파이프라인 동작 검증용 커밋 (notification-service)
package com.example.notification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * notification-service 스프링 부트 애플리케이션 진입점.
 *
 * <p>order-service가 Kafka order-completed 토픽으로 발행한 주문 완료 이벤트를
 * 구독하여 알림을 처리하는 서비스다.</p>
 */
@SpringBootApplication
public class NotificationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }
}
