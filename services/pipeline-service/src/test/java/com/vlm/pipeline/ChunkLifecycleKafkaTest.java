package com.vlm.pipeline;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vlm.pipeline.infra.persistence.JdbcResultRepository;
import java.net.URI;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.admin.*;
import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.clients.producer.*;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.kafka.KafkaContainer;
import software.amazon.awssdk.auth.credentials.*;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

@SpringBootTest(
    properties = {
      "pipeline.intake.enabled=true", "pipeline.intake.mode=local",
      "pipeline.intake.profile-version=spec014-v1",
          "pipeline.intake.inference-profile-version=verification-v1",
      "pipeline.intake.group-id=spec014-normalized", "pipeline.intake.retry-interval-ms=100",
      "pipeline.intake.retry-attempts=1", "pipeline.results.enabled=true",
      "pipeline.results.group-id=spec014-results", "pipeline.dispatch.enabled=true",
      "pipeline.dispatch.interval-ms=50", "vlm.outbox.scheduling-enabled=true",
      "vlm.outbox.publish-interval-ms=50"
    })
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Import(ChunkLifecycleKafkaTest.Topics.class)
class ChunkLifecycleKafkaTest {
  static final Network network = Network.newNetwork();

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

  @Container
  static PostgreSQLContainer<?> workerDb =
      new PostgreSQLContainer<>("postgres:17-alpine")
          .withNetwork(network)
          .withNetworkAliases("workerdb");

  @Container @ServiceConnection
  static KafkaContainer kafka =
      new KafkaContainer("apache/kafka:4.3.2")
          .withNetwork(network)
          .withNetworkAliases("kafka")
          .withListener("kafka:19092");

  @Container
  static GenericContainer<?> storage =
      new GenericContainer<>(
              "chrislusf/seaweedfs:4.48@sha256:4e61d15fd35994cb1e43e1e553dff106794841fd9a99ade2fc8c8bfce4d7872d")
          .withCopyToContainer(
              Transferable.of(
                  """
        {"identities":[{"name":"test","credentials":[{"accessKey":"test","secretKey":"test"}],"actions":["Admin","Read","Write","List","Tagging"]}]}
        """),
              "/etc/test-s3.json")
          .withCommand("server", "-s3", "-s3.config=/etc/test-s3.json", "-dir=/data")
          .withExposedPorts(8333)
          .withNetwork(network)
          .withNetworkAliases("storage")
          .waitingFor(Wait.forListeningPort());

  @TestConfiguration(proxyBeanMethods = false)
  static class Topics {
    @Bean
    KafkaAdmin.NewTopics lifecycleTopics() {
      return new KafkaAdmin.NewTopics(
          java.util.stream.Stream.of(
                  "video.uploaded",
                  "video.normalizing",
                  "video.normalized",
                  "video.uploaded.normalize.retry",
                  "normalize.dlq",
                  "chunk.requested",
                  "chunk.requested.chunk-processor-v1.retry",
                  "chunk-processor-v1.dlq",
                  "chunk.execution.finished",
                  "job.status",
                  "stage.timing")
              .map(n -> new NewTopic(n, 1, (short) 1))
              .toArray(NewTopic[]::new));
    }
  }

  @Autowired JdbcTemplate jdbc;
  @MockitoSpyBean JdbcResultRepository results;
  final ObjectMapper mapper = new ObjectMapper();

  final List<String> builtImages = new ArrayList<>();

  String image(String service) throws Exception {
    Path source = Path.of(Objects.requireNonNull(System.getProperty("chunk.processor.source")));
    assertThat(source.resolve("services/" + service + "/Dockerfile")).exists();
    String tag = "vlm/spec014-" + service + ":" + UUID.randomUUID();
    Path log = Files.createTempFile("spec014-build-", ".log");
    try {
      // 실제 레포 .dockerignore를 적용하여 .env·가상환경을 읽거나 이미지에 넣지 않는다.
      var process =
          new ProcessBuilder(
                  "docker",
                  "build",
                  "-f",
                  source.resolve("services/" + service + "/Dockerfile").toString(),
                  "--target",
                  "runtime",
                  "-t",
                  tag,
                  source.toString())
              .redirectErrorStream(true)
              .redirectOutput(log.toFile())
              .start();
      if (!process.waitFor(5, TimeUnit.MINUTES)) {
        process.destroyForcibly().waitFor();
        fail("Worker image build timed out");
      }
      assertThat(process.exitValue()).withFailMessage(Files.readString(log)).isZero();
      builtImages.add(tag);
      return tag;
    } finally {
      Files.deleteIfExists(log);
    }
  }

