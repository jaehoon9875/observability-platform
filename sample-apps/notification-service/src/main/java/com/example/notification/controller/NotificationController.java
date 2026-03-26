package com.example.notification.controller;

import com.example.notification.dto.NotificationResponse;
import com.example.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 알림 이력 REST API 컨트롤러.
 *
 * <p>모든 엔드포인트는 /api/notifications 경로 하위에 위치한다.
 * 비즈니스 로직은 NotificationService에 위임한다.</p>
 *
 * <p>Swagger UI 접근: /swagger-ui.html</p>
 */
@Tag(name = "Notification", description = "알림 이력 API")
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    /**
     * 단건 알림 이력을 조회한다.
     *
     * <p>GET /api/notifications/{id}<br>
     * 존재하지 않는 ID 요청 시 404 Not Found를 반환한다.</p>
     *
     * @param id 조회할 알림 ID (경로 변수)
     * @return 알림 상세 정보
     */
    @Operation(summary = "알림 단건 조회")
    @GetMapping("/{id}")
    public NotificationResponse getNotification(@PathVariable Long id) {
        return notificationService.getNotification(id);
    }

    /**
     * 전체 알림 이력 목록을 조회한다.
     *
     * <p>GET /api/notifications<br>
     * 알림이 없으면 빈 배열을 반환한다.</p>
     *
     * @return 알림 목록
     */
    @Operation(summary = "알림 목록 조회")
    @GetMapping
    public List<NotificationResponse> getNotifications() {
        return notificationService.getNotifications();
    }
}
