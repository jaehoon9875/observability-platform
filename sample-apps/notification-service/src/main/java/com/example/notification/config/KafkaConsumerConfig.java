package com.example.notification.config;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka Consumer 설정 클래스.
 *
 * <p>notification-service는 order-completed 토픽을 구독(consume)해
 * 주문 완료 이벤트를 처리한다.</p>
 *
 * <p>키와 값 모두 String 역직렬화를 사용한다.
 * JSON 파싱은 NotificationConsumer에서 ObjectMapper로 직접 수행한다.</p>
 */
@Configuration
public class KafkaConsumerConfig {

    /** Kafka 브로커 접속 주소. application.yml 또는 환경변수로 주입된다. */
    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    /** Consumer 그룹 ID. 동일 그룹 내에서 파티션이 분배된다. */
    @Value("${spring.kafka.consumer.group-id:notification-service}")
    private String groupId;

    /**
     * Kafka Consumer 팩토리 빈.
     *
     * <p>브로커 주소, 그룹 ID, 역직렬화 방식을 설정한다.
     * auto-offset-reset을 earliest로 설정해 컨슈머 재기동 시
     * 미처리된 메시지부터 소비한다.</p>
     *
     * @return 설정이 적용된 DefaultKafkaConsumerFactory
     */
    @Bean
    public ConsumerFactory<String, String> consumerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        return new DefaultKafkaConsumerFactory<>(config);
    }

    /**
     * Kafka 리스너 컨테이너 팩토리 빈.
     *
     * <p>@KafkaListener 어노테이션이 붙은 메서드가 이 팩토리를 통해
     * 컨슈머 컨테이너를 생성한다.</p>
     *
     * @return 설정된 ConcurrentKafkaListenerContainerFactory
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        return factory;
    }
}
