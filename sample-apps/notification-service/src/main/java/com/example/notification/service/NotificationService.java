package com.example.notification.service;

import com.example.notification.domain.Notification;
import com.example.notification.domain.NotificationStatus;
import com.example.notification.dto.NotificationResponse;
import com.example.notification.dto.OrderCompletedEvent;
import com.example.notification.exception.NotificationNotFoundException;
import com.example.notification.repository.NotificationRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 알림 비즈니스 로직을 담당하는 서비스.
 *
 * <p>알림 처리 흐름:
 * <ol>
 *   <li>Kafka로부터 OrderCompletedEvent를 수신한다.</li>
 *   <li>알림 메시지를 생성하고 이력을 DB에 저장한다.</li>
 *   <li>성공 시 SENT, 실패 시 FAILED 상태로 저장한다.</li>
 * </ol>
 * </p>
 *
 * <p>Micrometer 커스텀 메트릭을 생성자에서 직접 등록한다.
 * {@code /actuator/prometheus}에서 Prometheus 포맷으로 노출된다.</p>
 */
@Slf4j
@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;

    /** Kafka로 수신된 이벤트 수 카운터. 메트릭명: notification_received_total */
    private final Counter notificationReceivedCounter;

    /** 처리 성공 알림 수 카운터. 메트릭명: notification_processed_total */
    private final Counter notificationProcessedCounter;

    /** 처리 실패 알림 수 카운터. 메트릭명: notification_failed_total */
    private final Counter notificationFailedCounter;

    /**
     * 알림 처리 소요 시간 타이머.
     * 메트릭명: notification_processing_duration_seconds
     * P50/P95/P99 퍼센타일을 함께 노출한다.
     */
    private final Timer notificationProcessingTimer;

    /**
     * 생성자에서 Micrometer 메트릭을 MeterRegistry에 등록한다.
     *
     * @param notificationRepository 알림 JPA 레포지토리
     * @param meterRegistry          Micrometer 메트릭 레지스트리 (Prometheus 구현체가 자동 주입됨)
     */
    public NotificationService(NotificationRepository notificationRepository,
                                MeterRegistry meterRegistry) {
        this.notificationRepository = notificationRepository;

        this.notificationReceivedCounter = Counter.builder("notification_received_total")
                .description("Kafka로 수신된 주문 완료 이벤트 수")
                .register(meterRegistry);

        this.notificationProcessedCounter = Counter.builder("notification_processed_total")
                .description("성공적으로 처리된 알림 수")
                .register(meterRegistry);

        this.notificationFailedCounter = Counter.builder("notification_failed_total")
                .description("처리 실패한 알림 수")
                .register(meterRegistry);

        this.notificationProcessingTimer = Timer.builder("notification_processing_duration_seconds")
                .description("알림 처리 소요 시간")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(meterRegistry);
    }

    /**
     * 주문 완료 이벤트를 수신하여 알림을 처리하고 이력을 저장한다.
     *
     * <p>처리 시간은 {@code notification_processing_duration_seconds} 타이머로,
     * 수신/성공/실패 건수는 각각의 카운터로 집계된다.</p>
     *
     * @param event Kafka로 수신된 주문 완료 이벤트
     */
    @Transactional
    public void processNotification(OrderCompletedEvent event) {
        notificationReceivedCounter.increment();

        notificationProcessingTimer.record(() -> {
            try {
                String message = String.format(
                        "주문이 완료되었습니다. 주문번호: %d, 상품: %s, 금액: %s원",
                        event.orderId(), event.productId(), event.totalAmount()
                );

                Notification notification = Notification.builder()
                        .orderId(event.orderId())
                        .message(message)
                        .status(NotificationStatus.SENT)
                        .build();
                notificationRepository.save(notification);

                notificationProcessedCounter.increment();
                log.info("알림 처리 완료: orderId={}, message={}", event.orderId(), message);
            } catch (Exception e) {
                // 카운터는 저장 성공 여부와 무관하게 반드시 기록한다.
                notificationFailedCounter.increment();
                log.error("알림 처리 실패: orderId={}, error={}", event.orderId(), e.getMessage());

                // 실패 이력도 DB에 저장해 추적 가능하게 한다.
                try {
                    Notification failedNotification = Notification.builder()
                            .orderId(event.orderId())
                            .message("알림 처리 실패: " + e.getMessage())
                            .status(NotificationStatus.FAILED)
                            .build();
                    notificationRepository.save(failedNotification);
                } catch (Exception saveEx) {
                    log.error("실패 이력 저장 중 오류: orderId={}, error={}", event.orderId(), saveEx.getMessage());
                }
                throw e;
            }
        });
    }

    /**
     * 단건 알림 이력을 조회한다.
     *
     * @param id 알림 ID
     * @return 알림 응답 DTO
     * @throws NotificationNotFoundException 해당 ID의 알림이 존재하지 않을 때
     */
    @Transactional(readOnly = true)
    public NotificationResponse getNotification(Long id) {
        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new NotificationNotFoundException(id));
        return NotificationResponse.from(notification);
    }

    /**
     * 전체 알림 이력 목록을 조회한다.
     *
     * @return 알림 응답 DTO 리스트 (알림이 없으면 빈 리스트)
     */
    @Transactional(readOnly = true)
    public List<NotificationResponse> getNotifications() {
        return notificationRepository.findAll().stream()
                .map(NotificationResponse::from)
                .toList();
    }
}