  @AfterEach
  void removeBuiltImages() {
    for (String tag : builtImages)
      DockerClientFactory.instance().client().removeImageCmd(tag).exec();
  }

  GenericContainer<?> normalizer() throws Exception {
    return new GenericContainer<>(image("normalize"))
        .withNetwork(network)
        .withEnv("KAFKA_BOOTSTRAP", "kafka:19092")
        .withEnv("NORMALIZE_WORK_DIR", "/work")
        .withEnv("STAGE_TIMING_PUBLISH", "false")
        .withEnv("S3_BUCKET", "spec014")
        .withEnv("S3_ENDPOINT_URL", "http://storage:8333")
        .withEnv("S3_PATH_STYLE", "true")
        .withEnv("AWS_ACCESS_KEY_ID", "test")
        .withEnv("AWS_SECRET_ACCESS_KEY", "test")
        .withTmpFs(Map.of("/work", "rw,uid=10001,gid=10001"))
        .withExposedPorts(8000)
        .waitingFor(Wait.forHttp("/ready").forStatusCode(200))
        .withStartupTimeout(Duration.ofMinutes(3));
  }

  GenericContainer<?> processor(String image, String outcome) {
    return new GenericContainer<>(image)
        .withNetwork(network)
        .withEnv("CHUNK_PROCESSOR_ENABLED", "true")
        .withEnv("CHUNK_PROCESSOR_ENVIRONMENT", "test")
        .withEnv("CHUNK_PROCESSOR_ADAPTER", "verification")
        .withEnv("CHUNK_PROCESSOR_VERIFICATION_OUTCOME", outcome)
        .withEnv(
            "CHUNK_PROCESSOR_DATABASE_URL",
            "postgresql+asyncpg://"
                + workerDb.getUsername()
                + ":"
                + workerDb.getPassword()
                + "@workerdb:5432/"
                + workerDb.getDatabaseName())
        .withEnv("CHUNK_PROCESSOR_KAFKA_BOOTSTRAP", "kafka:19092")
        .withEnv("S3_BUCKET", "spec014")
        .withEnv("S3_ENDPOINT_URL", "http://storage:8333")
        .withEnv("S3_PATH_STYLE", "true")
        .withEnv("AWS_ACCESS_KEY_ID", "test")
        .withEnv("AWS_SECRET_ACCESS_KEY", "test")
        .withTmpFs(Map.of("/work", "rw,uid=10001,gid=10001"))
        .withExposedPorts(8000)
        .waitingFor(Wait.forHttp("/ready").forStatusCode(200))
        .withStartupTimeout(Duration.ofMinutes(3));
  }

  int count(String sql) {
    return Objects.requireNonNull(jdbc.queryForObject(sql, Integer.class));
  }

  long committed() throws Exception {
    try (var admin = Admin.create(Map.of("bootstrap.servers", kafka.getBootstrapServers()))) {
      var offset =
          admin
              .listConsumerGroupOffsets("spec014-results")
              .partitionsToOffsetAndMetadata()
              .get(10, TimeUnit.SECONDS)
              .get(new TopicPartition("chunk.execution.finished", 0));
      return offset == null ? 0 : offset.offset();
    }
  }

  void send(ObjectNode event) throws Exception {
    try (var producer =
        new KafkaProducer<String, String>(
            Map.of(
                "bootstrap.servers",
                kafka.getBootstrapServers(),
                "key.serializer",
                StringSerializer.class,
                "value.serializer",
                StringSerializer.class))) {
      var record =
          new ProducerRecord<String, String>(
              "video.uploaded", event.get("sourceId").asText(), event.toString());
      record
          .headers()
          .add(
              "traceparent",
              "00-0123456789abcdef0123456789abcdef-0123456789abcdef-01"
                  .getBytes(java.nio.charset.StandardCharsets.UTF_8));
      producer.send(record).get(10, TimeUnit.SECONDS);
    }
  }

