package com.example.notification.consumer;

import com.example.notification.dto.OrderCompletedEvent;
import com.example.notification.service.NotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * order-completed 토픽을 구독하는 Kafka Consumer.
 *
 * <p>order-service가 주문 완료 시 발행한 JSON 메시지를 수신해
 * OrderCompletedEvent로 역직렬화한 후 NotificationService에 처리를 위임한다.</p>
 *
 * <p>역직렬화 실패 또는 처리 중 예외 발생 시 오류를 로그로 기록하고
 * 해당 메시지의 처리를 건너뛴다(재처리 로직은 추후 Dead Letter Queue로 확장 가능).</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationConsumer {

    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    /**
     * order-completed 토픽 메시지를 수신하여 알림을 처리한다.
     *
     * <p>수신된 JSON 문자열을 OrderCompletedEvent로 역직렬화한다.
     * 역직렬화 또는 처리 실패 시 에러 로그를 남기고 오프셋을 커밋해 재처리를 방지한다.</p>
     *
     * @param message Kafka로부터 수신된 JSON 문자열
     */
    @KafkaListener(topics = "order-completed", groupId = "notification-service")
    public void consume(String message) {
        log.debug("Kafka 메시지 수신: {}", message);

        try {
            OrderCompletedEvent event = objectMapper.readValue(message, OrderCompletedEvent.class);
            notificationService.processNotification(event);
        } catch (Exception e) {
            log.error("Kafka 메시지 처리 실패: message={}, error={}", message, e.getMessage(), e);
        }
    }
}
