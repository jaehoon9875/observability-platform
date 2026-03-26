package com.example.notification.service;

import com.example.notification.domain.Notification;
import com.example.notification.domain.NotificationStatus;
import com.example.notification.dto.OrderCompletedEvent;
import com.example.notification.exception.NotificationNotFoundException;
import com.example.notification.repository.NotificationRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * NotificationService 단위 테스트.
 *
 * <p>Mockito로 NotificationRepository를 모킹하고,
 * SimpleMeterRegistry로 메트릭 카운터를 검증한다.</p>
 */
@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    private MeterRegistry meterRegistry;
    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        // SimpleMeterRegistry로 메트릭 값을 검증한다.
        meterRegistry = new SimpleMeterRegistry();
        notificationService = new NotificationService(notificationRepository, meterRegistry);
    }

    @Test
    @DisplayName("주문 완료 이벤트 수신 시 SENT 상태로 알림 이력이 저장된다")
    void processNotification_success() {
        // given
        OrderCompletedEvent event = new OrderCompletedEvent(1L, "P001", BigDecimal.valueOf(10000));

        // when
        notificationService.processNotification(event);

        // then
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(1)).save(captor.capture());

        Notification saved = captor.getValue();
        assertThat(saved.getOrderId()).isEqualTo(1L);
        assertThat(saved.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(saved.getMessage()).contains("1", "P001", "10000");
    }

    @Test
    @DisplayName("알림 처리 성공 시 notification_received_total과 notification_processed_total 카운터가 증가한다")
    void processNotification_success_metricsIncremented() {
        // given
        OrderCompletedEvent event = new OrderCompletedEvent(2L, "P002", BigDecimal.valueOf(5000));

        // when
        notificationService.processNotification(event);

        // then
        assertThat(meterRegistry.counter("notification_received_total").count()).isEqualTo(1.0);
        assertThat(meterRegistry.counter("notification_processed_total").count()).isEqualTo(1.0);
        assertThat(meterRegistry.counter("notification_failed_total").count()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("알림 처리 실패 시 FAILED 상태로 저장되고 notification_failed_total 카운터가 증가한다")
    void processNotification_failure_metricsIncremented() {
        // given
        OrderCompletedEvent event = new OrderCompletedEvent(3L, "P003", BigDecimal.valueOf(3000));
        willThrow(new RuntimeException("DB 저장 실패"))
                .given(notificationRepository).save(any(Notification.class));

        // when / then
        // 첫 번째 save 호출(SENT 시도)에서 예외 발생 → FAILED로 재저장 시도도 실패하므로 예외 전파
        // 실패 이력 저장도 예외가 발생하지만 failed 카운터는 증가해야 한다.
        try {
            notificationService.processNotification(event);
        } catch (Exception ignored) {
            // 예외 전파는 정상 동작
        }

        assertThat(meterRegistry.counter("notification_received_total").count()).isEqualTo(1.0);
        assertThat(meterRegistry.counter("notification_failed_total").count()).isEqualTo(1.0);
        assertThat(meterRegistry.counter("notification_processed_total").count()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("존재하지 않는 알림 ID 조회 시 NotificationNotFoundException이 발생한다")
    void getNotification_notFound() {
        // given
        given(notificationRepository.findById(999L)).willReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> notificationService.getNotification(999L))
                .isInstanceOf(NotificationNotFoundException.class)
                .hasMessageContaining("999");
    }
}