  ObjectNode uploaded(S3Client s3, Path video) throws Exception {
    UUID source = UUID.randomUUID();
    String key = "sources/" + source + "/original";
    s3.putObject(
        PutObjectRequest.builder().bucket("spec014").key(key).build(), RequestBody.fromFile(video));
    var e = mapper.createObjectNode();
    e.put("eventId", UUID.randomUUID().toString());
    e.put("eventType", "video.uploaded");
    e.put("schemaVersion", 1);
    e.put("sourceId", source.toString());
    e.put("ownerId", UUID.randomUUID().toString());
    e.put("occurredAt", Instant.now().toString());
    e.put("storageKey", key);
    e.put("sizeBytes", Files.size(video));
    e.put("contentType", "video/mp4");
    return e;
  }

  List<String> normalizedPayloads(int expected) {
    return payloads("video.normalized", expected);
  }

  List<String> payloads(String topic, int expected) {
    var values = new ArrayList<String>();
    try (var consumer =
        new KafkaConsumer<String, String>(
            Map.of(
                "bootstrap.servers",
                kafka.getBootstrapServers(),
                "group.id",
                "verify-normalize-" + UUID.randomUUID(),
                "key.deserializer",
                StringDeserializer.class,
                "value.deserializer",
                StringDeserializer.class,
                "enable.auto.commit",
                false))) {
      var tp = new TopicPartition(topic, 0);
      consumer.assign(List.of(tp));
      consumer.seekToBeginning(List.of(tp));
      long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
      while (values.size() < expected && System.nanoTime() < deadline)
        for (var r : consumer.poll(Duration.ofMillis(100))) values.add(r.value());
    }
    return values;
  }

  JsonNode publishedStatus(String status) throws Exception {
    String eventId =
        jdbc.queryForObject(
            "SELECT event_id::text FROM outbox_events WHERE event_type='job.status' AND payload->'execution'->>'status'=? ORDER BY id DESC LIMIT 1",
            String.class,
            status);
    try (var consumer =
        new KafkaConsumer<String, String>(
            Map.of(
                "bootstrap.servers",
                kafka.getBootstrapServers(),
                "group.id",
                "verify-status-" + UUID.randomUUID(),
                "key.deserializer",
                StringDeserializer.class,
                "value.deserializer",
                StringDeserializer.class,
                "enable.auto.commit",
                false))) {
      var tp = new TopicPartition("job.status", 0);
      consumer.assign(List.of(tp));
      consumer.seekToBeginning(List.of(tp));
      long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
      while (System.nanoTime() < deadline)
        for (var r : consumer.poll(Duration.ofMillis(100))) {
          var event = mapper.readTree(r.value());
          if (event.path("eventId").asText().equals(eventId)) {
            assertThat(r.key()).isEqualTo(event.path("sourceId").asText());
            assertThat(event.path("sequence").asLong()).isPositive();
            return event;
          }
        }
    }
    throw new AssertionError("Committed terminal job.status event did not reach Kafka: " + eventId);
  }

