package com.example.payment.exception;

/**
 * 요청한 결제 정보를 찾을 수 없을 때 발생하는 예외.
 *
 * <p>GlobalExceptionHandler에서 404 Not Found 응답으로 처리된다.</p>
 */
public class PaymentNotFoundException extends RuntimeException {

    /**
     * 결제 ID를 포함한 예외 메시지를 생성한다.
     *
     * @param id 찾을 수 없는 결제 ID
     */
    public PaymentNotFoundException(Long id) {
        super("결제를 찾을 수 없습니다. id=" + id);
    }
}
