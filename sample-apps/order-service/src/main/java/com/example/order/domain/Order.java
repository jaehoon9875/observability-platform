package com.example.order.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 주문 도메인 엔티티.
 *
 * <p>JPA 엔티티로 orders 테이블에 매핑된다.
 * 외부에서 직접 필드를 수정하지 못하도록 setter를 열지 않고,
 * 도메인 의도를 드러내는 상태 전이 메서드(complete/fail/processing)만 공개한다.</p>
 *
 * <p>Lombok @NoArgsConstructor를 PROTECTED로 제한해 JPA 프록시 생성은 허용하되
 * 애플리케이션 코드에서 기본 생성자를 직접 호출하는 것을 막는다.</p>
 */
@Entity
@Table(name = "orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order {

    /** 주문 식별자. DB AUTO_INCREMENT로 생성된다. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 주문 대상 상품 식별자. */
    @Column(nullable = false)
    private String productId;

    /** 주문 수량. */
    @Column(nullable = false)
    private Integer quantity;

    /**
     * 주문 총 금액.
     * DECIMAL(19, 2) 타입으로 저장해 부동소수점 오차를 방지한다.
     */
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal totalAmount;

    /** 주문 현재 상태. DB에는 문자열(ENUM 이름)로 저장된다. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    /** 주문 최초 생성 시각. 한 번 저장된 후 변경하지 않는다(updatable=false). */
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** 주문 상태가 마지막으로 변경된 시각. */
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    /**
     * 주문 생성 빌더.
     *
     * <p>초기 상태는 항상 PENDING이며, 생성 시각을 현재 시각으로 설정한다.
     * id는 DB 저장 후 자동 할당되므로 빌더 파라미터에서 제외한다.</p>
     *
     * @param productId   주문 상품 ID
     * @param quantity    주문 수량
     * @param totalAmount 주문 총 금액
     */
    @Builder
    public Order(String productId, Integer quantity, BigDecimal totalAmount) {
        this.productId = productId;
        this.quantity = quantity;
        this.totalAmount = totalAmount;
        this.status = OrderStatus.PENDING;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 결제 성공 후 주문을 완료 상태로 전이한다.
     * updatedAt을 현재 시각으로 갱신한다.
     */
    public void complete() {
        this.status = OrderStatus.COMPLETED;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 결제 실패 등 예외 상황에서 주문을 실패 상태로 전이한다.
     * updatedAt을 현재 시각으로 갱신한다.
     */
    public void fail() {
        this.status = OrderStatus.FAILED;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * payment-service에 결제 요청을 보내기 직전에 호출해
     * 주문 상태를 처리 중으로 전이한다.
     */
    public void processing() {
        this.status = OrderStatus.PROCESSING;
        this.updatedAt = LocalDateTime.now();
    }
}
