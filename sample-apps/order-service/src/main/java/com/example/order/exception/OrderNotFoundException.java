package com.example.order.exception;

/**
 * 요청한 ID에 해당하는 주문이 존재하지 않을 때 발생하는 예외.
 *
 * <p>RuntimeException을 상속해 트랜잭션 롤백 대상이 된다.
 * GlobalExceptionHandler에서 잡아 404 Not Found로 변환한다.</p>
 */
public class OrderNotFoundException extends RuntimeException {

    /**
     * 주문 ID를 포함한 메시지로 예외를 생성한다.
     *
     * @param id 존재하지 않는 주문 ID
     */
    public OrderNotFoundException(Long id) {
        super("주문을 찾을 수 없습니다. id=" + id);
    }
}
