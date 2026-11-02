package com.vlm.media.infra.storage;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;

/**
 * 브라우저 POST 업로드 정책을 AWS SigV4로 서명한다. AWS SDK v2가 지원하지 않아 직접 만든다(plan 5절).
 *
 * <p>형식은 AWS 문서 "Browser-Based Uploads Using POST (AWS Signature Version 4)"를 따른다.
 */
final class PostPolicySigner {

  private static final String ALGORITHM = "AWS4-HMAC-SHA256";
  private static final DateTimeFormatter DATE =
      DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC);
  private static final DateTimeFormatter DATE_TIME =
      DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);
  private static final ObjectMapper JSON = new ObjectMapper();

  private PostPolicySigner() {}

  /** SigV4 서명 키: HMAC(HMAC(HMAC(HMAC("AWS4" + secret, date), region), service), "aws4_request"). */
  static byte[] signingKey(String secretKey, String date, String region, String service) {
    byte[] key = hmac(("AWS4" + secretKey).getBytes(StandardCharsets.UTF_8), date);
    key = hmac(key, region);
    key = hmac(key, service);
    return hmac(key, "aws4_request");
  }

  /** 폼 필드(정책과 서명 포함)를 만든다. 브라우저는 이 필드를 모두 넣고 마지막에 {@code file}을 넣어 보낸다. */
  static Map<String, String> sign(PostPolicy policy, AwsCredentials credentials, Instant now) {
    String date = DATE.format(now);
    String amzDate = DATE_TIME.format(now);
    String credential =
        credentials.accessKeyId() + "/" + date + "/" + policy.region() + "/s3/aws4_request";

    Map<String, String> fields = new LinkedHashMap<>();
    fields.put("key", policy.key());
    fields.put("Content-Type", policy.contentType());
    fields.put("x-amz-algorithm", ALGORITHM);
    fields.put("x-amz-credential", credential);
    fields.put("x-amz-date", amzDate);
    if (credentials instanceof AwsSessionCredentials session) {
      // IRSA 같은 임시 자격 증명은 세션 토큰이 없으면 저장소가 서명을 거절한다.
      fields.put("x-amz-security-token", session.sessionToken());
    }

    List<Object> conditions = new ArrayList<>();
    conditions.add(Map.of("bucket", policy.bucket()));
    fields.forEach((name, value) -> conditions.add(Map.of(name, value)));
    // SPEC-003 R-2: 신고한 크기와 다른 파일은 저장소가 거절한다.
    conditions.add(List.of("content-length-range", policy.sizeBytes(), policy.sizeBytes()));

    Map<String, Object> document = new LinkedHashMap<>();
    document.put(
        "expiration",
        DateTimeFormatter.ISO_INSTANT.format(policy.expiresAt().truncatedTo(ChronoUnit.SECONDS)));
    document.put("conditions", conditions);
    String encodedPolicy =
        Base64.getEncoder().encodeToString(toJson(document).getBytes(StandardCharsets.UTF_8));

    byte[] key = signingKey(credentials.secretAccessKey(), date, policy.region(), "s3");
    fields.put("policy", encodedPolicy);
    fields.put("x-amz-signature", HexFormat.of().formatHex(hmac(key, encodedPolicy)));
    return Map.copyOf(fields);
  }

  private static byte[] hmac(byte[] key, String data) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(key, "HmacSHA256"));
      return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("HmacSHA256을 쓸 수 없다", e);
    }
  }

  private static String toJson(Object value) {
    try {
      return JSON.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("업로드 정책을 JSON으로 만들 수 없다", e);
    }
  }

  /** 서명할 업로드 조건. */
  record PostPolicy(
      String bucket,
      String key,
      String contentType,
      long sizeBytes,
      Instant expiresAt,
      String region) {}
}
