package com.example.notification.exception;

/**
 * 요청한 알림 이력을 찾을 수 없을 때 발생하는 예외.
 *
 * <p>GlobalExceptionHandler에서 404 Not Found 응답으로 처리된다.</p>
 */
public class NotificationNotFoundException extends RuntimeException {

    /**
     * 알림 ID를 포함한 예외 메시지를 생성한다.
     *
     * @param id 찾을 수 없는 알림 ID
     */
    public NotificationNotFoundException(Long id) {
        super("알림을 찾을 수 없습니다. id=" + id);
    }
}