  @Test
  @DisplayName(
      "[SPEC-014 R-1 R-5 R-6 R-9 R-14 R-15 NFR-2] 실제 normalize→Kafka→추출 워커→결과→슬롯 반환과 다음 청크를 연결한다")
  void realWorkerLifecycle() throws Exception {
    Path video = Files.createTempFile("spec014-source-", ".mp4");
    var workerImage = image("chunk-processor");
    try (var s3 =
            S3Client.builder()
                .endpointOverride(
                    URI.create("http://" + storage.getHost() + ":" + storage.getMappedPort(8333)))
                .region(Region.US_EAST_1)
                .credentialsProvider(
                    StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test")))
                .forcePathStyle(true)
                .build();
        var normalize = normalizer()) {
      await()
          .atMost(Duration.ofSeconds(60))
          .ignoreException(S3Exception.class)
          .until(
              () -> {
                s3.createBucket(b -> b.bucket("spec014"));
                return true;
              });
      await()
          .atMost(Duration.ofSeconds(60))
          .ignoreException(S3Exception.class)
          .until(
              () -> {
                s3.putObject(
                    PutObjectRequest.builder().bucket("spec014").key(".ready").build(),
                    RequestBody.fromString("ready"));
                return true;
              });
      normalize.start();
      var generated =
          normalize.execInContainer(
              "ffmpeg",
              "-nostdin",
              "-v",
              "error",
              "-y",
              "-f",
              "lavfi",
              "-i",
              "testsrc2=size=64x64:rate=2:duration=62",
              "-c:v",
              "libx264",
              "/tmp/source.mp4");
      assertThat(generated.getExitCode()).withFailMessage(generated.getStderr()).isZero();
      normalize.copyFileFromContainer("/tmp/source.mp4", video.toString());
      try (var worker = processor(workerImage, "success")) {
        worker.start();
        var upload = uploaded(s3, video);
        send(upload);
        await()
            .atMost(Duration.ofSeconds(90))
            .untilAsserted(
                () -> {
                  assertThat(count("SELECT count(*) FROM chunks WHERE status='PROCESSED'"))
                      .isEqualTo(2);
                  assertThat(
                          count("SELECT count(*) FROM execution_slots WHERE released_at IS NULL"))
                      .isZero();
                  assertThat(committed()).isEqualTo(2);
                });
        assertThat(count("SELECT count(*) FROM chunk_executions")).isEqualTo(2);
        assertThat(count("SELECT count(*) FROM execution_result_receipts WHERE decision='APPLIED'"))
            .isEqualTo(2);
        var refs =
            jdbc.queryForList(
                "SELECT result_payload->>'resultKey' FROM chunk_executions ORDER BY id",
                String.class);
        for (var ref : refs) {
          var artifact =
              mapper.readTree(
                  s3.getObjectAsBytes(GetObjectRequest.builder().bucket("spec014").key(ref).build())
                      .asUtf8String());
          assertThat(artifact.get("verification").asBoolean()).isTrue();
          assertThat(artifact.get("actual_start_seconds").asText()).isNotBlank();
        }
        // 상태 이벤트는 중간 불명 상태와 중복 전달이 추가될 수 있다. 커밋한 종료 eventId의 실제 도착을 검증한다.
        var finished = publishedStatus("SUCCEEDED");
        assertThat(finished.path("execution").path("status").asText()).isEqualTo("SUCCEEDED");
        assertThat(finished.path("execution").path("progressPercent").asInt()).isEqualTo(100);
        assertThat(finished.path("status").asText()).isEqualTo("RUNNING");
        send(upload);
        var replay = normalizedPayloads(2);
        assertThat(replay).hasSize(2);
        assertThat(mapper.readTree(replay.get(0))).isEqualTo(mapper.readTree(replay.get(1)));
        assertThat(count("SELECT count(*) FROM jobs")).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM normalized_receipts")).isEqualTo(1);
      }
      try (var failure = processor(workerImage, "failure")) {
        failure.start();
        jdbc.execute(
            "ALTER TABLE execution_result_receipts ADD CONSTRAINT lifecycle_failure CHECK(false) NOT VALID");
        try {
          send(uploaded(s3, video));
          await()
              .atMost(Duration.ofSeconds(90))
              .untilAsserted(
                  () -> verify(results, atLeast(3)).apply(org.mockito.ArgumentMatchers.any()));
          assertThat(committed()).isEqualTo(2);
          assertThat(count("SELECT count(*) FROM execution_slots WHERE released_at IS NULL"))
              .isEqualTo(1);
          assertThat(count("SELECT count(*) FROM execution_result_receipts")).isEqualTo(2);
        } finally {
          jdbc.execute("ALTER TABLE execution_result_receipts DROP CONSTRAINT lifecycle_failure");
        }
        await()
            .atMost(Duration.ofSeconds(90))
            .untilAsserted(
                () -> {
                  assertThat(count("SELECT count(*) FROM chunks WHERE status='FAILED'"))
                      .isEqualTo(2);
                  assertThat(
                          count("SELECT count(*) FROM execution_slots WHERE released_at IS NULL"))
                      .isZero();
                  assertThat(committed()).isEqualTo(4);
                });
        assertThat(count("SELECT count(*) FROM chunk_executions WHERE status='FAILED'"))
            .isEqualTo(2);
        var failed = publishedStatus("FAILED");
        assertThat(failed.path("execution").path("status").asText()).isEqualTo("FAILED");
        assertThat(failed.path("failures")).hasSize(2);
        assertThat(
                count("SELECT count(*) FROM outbox_events WHERE event_type='analysis.completed'"))
            .isZero();
      }
    } finally {
      Files.deleteIfExists(video);
    }
  }
}
