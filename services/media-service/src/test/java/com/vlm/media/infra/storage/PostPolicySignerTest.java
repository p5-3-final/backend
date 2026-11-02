package com.vlm.media.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;

class PostPolicySignerTest {

  private static final Instant NOW = Instant.parse("2026-11-03T09:30:00Z");
  private static final PostPolicySigner.PostPolicy POLICY =
      new PostPolicySigner.PostPolicy(
          "vlm-media",
          "sources/v1/original",
          "video/mp4",
          1000,
          Instant.parse("2026-11-03T10:30:00Z"),
          "ap-northeast-2");

  @Test
  @DisplayName("[SPEC-003 R-2] 서명 키는 AWS 문서의 SigV4 서명 키 예제와 같다")
  void derivesSigningKeyLikeAwsExample() {
    // AWS 문서 "Signature Version 4 서명 키 만들기" 예제 값
    byte[] key =
        PostPolicySigner.signingKey(
            "wJalrXUtnFEMI/K7MDENG+bPxRfiCYEXAMPLEKEY", "20120215", "us-east-1", "iam");

    assertThat(HexFormat.of().formatHex(key))
        .isEqualTo("f4780e2d9f65fa895f9c67b32ce1baf0b0d8a43505a000a1a9e090d414db404d");
  }

  @Test
  @DisplayName("[SPEC-003 R-2] [NFR-6] 정책은 키·형식·정확한 크기·1시간 만료를 조건으로 담고 서명이 맞다")
  void signsPolicyWithExactSize() throws Exception {
    Map<String, String> fields =
        PostPolicySigner.sign(POLICY, AwsBasicCredentials.create("AKID", "SECRET"), NOW);

    JsonNode policy = new ObjectMapper().readTree(Base64.getDecoder().decode(fields.get("policy")));
    assertThat(policy.get("expiration").asText()).isEqualTo("2026-11-03T10:30:00Z");
    assertThat(policy.get("conditions").toString())
        .contains("{\"bucket\":\"vlm-media\"}")
        .contains("{\"key\":\"sources/v1/original\"}")
        .contains("{\"Content-Type\":\"video/mp4\"}")
        .contains("[\"content-length-range\",1000,1000]")
        .contains("{\"x-amz-algorithm\":\"AWS4-HMAC-SHA256\"}")
        .contains("{\"x-amz-credential\":\"AKID/20261103/ap-northeast-2/s3/aws4_request\"}")
        .contains("{\"x-amz-date\":\"20261103T093000Z\"}");
    assertThat(fields)
        .containsEntry("key", "sources/v1/original")
        .containsEntry("Content-Type", "video/mp4")
        .containsEntry("x-amz-algorithm", "AWS4-HMAC-SHA256")
        .containsEntry("x-amz-credential", "AKID/20261103/ap-northeast-2/s3/aws4_request")
        .containsEntry("x-amz-date", "20261103T093000Z")
        .doesNotContainKey("x-amz-security-token");
    byte[] signingKey = PostPolicySigner.signingKey("SECRET", "20261103", "ap-northeast-2", "s3");
    assertThat(fields.get("x-amz-signature")).isEqualTo(hmacHex(signingKey, fields.get("policy")));
  }

  @Test
  @DisplayName("[SPEC-003 R-2] 임시 자격 증명(IRSA)이면 세션 토큰을 정책과 폼 필드에 넣는다")
  void includesSessionToken() throws Exception {
    Map<String, String> fields =
        PostPolicySigner.sign(POLICY, AwsSessionCredentials.create("AKID", "SECRET", "TOKEN"), NOW);

    String policy =
        new String(Base64.getDecoder().decode(fields.get("policy")), StandardCharsets.UTF_8);
    assertThat(policy).contains("{\"x-amz-security-token\":\"TOKEN\"}");
    assertThat(fields).containsEntry("x-amz-security-token", "TOKEN");
  }

  private static String hmacHex(byte[] key, String data) throws Exception {
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(key, "HmacSHA256"));
    return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
  }
}
