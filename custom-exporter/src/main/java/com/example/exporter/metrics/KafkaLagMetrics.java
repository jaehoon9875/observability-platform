package com.example.exporter.metrics;

import com.example.exporter.collector.KafkaLagCollector;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Kafka Consumer Lag 메트릭을 Prometheus에 등록하고 주기적으로 갱신하는 클래스.
 *
 * <p>동작 방식:
 * <ol>
 *   <li>@Scheduled 메서드가 15초마다 KafkaLagCollector를 호출하여 Lag 값을 수집한다.</li>
 *   <li>파티션별로 AtomicLong 값을 Map에 보관한다.</li>
 *   <li>새 파티션이 발견되면 Gauge를 MeterRegistry에 등록한다 (이후에는 재등록 없이 값만 갱신).</li>
 *   <li>Prometheus가 /actuator/prometheus를 스크랩하면 등록된 Gauge 값이 반환된다.</li>
 * </ol>
 * </p>
 *
 * <p>AtomicLong을 쓰는 이유: Gauge는 등록 시점에 "이 객체의 값을 읽어라"고 참조를 걸어둔다.
 * 이후 @Scheduled 메서드가 AtomicLong.set()으로 값을 바꾸면, 다음 스크랩 때 자동으로 새 값이 노출된다.</p>
 */
@Slf4j
@Component
public class KafkaLagMetrics {

    private final KafkaLagCollector collector;
    private final MeterRegistry registry;

    /**
     * 모니터링할 Consumer Group ID 목록.
     * 쉼표로 구분하여 여러 그룹을 지정할 수 있다 (예: "group-a,group-b").
     */
    @Value("${kafka.consumer.groups}")
    private String consumerGroups;

    /**
     * 파티션별 Lag 값을 보관하는 맵.
     * 키: "groupId|topic|partition" 형태의 고유 식별자
     * 값: Prometheus Gauge가 참조하는 AtomicLong
     */
    private final ConcurrentHashMap<String, AtomicLong> lagValues = new ConcurrentHashMap<>();

    public KafkaLagMetrics(KafkaLagCollector collector, MeterRegistry registry) {
        this.collector = collector;
        this.registry = registry;
    }

    /**
     * Consumer Lag을 수집하고 Gauge 값을 갱신하는 스케줄 메서드.
     *
     * <p>fixedDelay: 이전 실행 완료 후 지정된 시간이 지나면 다음 실행.
     * Kafka 응답이 느릴 때 중복 실행되지 않도록 fixedRate 대신 fixedDelay를 사용한다.</p>
     */
    @Scheduled(fixedDelayString = "${kafka.lag.collect.interval-ms:15000}")
    public void collectAndUpdate() {
        Arrays.stream(consumerGroups.split(","))
            .map(String::trim)
            .filter(group -> !group.isEmpty())
            .forEach(this::updateGroupMetrics);
    }

    /**
     * 특정 Consumer Group의 모든 파티션 Lag을 수집하고 Gauge 값을 갱신한다.
     *
     * @param groupId 조회할 Consumer Group ID
     */
    private void updateGroupMetrics(String groupId) {
        var lagInfos = collector.collectLag(groupId);

        for (var info : lagInfos) {
            String key = info.group() + "|" + info.topic() + "|" + info.partition();

            // computeIfAbsent: 맵에 키가 없을 때만 Gauge를 새로 등록한다.
            // 이미 등록된 파티션은 AtomicLong 값만 갱신되므로 중복 등록되지 않는다.
            AtomicLong lagValue = lagValues.computeIfAbsent(key, k -> {
                AtomicLong val = new AtomicLong(0);
                Gauge.builder("kafka_consumer_group_lag", val, AtomicLong::get)
                    .description("Kafka consumer group lag (number of unprocessed messages)")
                    .tag("group", info.group())
                    .tag("topic", info.topic())
                    .tag("partition", String.valueOf(info.partition()))
                    .register(registry);
                log.info("Registered gauge for group={}, topic={}, partition={}",
                    info.group(), info.topic(), info.partition());
                return val;
            });

            lagValue.set(info.lag());
            log.debug("Updated lag: group={}, topic={}, partition={}, lag={}",
                info.group(), info.topic(), info.partition(), info.lag());
        }
    }
}
