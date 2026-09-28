package app.accentury.backend.training;

import app.accentury.backend.common.AccenturyProperties;
import app.accentury.backend.common.SsmPlaceholder;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * 학습 샘플 저장의 배선 (KAN-201) - {@code accentury.training.consented-bucket}이 있을 때만 전부 만들어진다.
 * <p>
 * 없으면(로컬, 테스트, prod) 이 클래스의 빈은 하나도 없다. S3 클라이언트도 없다 - 소비자
 * ({@code AnalysisDispatchConfig})가 {@link TrainingSampleStore#NONE}으로 자리를 채운다. 값이 있는데
 * 비어 있는 것("")은 설정 실수라 뜨지 않는다 - 조용히 no-op으로 접으면 staging에서 샘플이 안 쌓이는
 * 원인이 묻힌다.
 * <p>
 * 스위치 이름이 KAN-201의 {@code bucket}이 아니라 {@code consented-bucket}인 것은 롤백 대비다 (KAN-239 PR #4 리뷰 P2).
 * 옛 이름이면 KAN-239 이전 이미지(수동 롤백, 반영 실패 시 자동 롤백)가 같은 파라미터로 뜨면서 테스터 한정과 가명 없이
 * 모든 세션을 원문 ID로 다시 저장한다. 이름을 바꿔 두면 옛 이미지는 이 값을 모르므로 수집이 꺼진 채 뜬다.
 * <p>
 * 버킷이 있어도 저장 대상은 동의한 테스터 계정의 세션뿐이고, 세션 ID는 가명으로 바뀐다 (KAN-239,
 * {@link TrainingSpeakers}). 그래서 가명 키가 없으면 기동을 세우고, 테스터 목록이 없으면 빈 목록으로 뜬다.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "accentury.training", name = "consented-bucket")
class TrainingConfig {

    /**
     * 호출 1건(시도 재시도 포함)의 상한. 저장은 분석 워커(dispatch-concurrency 1)가 동기로 하므로 S3가 느리거나
     * 죽으면 그 시간만큼 뒤 작업의 큐 대기가 늘어난다 - SDK 기본값(소켓 30초 x 재시도 3회)이면 워커가 수 분
     * 멈춰 큐의 작업이 유실 한도(queued-timeout 5분)에 닿는다 (Codex astra 리뷰 P2). WAV 1MB 왕복은 수십 ms라
     * 시도 5초, 호출 10초면 넉넉하고, 넘기면 저장소가 실패로 삼킨다 - 분석 결과는 이미 확정된 뒤다. 샘플 하나가
     * PutObject 2회(WAV, JSON)라 워커 점유의 최악은 20초다(WAV가 실패하면 JSON은 시도하지 않아 10초).
     */
    static final Duration API_CALL_TIMEOUT = Duration.ofSeconds(10);
    static final Duration API_CALL_ATTEMPT_TIMEOUT = Duration.ofSeconds(5);
    static final Duration CONNECTION_TIMEOUT = Duration.ofSeconds(2);

    /** 가명 키의 최소 길이 - Terraform은 64자 영숫자로 만든다 (config 모듈). */
    static final int MIN_PSEUDONYM_KEY_LENGTH = 32;

    private static final Logger log = LoggerFactory.getLogger(TrainingConfig.class);

    /**
     * 자격 증명은 기본 제공자 체인이 태스크 역할에서 받는다 - 버킷 하나에 PutObject만 허용된 역할이다
     * (fargate 모듈). 리전은 설정({@code accentury.training.region})이 있으면 그 값이고, 없으면 SDK 기본
     * 체인(태스크의 {@code AWS_REGION})이다. 동기 클라이언트(apache)이고 타임아웃은 위 상수다.
     */
    @Bean
    S3Client trainingS3Client(AccenturyProperties properties) {
        // 빈 문자열 검사는 여기서도 한다 - 이 빈이 저장 빈보다 먼저 만들어지므로, 리전을 못 찾는 환경(CI 러너)에서는
        // SDK의 리전 오류가 먼저 나와 "설정 실수"라는 원인이 묻힌다.
        requireBucket(properties);
        S3ClientBuilder builder = S3Client.builder()
                .httpClientBuilder(ApacheHttpClient.builder()
                        .connectionTimeout(CONNECTION_TIMEOUT)
                        .socketTimeout(API_CALL_ATTEMPT_TIMEOUT))
                .overrideConfiguration(ClientOverrideConfiguration.builder()
                        .apiCallTimeout(API_CALL_TIMEOUT)
                        .apiCallAttemptTimeout(API_CALL_ATTEMPT_TIMEOUT)
                        .build());
        String region = properties.training().region();
        if (region != null && !region.isBlank()) {
            builder.region(Region.of(region));
        }
        return builder.build();
    }

    @Bean
    TrainingSampleStore trainingSampleStore(S3Client trainingS3Client, AccenturyProperties properties,
                                            ObjectMapper objectMapper, MeterRegistry meterRegistry) {
        TrainingSpeakers speakers = new TrainingSpeakers(testerIds(properties), requirePseudonymKey(properties));
        // 켜진 채로 목록이 비어 있는 것은 정상 상태다(동의 받기 전) - 샘플이 안 쌓이는 이유를 기동 로그에 한 줄 남긴다.
        log.info("학습 샘플 저장 켜짐 - 동의 테스터 {}명의 세션만 저장한다 (KAN-239)", speakers.testerCount());
        return new S3TrainingSampleStore(trainingS3Client, requireBucket(properties), speakers, objectMapper,
                Clock.systemUTC(), meterRegistry);
    }

    /**
     * 동의 테스터 목록 (KAN-239). 없거나 자리 표시 값이면 빈 집합이다 - 저장하지 않는 쪽이 안전한 기본값이다.
     * UUID가 아닌 항목은 기동을 세운다 - 조용히 건너뛰면 동의한 테스터의 샘플이 빠지는 원인이 묻힌다.
     */
    static Set<UUID> testerIds(AccenturyProperties properties) {
        List<String> raw = properties.training().testerIds();
        if (raw == null) {
            return Set.of();
        }
        Set<UUID> ids = new HashSet<>();
        for (String entry : raw) {
            String value = entry.strip();
            if (value.isEmpty() || SsmPlaceholder.UNSET.equals(value)) {
                continue;
            }
            try {
                ids.add(UUID.fromString(value));
            } catch (IllegalArgumentException e) {
                // 원인은 잇지 않는다 - UUID 파서의 메시지가 맨 아래 원인이 되면 어느 설정이 틀렸는지가 가려진다.
                throw new IllegalStateException("accentury.training.tester-ids에 UUID가 아닌 항목이 있다 ('" + value
                        + "') - SSM ACCENTURY_TRAINING_TESTERIDS는 app_user.id를 쉼표로 이은 값이다 (KAN-239)");
            }
        }
        return ids;
    }

    /**
     * 가명 키는 버킷이 있으면 필수다 (KAN-239) - 없으면 세션 ID 원문을 쓸 수밖에 없는데, 그 원문이 계정과
     * 이어지는 고리다. 자리 표시 값과 짧은 키도 같이 거부한다(레포를 읽은 누구나 아는 값이거나 추측 가능한 값).
     */
    private static String requirePseudonymKey(AccenturyProperties properties) {
        String key = properties.training().pseudonymKey();
        // HMAC이 쓰는 원문 그대로 잰다 - 앞뒤 공백을 떼고 재면 공백으로 채운 짧은 키가 통과한다 (PR #4 리뷰 P3).
        if (key == null || key.isBlank() || key.length() < MIN_PSEUDONYM_KEY_LENGTH
                || SsmPlaceholder.UNSET.equals(key)) {
            throw new IllegalStateException("accentury.training.pseudonym-key가 없거나 " + MIN_PSEUDONYM_KEY_LENGTH
                    + "자 미만이다 - 학습 샘플을 저장하려면 SSM ACCENTURY_TRAINING_PSEUDONYMKEY가 있어야 한다 (KAN-239)");
        }
        return key;
    }

    private static String requireBucket(AccenturyProperties properties) {
        String bucket = Objects.requireNonNull(properties.training().consentedBucket());
        if (bucket.isBlank()) {
            throw new IllegalStateException("accentury.training.consented-bucket이 비어 있다 - 학습 데이터 저장을 끄려면 "
                    + "값을 지우고(SSM ACCENTURY_TRAINING_CONSENTEDBUCKET 없음), 켜려면 버킷 이름을 넣는다 (KAN-201)");
        }
        return bucket;
    }
}
