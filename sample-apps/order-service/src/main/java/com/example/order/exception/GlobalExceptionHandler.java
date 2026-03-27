package com.example.order.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 애플리케이션 전역 예외 처리 핸들러.
 *
 * <p>@RestControllerAdvice로 모든 컨트롤러의 예외를 한 곳에서 처리한다.
 * Spring 6(Boot 3.x)의 RFC 7807 표준 응답 형식인 {@link ProblemDetail}을 사용해
 * 일관된 에러 응답 구조를 제공한다.</p>
 *
 * <pre>
 * 처리 우선순위 (구체적 예외 → 일반 예외 순):
 *   1. OrderNotFoundException       → 404 Not Found
 *   2. PaymentFailedException       → 502 Bad Gateway
 *   3. MethodArgumentNotValidException → 400 Bad Request
 *   4. Exception (fallback)         → 500 Internal Server Error
 * </pre>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 주문을 찾을 수 없을 때 404 응답을 반환한다.
     *
     * @param e 발생한 OrderNotFoundException
     * @return RFC 7807 형식의 404 ProblemDetail
     */
    @ExceptionHandler(OrderNotFoundException.class)
    public ProblemDetail handleOrderNotFound(OrderNotFoundException e) {
        log.warn(e.getMessage());
        ProblemDetail detail = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
        detail.setDetail(e.getMessage());
        return detail;
    }

    /**
     * 결제 처리 실패 시 502 응답을 반환한다.
     *
     * <p>payment-service 호출이 실패한 경우로, 주문은 FAILED 상태로 DB에 저장된다.</p>
     *
     * @param e 발생한 PaymentFailedException
     * @return RFC 7807 형식의 502 ProblemDetail
     */
    @ExceptionHandler(PaymentFailedException.class)
    public ProblemDetail handlePaymentFailed(PaymentFailedException e) {
        log.warn(e.getMessage());
        ProblemDetail detail = ProblemDetail.forStatus(HttpStatus.BAD_GATEWAY);
        detail.setDetail(e.getMessage());
        return detail;
    }

    /**
     * Bean Validation(@Valid) 실패 시 400 응답을 반환한다.
     *
     * <p>여러 필드 오류가 있을 경우 콤마로 이어붙여 하나의 메시지로 반환한다.</p>
     *
     * @param e 발생한 MethodArgumentNotValidException
     * @return RFC 7807 형식의 400 ProblemDetail (필드별 오류 메시지 포함)
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .reduce((a, b) -> a + ", " + b)
                .orElse("유효성 검증 실패");
        ProblemDetail detail = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        detail.setDetail(message);
        return detail;
    }

    /**
     * 처리되지 않은 모든 예외를 잡는 fallback 핸들러.
     *
     * <p>예상치 못한 예외가 클라이언트에 스택 트레이스로 노출되지 않도록
     * 일반적인 500 메시지만 반환하고, 서버 측에는 전체 스택을 에러 로그로 기록한다.</p>
     *
     * @param e 처리되지 않은 예외
     * @return RFC 7807 형식의 500 ProblemDetail
     */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGeneral(Exception e) {
        log.error("처리되지 않은 예외", e);
        ProblemDetail detail = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        detail.setDetail("내부 서버 오류가 발생했습니다.");
        return detail;
    }
}
