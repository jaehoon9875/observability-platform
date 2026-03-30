package com.example.exporter.config;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

/**
 * Kafka AdminClient 설정 클래스.
 *
 * <p>AdminClient는 Kafka 브로커에 관리 명령을 보내는 클라이언트다.
 * Consumer Group 오프셋 조회, 토픽 목록 확인 등 운영 작업에 사용된다.
 * 일반 Consumer/Producer와 달리 메시지를 주고받지 않고 메타데이터만 다룬다.</p>
 */
@Configuration
public class KafkaAdminConfig {

    /** Kafka 브로커 접속 주소. application.yml 또는 환경변수로 주입된다. */
    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    /**
     * Kafka AdminClient 빈.
     *
     * <p>브로커 주소만 설정하면 동작한다. 인증이 필요한 환경에서는
     * SASL/SSL 관련 설정을 추가해야 한다 (현재 클러스터는 평문 통신).</p>
     *
     * @return 설정이 적용된 AdminClient 인스턴스
     */
    @Bean
    public AdminClient adminClient() {
        return AdminClient.create(Map.of(
            AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers
        ));
    }
}
