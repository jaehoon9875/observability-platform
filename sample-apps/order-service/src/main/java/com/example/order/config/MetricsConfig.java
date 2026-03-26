package com.example.order.config;

import io.micrometer.core.aop.TimedAspect;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Micrometer 메트릭 설정 클래스.
 *
 * <p>@Timed 애노테이션이 Controller 외의 @Component, @Service 등에서도
 * 동작하려면 TimedAspect 빈이 등록되어 있어야 한다.</p>
 */
@Configuration
public class MetricsConfig {

    /**
     * @Timed 애노테이션을 @Component 계층에서 활성화하는 AOP Aspect 빈.
     *
     * @param registry Micrometer MeterRegistry
     * @return TimedAspect 인스턴스
     */
    @Bean
    public TimedAspect timedAspect(MeterRegistry registry) {
        return new TimedAspect(registry);
    }
}
