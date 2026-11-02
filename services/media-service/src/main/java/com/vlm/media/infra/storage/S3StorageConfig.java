package com.vlm.media.infra.storage;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * 저장소 클라이언트 빈.
 *
 * <p>자격 증명은 SDK 기본 체인을 쓴다. 운영은 IRSA 역할, 로컬은 {@code AWS_ACCESS_KEY_ID}·{@code AWS_SECRET_ACCESS_KEY}
 * 환경변수다.
 */
@Configuration(proxyBeanMethods = false)
class S3StorageConfig {

  @Bean(destroyMethod = "close")
  DefaultCredentialsProvider awsCredentialsProvider() {
    return DefaultCredentialsProvider.builder().build();
  }

  @Bean(destroyMethod = "close")
  S3Client s3Client(MediaStorageProperties properties, DefaultCredentialsProvider credentials) {
    return S3Clients.client(properties, credentials);
  }

  @Bean(destroyMethod = "close")
  S3Presigner s3Presigner(
      MediaStorageProperties properties, DefaultCredentialsProvider credentials) {
    return S3Clients.presigner(properties, credentials);
  }
}
