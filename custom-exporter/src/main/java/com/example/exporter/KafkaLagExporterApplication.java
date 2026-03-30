package com.example.exporter;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Kafka Consumer Lag Exporter 메인 클래스.
 *
 * <p>Kafka Consumer Group의 Lag(미처리 메시지 수)을 주기적으로 수집하여
 * Prometheus가 스크랩할 수 있는 /actuator/prometheus 엔드포인트로 노출한다.</p>
 *
 * <p>@EnableScheduling: KafkaLagMetrics의 @Scheduled 메서드가 동작하려면 필수.</p>
 */
@SpringBootApplication
@EnableScheduling
public class KafkaLagExporterApplication {

    public static void main(String[] args) {
        SpringApplication.run(KafkaLagExporterApplication.class, args);
    }
}
