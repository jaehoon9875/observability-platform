package com.example.payment.domain;

/**
 * 결제 상태를 나타내는 열거형.
 *
 * <p>상태 전이: PENDING → COMPLETED 또는 FAILED</p>
 */
public enum PaymentStatus {

    /** 결제 요청이 접수되어 처리 대기 중인 상태. */
    PENDING,

    /** 결제가 성공적으로 완료된 상태. */
    COMPLETED,

    /** 결제가 실패한 상태. */
    FAILED
}
