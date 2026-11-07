# syntax=docker/dockerfile:1
# Spring 서비스 공통 이미지. 서비스는 빌드 인자로 고른다.
#   docker build --build-arg SERVICE=auth-service -t vlm/auth-service .
# 계약에서 코드를 만드는 서비스(quality-service)는 먼저 scripts/fetch-service-contracts.sh로 잠금 버전 계약을 받는다.
# 빌드 컨텍스트에는 작업 공간의 ../../service-contracts가 없어 받은 사본(.service-contracts/)을 CONTRACTS_DIR로 읽는다.
# 로컬 compose(docs 레포 local/)와 서비스 workflow가 같은 파일을 쓴다.

FROM eclipse-temurin:21.0.13_9-jdk-noble@sha256:114cabb73033ca4ce7ceec3deb62396bc1b716282bae051ed3c1d82e38fcab2d AS build
ARG SERVICE
ARG CONTRACTS_DIR=/workspace/.service-contracts/service-contracts
WORKDIR /workspace
COPY . .
# Gradle 배포본과 의존성은 BuildKit 캐시에 두어 다시 빌드할 때 받지 않는다. 테스트는 CI의 checks에서 돌린다.
# compose가 여러 서비스를 동시에 빌드하면 같은 캐시를 함께 연다. Gradle은 잠금을 가진 프로세스에 해제를 요청하는데
# 빌드마다 컨테이너가 달라 요청이 닿지 않고 60초 뒤 "Timeout waiting to lock journal cache"로 실패한다.
# sharing=locked로 이 캐시를 쓰는 빌드를 한 번에 하나씩 돌린다.
RUN --mount=type=cache,target=/root/.gradle,sharing=locked \
    test -n "${SERVICE}" \
    && CONTRACTS_DIR="${CONTRACTS_DIR}" ./gradlew --no-daemon ":services:${SERVICE}:bootJar" \
    && java -Djarmode=tools -jar "services/${SERVICE}/build/libs/${SERVICE}.jar" \
        extract --layers --launcher --destination /extracted

FROM eclipse-temurin:21.0.13_9-jre-noble@sha256:0eda5830ed7b669abc246a8dc520ea8a23cab06f9a8d4b6964109e2f2394e46f
# Kubernetes runAsNonRoot가 확인할 수 있도록 숫자 UID로 실행한다.
RUN groupadd --system --gid 10001 spring \
    && useradd --system --uid 10001 --gid spring --no-create-home spring
WORKDIR /app
# 자주 바뀌는 계층을 뒤에 두어, 코드만 바뀌면 의존성 계층은 캐시를 쓴다.
COPY --from=build /extracted/dependencies/ ./
COPY --from=build /extracted/spring-boot-loader/ ./
COPY --from=build /extracted/snapshot-dependencies/ ./
COPY --from=build /extracted/application/ ./
USER 10001:10001
# 컨테이너 메모리 상한에 맞춰 힙을 잡는다.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
EXPOSE 8080
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
