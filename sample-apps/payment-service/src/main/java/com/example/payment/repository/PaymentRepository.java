package com.example.payment.repository;

import com.example.payment.domain.Payment;
import com.example.payment.domain.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 결제 JPA 레포지토리.
 *
 * <p>JpaRepository를 상속해 기본 CRUD 메서드를 제공한다.</p>
 */
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    /**
     * 주문 ID로 결제 정보를 조회한다.
     *
     * @param orderId 조회할 주문 ID
     * @return 해당 주문의 결제 정보 (없으면 empty)
     */
    Optional<Payment> findByOrderId(Long orderId);

    /**
     * 결제 상태별로 결제 목록을 조회한다.
     *
     * @param status 조회할 결제 상태
     * @return 해당 상태의 결제 목록
     */
    List<Payment> findAllByStatus(PaymentStatus status);
}
