package com.example.order.config;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka Producer 설정 클래스.
 *
 * <p>order-service는 주문 완료 이벤트를 Kafka로 발행(produce)한다.
 * notification-service가 해당 토픽을 구독(consume)해 알림을 처리한다.
 * (2단계에서 알림 이벤트 발행 로직이 추가될 예정)</p>
 *
 * <p>키와 값 모두 String 직렬화를 사용한다.
 * 브로커 주소는 application.yml의 spring.kafka.bootstrap-servers에서 주입받는다.</p>
 */
@Configuration
public class KafkaConfig {

    /** Kafka 브로커 접속 주소. application.yml 또는 환경변수로 주입된다. */
    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    /**
     * Kafka Producer 팩토리 빈.
     *
     * <p>브로커 주소와 직렬화 방식을 설정해 ProducerFactory를 생성한다.
     * KafkaTemplate이 이 팩토리를 사용해 실제 Producer를 생성한다.</p>
     *
     * @return 설정이 적용된 DefaultKafkaProducerFactory
     */
    @Bean
    public ProducerFactory<String, String> producerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        return new DefaultKafkaProducerFactory<>(config);
    }

    /**
     * Kafka 메시지 발행용 KafkaTemplate 빈.
     *
     * <p>서비스 레이어에서 kafkaTemplate.send(topic, key, value) 형태로 사용한다.</p>
     *
     * @return 설정된 KafkaTemplate
     */
    @Bean
    public KafkaTemplate<String, String> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }
}
