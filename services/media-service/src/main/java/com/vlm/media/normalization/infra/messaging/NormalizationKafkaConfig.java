package com.vlm.media.normalization.infra.messaging;

import java.util.HashMap;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.kafka.KafkaConnectionDetails;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

/**
 * 정규화 이벤트 수신 설정.
 *
 * <p>DB 트랜잭션이 끝나 리스너가 반환한 뒤에만 offset을 동기 커밋한다. 계약을 어긴 메시지는 다시 받아도 같으므로 로그를 남기고 건너뛰고, 그 밖의 오류(DB 장애
 * 등)는 건너뛰면 상태가 영영 안 바뀌므로 offset을 멈춘 채 계속 다시 시도한다.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
    name = "vlm.media.normalization.consumer-enabled",
    havingValue = "true",
    matchIfMissing = true)
public class NormalizationKafkaConfig {

  private static final Logger log = LoggerFactory.getLogger(NormalizationKafkaConfig.class);

  // 재시도 간격 상한. max.poll.interval.ms(기본 5분)보다 충분히 짧아야 재시도 중 그룹에서 빠지지 않는다.
  private static final long MAX_RETRY_INTERVAL_MS = 30_000;

  @Bean
  ConcurrentKafkaListenerContainerFactory<String, String>
      normalizationKafkaListenerContainerFactory(
          KafkaProperties kafka, KafkaConnectionDetails connection) {
    var consumer = new HashMap<>(kafka.buildConsumerProperties(null));
    consumer.put(
        ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, connection.getConsumer().getBootstrapServers());
    consumer.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
    // 처음 붙는 그룹은 토픽 처음부터 읽는다. 업로드 직후 결과를 놓치면 영상이 정규화 중에 머문다.
    consumer.putIfAbsent(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    consumer.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    consumer.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    consumer.put(ConsumerConfig.ALLOW_AUTO_CREATE_TOPICS_CONFIG, false);

    var factory = new ConcurrentKafkaListenerContainerFactory<String, String>();
    factory.setConsumerFactory(new DefaultKafkaConsumerFactory<>(consumer));
    factory.setConcurrency(1);
    factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
    factory.getContainerProperties().setSyncCommits(true);
    factory.getContainerProperties().setObservationEnabled(true);

    var backOff = new ExponentialBackOff(1_000, 2.0);
    backOff.setMaxInterval(MAX_RETRY_INTERVAL_MS);
    var errors =
        new DefaultErrorHandler(
            (record, error) ->
                log.warn(
                    "Skipping invalid normalization event topic={} partition={} offset={} key={} reason={}",
                    record.topic(),
                    record.partition(),
                    record.offset(),
                    record.key(),
                    NestedExceptionUtils.getMostSpecificCause(error).getMessage()),
            backOff);
    errors.addNotRetryableExceptions(InvalidEventException.class);
    errors.setCommitRecovered(true);
    factory.setCommonErrorHandler(errors);
    return factory;
  }
}
