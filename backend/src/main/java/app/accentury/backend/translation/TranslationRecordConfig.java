package app.accentury.backend.translation;

import app.accentury.backend.common.AccenturyProperties;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Duration;
import java.util.Objects;

/**
 * 번역 기록 저장의 배선 (KAN-266) - {@code accentury.translation.record-bucket}이 있을 때만 전부 만들어진다 (prod).
 * <p>
 * 없으면(staging, 로컬, 테스트) 이 클래스의 빈은 하나도 없고 {@code TranslationService}가
 * {@link TranslationRecordStore#NONE}으로 자리를 채운다. 값이 있는데 비어 있는 것("")은 설정 실수라 뜨지 않는다 -
 * 조용히 no-op으로 접으면 prod에서 기록이 안 쌓이는 원인이 묻힌다 ({@code TrainingConfig}와 같은 규율).
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "accentury.translation", name = "record-bucket")
class TranslationRecordConfig {

    /** 호출 1건의 상한 - 기록은 비동기라 응답과 무관하지만, S3가 멈추면 실행기 스레드가 이만큼 묶인다. */
    static final Duration API_CALL_TIMEOUT = Duration.ofSeconds(10);
    static final Duration API_CALL_ATTEMPT_TIMEOUT = Duration.ofSeconds(5);
    static final Duration CONNECTION_TIMEOUT = Duration.ofSeconds(2);

    /**
     * 대기 상한. 기록 1건은 작은 PutObject 하나라 스레드 하나로 충분하다. S3가 멈춰도 대기열이 이 수를 넘으면 버리므로
     * 메모리가 자라지 않는다 (단어 정오 기록과 같은 값).
     */
    static final int QUEUE_CAPACITY = 1000;

    /**
     * 자격 증명은 기본 제공자 체인이 태스크 역할에서 받는다 - 이 버킷에 PutObject만 허용된 역할이다 (fargate 모듈).
     * 음성 버킷의 클라이언트({@code trainingS3Client})와 따로 둔다 - 버킷도 리전 설정도 다른 기능이다.
     */
    @Bean
    S3Client translationS3Client(AccenturyProperties properties) {
        requireBucket(properties);
        S3ClientBuilder builder = S3Client.builder()
                .httpClientBuilder(ApacheHttpClient.builder()
                        .connectionTimeout(CONNECTION_TIMEOUT)
                        .socketTimeout(API_CALL_ATTEMPT_TIMEOUT))
                .overrideConfiguration(ClientOverrideConfiguration.builder()
                        .apiCallTimeout(API_CALL_TIMEOUT)
                        .apiCallAttemptTimeout(API_CALL_ATTEMPT_TIMEOUT)
                        .build());
        String region = properties.translation().region();
        if (region != null && !region.isBlank()) {
            builder.region(Region.of(region));
        }
        return builder.build();
    }

    /** 기록 전용 실행기. 큐가 넘치면 기본 거절이 나고 저장소가 받아 지표({@code failed})로 남긴다. 종료 때는 5초만 기다린다. */
    @Bean
    ThreadPoolTaskExecutor translationRecordExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("translation-record-");
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(QUEUE_CAPACITY);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(5);
        return executor;
    }

    @Bean
    TranslationRecordStore translationRecordStore(@Qualifier("translationS3Client") S3Client translationS3Client,
                                                  AccenturyProperties properties, TranslationSubjects subjects,
                                                  ObjectMapper objectMapper,
                                                  @Qualifier("translationRecordExecutor")
                                                  ThreadPoolTaskExecutor translationRecordExecutor,
                                                  MeterRegistry meterRegistry) {
        return new S3TranslationRecordStore(translationS3Client, requireBucket(properties), subjects, objectMapper,
                Clock.systemUTC(), translationRecordExecutor, meterRegistry);
    }

    private static String requireBucket(AccenturyProperties properties) {
        String bucket = Objects.requireNonNull(properties.translation().recordBucket());
        if (bucket.isBlank()) {
            throw new IllegalStateException("accentury.translation.record-bucket이 비어 있다 - 번역 기록을 끄려면 값을 지우고"
                    + "(SSM ACCENTURY_TRANSLATION_RECORDBUCKET 없음), 켜려면 버킷 이름을 넣는다 (KAN-266)");
        }
        return bucket;
    }
}
