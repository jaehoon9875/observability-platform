package com.example.order.repository;

import com.example.order.domain.Order;
import com.example.order.domain.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Order 엔티티에 대한 JPA Repository.
 *
 * <p>Spring Data JPA가 기본 CRUD를 자동으로 구현한다.
 * findAllByStatus 메서드는 메서드 이름 규칙에 따라 쿼리가 자동 생성된다:
 * {@code SELECT * FROM orders WHERE status = ?}</p>
 */
public interface OrderRepository extends JpaRepository<Order, Long> {

    /**
     * 특정 상태의 주문 목록을 조회한다.
     *
     * @param status 조회할 주문 상태
     * @return 해당 상태의 주문 리스트 (없으면 빈 리스트)
     */
    List<Order> findAllByStatus(OrderStatus status);
}
