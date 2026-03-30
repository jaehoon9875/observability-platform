package com.example.exporter.collector;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.ListOffsetsResult;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.common.TopicPartition;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Kafka Consumer Group의 Lag을 조회하는 수집기.
 *
 * <p>Lag = (토픽에 쌓인 최신 오프셋) - (Consumer가 마지막으로 읽은 오프셋)</p>
 *
 * <p>조회 순서:
 * <ol>
 *   <li>listConsumerGroupOffsets: Consumer가 마지막으로 커밋한 오프셋 조회</li>
 *   <li>listOffsets(OffsetSpec.latest): 각 파티션의 최신 오프셋 조회</li>
 *   <li>Lag = 최신 오프셋 - Consumer 오프셋</li>
 * </ol>
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaLagCollector {

    private final AdminClient adminClient;

    /**
     * 특정 Consumer Group의 모든 파티션 Lag 정보를 조회한다.
     *
     * @param groupId 조회할 Consumer Group ID (예: "notification-service")
     * @return 파티션별 Lag 정보 목록. 오류 발생 시 빈 리스트 반환.
     */
    public List<LagInfo> collectLag(String groupId) {
        try {
            // 1단계: Consumer가 마지막으로 커밋한 오프셋 조회
            //   - 키: TopicPartition (토픽명 + 파티션 번호)
            //   - 값: OffsetAndMetadata (커밋된 오프셋 번호)
            var consumerOffsets = adminClient
                .listConsumerGroupOffsets(groupId)
                .partitionsToOffsetAndMetadata()
                .get();

            if (consumerOffsets.isEmpty()) {
                log.debug("Consumer group '{}' has no committed offsets yet", groupId);
                return List.of();
            }

            // 2단계: 동일한 파티션들의 최신 오프셋(토픽 끝 위치) 조회
            //   - OffsetSpec.latest(): "이 파티션에서 가장 마지막 오프셋 알려줘"
            var offsetRequests = consumerOffsets.keySet().stream()
                .collect(Collectors.toMap(tp -> tp, tp -> OffsetSpec.latest()));

            Map<TopicPartition, ListOffsetsResult.ListOffsetsResultInfo> latestOffsets =
                adminClient.listOffsets(offsetRequests).all().get();

            // 3단계: Lag 계산 후 LagInfo 리스트로 변환
            return consumerOffsets.entrySet().stream()
                .map(entry -> {
                    TopicPartition tp = entry.getKey();
                    long consumerOffset = entry.getValue().offset();
                    long latestOffset = latestOffsets.get(tp).offset();
                    // 음수 방지: Consumer가 최신보다 앞에 있을 수는 없지만 방어적으로 처리
                    long lag = Math.max(0, latestOffset - consumerOffset);
                    return new LagInfo(groupId, tp.topic(), tp.partition(), lag);
                })
                .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("Failed to collect lag for consumer group '{}': {}", groupId, e.getMessage());
            return List.of();
        }
    }

    /**
     * 파티션 하나의 Lag 정보를 담는 불변 레코드.
     *
     * @param group     Consumer Group ID
     * @param topic     토픽명
     * @param partition 파티션 번호
     * @param lag       미처리 메시지 수 (0 이상)
     */
    public record LagInfo(String group, String topic, int partition, long lag) {}
}
