package com.example.payment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * payment-service 스프링 부트 애플리케이션 진입점.
 *
 * <p>order-service로부터 REST 호출을 받아 결제를 처리하는 서비스다.</p>
 */
@SpringBootApplication
public class PaymentServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaymentServiceApplication.class, args);
    }
}
