package com.example.order.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * RestTemplate 설정 클래스.
 *
 * <p>order-service에서 payment-service를 동기 HTTP 호출할 때 사용하는
 * RestTemplate 빈을 등록한다.</p>
 */
@Configuration
public class RestTemplateConfig {

    /**
     * HTTP 동기 호출용 RestTemplate 빈.
     *
     * <p>PaymentClient에서 payment-service로의 REST 호출에 사용된다.</p>
     *
     * @return 기본 설정의 RestTemplate
     */
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
