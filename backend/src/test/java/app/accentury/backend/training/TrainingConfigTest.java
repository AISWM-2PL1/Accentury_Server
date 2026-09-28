package app.accentury.backend.training;

import app.accentury.backend.common.AccenturyProperties;
import app.accentury.backend.common.SsmPlaceholder;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.services.s3.S3Client;
import tools.jackson.databind.ObjectMapper;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 버킷 설정 유무가 빈 존재를 가른다 (KAN-201 AC) - 없으면 S3 클라이언트도 저장 빈도 없다. 버킷이 있으면 가명 키가
 * 필수이고, 테스터 목록은 없어도 빈 목록으로 뜬다 (KAN-239).
 */
class TrainingConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(Collaborators.class, TrainingConfig.class)
            .withPropertyValues("accentury.session.ttl=30m");

    @Test
    void 버킷이_없으면_S3_클라이언트도_저장_빈도_없다() {
        runner.run(context -> {
            assertFalse(context.containsBean("trainingS3Client"));
            assertEquals(0, context.getBeansOfType(TrainingSampleStore.class).size());
        });
    }

    private static final String KEY = "accentury.training.pseudonym-key=" + "k".repeat(64);
    private static final String BUCKET = "accentury.training.bucket=accentury-staging-training-123456789012";
    private static final String REGION = "accentury.training.region=ap-northeast-2";

    @Test
    void 버킷이_있으면_S3_저장_빈이_뜬다() {
        runner.withPropertyValues(BUCKET, REGION, KEY)
                .run(context -> {
                    assertTrue(context.containsBean("trainingS3Client"));
                    assertInstanceOf(S3TrainingSampleStore.class, context.getBean(TrainingSampleStore.class));
                });
    }

    @Test
    void 빈_문자열_버킷은_설정_실수라_기동을_세운다() {
        // 리전을 명시한다 - CI 러너처럼 AWS 리전이 없는 환경에서는 SDK의 리전 오류가 먼저 나와 원인을 가린다.
        runner.withPropertyValues("accentury.training.bucket=", "accentury.training.region=ap-northeast-2")
                .run(context -> {
                    Throwable failure = context.getStartupFailure();
                    assertTrue(failure != null && rootMessage(failure).contains("accentury.training.bucket"),
                            () -> "기동 실패 사유가 다르다: " + failure);
                });
    }

    // === 동의 테스터 목록과 가명 키 (KAN-239) ===

    @Test
    void 버킷이_있는데_가명_키가_없으면_기동을_세운다() {
        // 키 없이 뜨면 세션 ID 원문을 쓸 수밖에 없다 - 그 원문이 계정과 이어지는 고리다.
        runner.withPropertyValues(BUCKET, REGION)
                .run(context -> assertStartupFailure(context.getStartupFailure(), "pseudonym-key"));
    }

    @Test
    void 자리_표시_값이나_짧은_가명_키는_거부한다() {
        runner.withPropertyValues(BUCKET, REGION, "accentury.training.pseudonym-key=" + SsmPlaceholder.UNSET)
                .run(context -> assertStartupFailure(context.getStartupFailure(), "pseudonym-key"));
        runner.withPropertyValues(BUCKET, REGION, "accentury.training.pseudonym-key=short")
                .run(context -> assertStartupFailure(context.getStartupFailure(), "pseudonym-key"));
    }

    @Test
    void 테스터_목록이_없거나_자리_표시_값이면_빈_목록이다() {
        runner.withPropertyValues(BUCKET, REGION, KEY)
                .run(context -> assertEquals(Set.of(), testerIds(context)));
        runner.withPropertyValues(BUCKET, REGION, KEY, "accentury.training.tester-ids=" + SsmPlaceholder.UNSET)
                .run(context -> assertEquals(Set.of(), testerIds(context)));
    }

    @Test
    void 쉼표로_이은_테스터_목록은_UUID_집합이다() {
        // SSM StringList는 태스크에 쉼표로 이은 한 문자열로 들어온다 - 환경 변수 ACCENTURY_TRAINING_TESTERIDS와 같은 모양이다.
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        runner.withPropertyValues(BUCKET, REGION, KEY, "accentury.training.tester-ids=" + a + "," + b)
                .run(context -> assertEquals(Set.of(a, b), testerIds(context)));
    }

    @Test
    void UUID가_아닌_테스터_항목은_기동을_세운다() {
        runner.withPropertyValues(BUCKET, REGION, KEY, "accentury.training.tester-ids=tester-1")
                .run(context -> assertStartupFailure(context.getStartupFailure(), "tester-ids"));
    }

    private static Set<UUID> testerIds(AssertableApplicationContext context) {
        assertNull(context.getStartupFailure());
        return TrainingConfig.testerIds(context.getBean(AccenturyProperties.class));
    }

    private static void assertStartupFailure(@Nullable Throwable failure, String expected) {
        assertTrue(failure != null && rootMessage(failure).contains(expected),
                () -> "기동 실패 사유가 다르다: " + failure);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(AccenturyProperties.class)
    static class Collaborators {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }

    /** 예외 사슬을 끝까지 따라가 마지막 원인의 메시지를 돌려준다. */
    private static String rootMessage(Throwable failure) {
        Throwable cause = failure;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return String.valueOf(cause.getMessage());
    }
}
