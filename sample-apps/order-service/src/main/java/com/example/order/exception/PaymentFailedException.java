package com.example.order.exception;

/**
 * 결제 처리 중 payment-service 호출이 실패했을 때 발생하는 예외.
 *
 * <p>RuntimeException을 상속하지만 {@code @Transactional(noRollbackFor)}에 등록되어
 * 트랜잭션을 롤백하지 않는다. 덕분에 주문이 FAILED 상태로 DB에 커밋된다.</p>
 *
 * <p>GlobalExceptionHandler에서 잡아 502 Bad Gateway로 변환한다.</p>
 */
public class PaymentFailedException extends RuntimeException {

    /**
     * 원인 예외를 포함한 결제 실패 예외를 생성한다.
     *
     * @param message 실패 원인 메시지
     * @param cause   원래 발생한 예외
     */
    public PaymentFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
