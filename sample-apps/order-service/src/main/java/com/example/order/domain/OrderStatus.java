package com.example.order.domain;

/**
 * 주문의 생명주기 상태를 나타내는 열거형.
 *
 * <p>상태 전이 흐름: PENDING → PROCESSING → COMPLETED
 *                                        ↘ FAILED</p>
 */
public enum OrderStatus {
    /** 주문이 생성되어 결제 처리를 대기 중인 상태. */
    PENDING,

    /** payment-service에 결제 요청을 보내고 응답을 기다리는 상태. */
    PROCESSING,

    /** 결제까지 완료되어 주문이 정상 처리된 최종 상태. */
    COMPLETED,

    /** 결제 실패 등으로 주문 처리가 중단된 상태. */
    FAILED
}
