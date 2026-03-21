package com.example.order;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * order-service의 진입점.
 *
 * <p>Observability 스택의 관측 대상이 되는 첫 번째 MSA 서비스다.
 * 주문 생성/조회 API를 제공하며, Micrometer를 통해 비즈니스 메트릭을
 * /actuator/prometheus 엔드포인트로 노출한다.</p>
 */
@SpringBootApplication
public class OrderServiceApplication {

    /** Spring Boot 애플리케이션을 시작한다. */
    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }
}
