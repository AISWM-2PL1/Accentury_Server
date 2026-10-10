package app.accentury.backend.learning;

import app.accentury.backend.common.AccenturyProperties;
import app.accentury.backend.common.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

/**
 * PROCESSING에 남은 억양 학습 채점 시도를 종결한다 (KAN-267) - 레벨테스트 {@code AnalysisJobTimeout}과 같은 두 단계,
 * 같은 상한이다 (§3.4). 시작한 지 {@code processing-timeout}이 지나면 {@code ANALYSIS_TIMEOUT}, 시작하지 못한 채
 * {@code queued-timeout}이 지나면(프로세스 재시작으로 큐가 유실된 경우 등) {@code ANALYSIS_UNAVAILABLE}이다. 둘 다
 * RETRYABLE_FAILED라 클라이언트는 재녹음(새 키)으로 넘어간다.
 */
@Component
class IntonationAttemptTimeout {

    private static final Logger log = LoggerFactory.getLogger(IntonationAttemptTimeout.class);

    private final IntonationAttemptRepository attempts;
    private final AccenturyProperties properties;

    IntonationAttemptTimeout(IntonationAttemptRepository attempts, AccenturyProperties properties) {
        this.attempts = attempts;
        this.properties = properties;
    }

    @Scheduled(initialDelay = 30, fixedDelay = 30, timeUnit = TimeUnit.SECONDS)
    void failStuckAttempts() {
        // 문장마다 자기 트랜잭션이다 (레벨테스트 스위퍼와 같은 이유 - 잠금을 쥔 채 다음 문장으로 가지 않는다).
        Instant now = Instant.now();
        int stuck = attempts.failStartedBefore(now.minus(properties.analysis().processingTimeout()),
                ErrorCode.ANALYSIS_TIMEOUT.name(), now);
        int lost = attempts.failUnstartedBefore(now.minus(properties.analysis().queuedTimeout()),
                ErrorCode.ANALYSIS_UNAVAILABLE.name(), now);
        if (stuck > 0 || lost > 0) {
            log.warn("PROCESSING 잔류 학습 채점 시도 종결 - 실행 잔류 {}건, 큐 유실 {}건", stuck, lost);
        }
    }
}
