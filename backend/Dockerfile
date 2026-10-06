# syntax=docker/dockerfile:1
# BE(Spring Boot) 컨테이너 이미지 (KAN-120).
# 빌드 컨텍스트는 backend/다 - 이 디렉터리가 자체 settings.gradle과 래퍼를 가진 독립
# Gradle 프로젝트이고, 루트 프로젝트(:app, Android)와는 별개다.
#   docker build -t accentury-backend:dev backend

# --- 빌드 스테이지 ---------------------------------------------------------
# 툴체인이 Java 25를 요구한다(build.gradle) - JDK도 25로 맞춰 foojay 리졸버가 런타임을
# 따로 내려받지 않게 한다.
FROM eclipse-temurin:25-jdk@sha256:8c0a84ea11c8f6ed52600fc19f1040121f2a162998e9f50a5faebbbad9172dcc AS build
WORKDIR /workspace

# 래퍼와 빌드 스크립트를 먼저 복사한다 - 소스만 바뀐 재빌드가 Gradle **배포본** 내려받기
# 레이어를 재사용한다. 의존성은 여기서 받지 않는다(--version은 배포본만 받는다) - 의존성
# 해석은 아래 bootJar에서 일어나고, 그 레이어는 COPY src 뒤라 소스가 바뀔 때마다 무효화된다.
# 그래서 의존성 캐시는 레이어가 아니라 BuildKit 캐시 마운트로 재사용한다.
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN --mount=type=cache,target=/root/.gradle \
    chmod +x gradlew && ./gradlew --no-daemon --version > /dev/null

COPY src src
# 테스트는 이미지 빌드에서 돌리지 않는다 - Testcontainers가 도커 데몬을 요구하는데(KAN-123)
# 빌드 컨테이너 안에는 없다. 테스트는 CI(test.yml)가 담당한다.
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon bootJar -x test \
 && rm -f build/libs/*-plain.jar \
 && mv build/libs/*.jar build/app.jar

# --- 실행 스테이지 ---------------------------------------------------------
FROM eclipse-temurin:25-jre@sha256:fcd7fd7b387f94bb2ac461478a7436ad8e349924c374ea8313919624dceae636 AS runtime

# 비루트 실행 (NFR-SC-04) - 컨테이너가 뚫려도 호스트 권한으로 번지지 않게 한다.
RUN groupadd --system --gid 10001 accentury \
 && useradd --system --uid 10001 --gid accentury --home-dir /app --shell /usr/sbin/nologin accentury

WORKDIR /app
COPY --from=build /workspace/build/app.jar /app/app.jar

# RDS 전역 CA 번들 (KAN-245). JDBC URL의 sslmode=verify-full이 이 파일로 RDS 서버 인증서와 호스트 이름을
# 검증한다 (SSM SPRING_DATASOURCE_URL의 sslrootcert, infra/modules/config). 체크섬을 고정해 빌드 중 받은
# 파일이 바뀌었으면 빌드가 실패한다 - AWS가 번들을 갱신하면(CA 교체 공지) 새 해시로 이 줄을 고친다.
# 로컬 compose의 PostgreSQL은 URL에 sslmode가 없어 이 파일을 쓰지 않는다.
# 디렉터리를 먼저 만든다 - ADD가 상위 디렉터리를 만들면 --chmod(644)가 디렉터리에도 걸려 실행 비트가 빠지고,
# 비루트 사용자가 파일에 닿지 못한다 (2026-10-03 로컬 빌드에서 확인).
RUN mkdir -m 755 /app/certs
ADD --checksum=sha256:fe45bbebf92ad3e27a583bbb2ddd1553c521ed4d49af5514dc0a40372ea5395c --chmod=644 \
    https://truststore.pki.rds.amazonaws.com/global/global-bundle.pem /app/certs/rds-global-bundle.pem
USER accentury

EXPOSE 8080

# JVM은 기본적으로 컨테이너 메모리 한도의 25%만 힙으로 쓴다 - 512MB 컨테이너면 힙이
# 128MB라 기동조차 빠듯하다. 한도의 75%를 쓰게 올리고, 힙이 마르면 살아 있는 척하지 말고
# 즉시 죽어 오케스트레이터가 다시 띄우게 한다(ExitOnOutOfMemoryError).
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=50.0 -XX:+ExitOnOutOfMemoryError"

# exec로 넘겨 java가 PID 1이 되게 한다 - 그래야 docker stop의 SIGTERM이 셸에 먹히지 않고
# 스프링의 우아한 종료까지 도달한다.
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
