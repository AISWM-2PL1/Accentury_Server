package app.accentury.backend.translation;

import app.accentury.backend.auth.AppUser;
import app.accentury.backend.common.AccenturyProperties;
import app.accentury.backend.common.ApiException;
import app.accentury.backend.common.ErrorCode;
import app.accentury.backend.common.RateLimits;
import app.accentury.backend.observability.ServiceMetrics;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 사투리 텍스트 번역 (KAN-266, 명세서 §3.18) - 두 방향이다 (KAN-316, {@link TranslationDirection}).
 * <p>
 * 순서: 형식 검사(400, {@code text}와 {@code direction}) → 분당 제한(429 {@code RATE_LIMITED}) → 길이(422 {@code TRANSLATION_TOO_LONG}) →
 * 서버 사전 검사(422 {@code TRANSLATION_REJECTED}) → 일일 한도(429 {@code TRANSLATION_DAILY_LIMIT_EXCEEDED}) →
 * LLM 호출(실패면 503 {@code TRANSLATION_UNAVAILABLE}) → 응답 검사(어기면 422 {@code TRANSLATION_REJECTED}) → 200.
 * 400과 분당 제한을 뺀 모든 끝에서 결과 종류를 지표로 세고 번역 기록을 넘긴다.
 * <p>
 * <b>입력과 출력 텍스트는 로그에 남기지 않는다</b> (§2.6) - 로그는 결과 종류, 입력 길이, 소요 시간뿐이다.
 * 계정당 하루 {@value AccenturyProperties.Translation#DAILY_LIMIT}건이고 성공한 번역만 센다 (2026-10-15 결정 - 10-04의
 * 「제한 없음」을 바꿨다). 한도 칸은 LLM 호출 전에 잡고 성공이 아니면 돌려준다 ({@link TranslationQuota}). LLM 호출 수는
 * 칸과 별개로 하루 {@value AccenturyProperties.Translation#DAILY_CALL_LIMIT}번까지이고 돌려주지 않는다 (KAN-322).
 */
@Service
class TranslationService {

    private static final Logger log = LoggerFactory.getLogger(TranslationService.class);

    /**
     * 호출 수 상한의 429 문구 - 코드는 성공 5건 한도와 같은 {@code TRANSLATION_DAILY_LIMIT_EXCEEDED}이고 문구만 다르다 (PR #66
     * 리뷰). 성공이 5건 미만인 사용자에게 「5회를 다 썼다」고 하지 않는다. 앱과 웹은 서버 문구를 그대로 보여 준다.
     */
    static final String CALL_LIMIT_MESSAGE = "오늘 번역 요청 횟수(%d회)를 다 썼습니다. 내일 다시 시도해 주세요."
            .formatted(AccenturyProperties.Translation.DAILY_CALL_LIMIT);

    private final DialectTranslator translator;
    private final TranslationQuota quota;
    private final TranslationRecordStore records;
    private final RateLimits rateLimits;
    private final Clock clock;
    /** 결과 종류 x 방향 카운터 - 지표는 하나고 태그 {@code result}, {@code direction}으로 가른다. */
    private final Map<TranslationDirection, Map<TranslationResult, Counter>> requests =
            new EnumMap<>(TranslationDirection.class);

    @Autowired
    TranslationService(DialectTranslator translator, TranslationQuota quota,
                       ObjectProvider<TranslationRecordStore> records, RateLimits rateLimits,
                       MeterRegistry meterRegistry) {
        this(translator, quota, records.getIfAvailable(() -> TranslationRecordStore.NONE), rateLimits, Clock.systemUTC(),
                meterRegistry);
    }

    TranslationService(DialectTranslator translator, TranslationQuota quota, TranslationRecordStore records,
                       RateLimits rateLimits, Clock clock, MeterRegistry meterRegistry) {
        this.translator = translator;
        this.quota = quota;
        this.records = records;
        this.rateLimits = rateLimits;
        this.clock = clock;
        for (TranslationDirection direction : TranslationDirection.values()) {
            Map<TranslationResult, Counter> byResult = new EnumMap<>(TranslationResult.class);
            for (TranslationResult result : TranslationResult.values()) {
                byResult.put(result, Counter.builder(ServiceMetrics.TRANSLATION_REQUESTS)
                        .description("번역 요청 - 태그 result는 success | rejected | too_long | limited | llm_failed (KAN-266), "
                                + "direction은 toDialect | toStandard (KAN-316)")
                        .tag("result", result.metricTag())
                        .tag("direction", direction.apiValue())
                        .register(meterRegistry));
            }
            requests.put(direction, byResult);
        }
    }

    /**
     * @throws ApiException 400 {@code VALIDATION_FAILED}, 422 {@code TRANSLATION_TOO_LONG}, 422 {@code TRANSLATION_REJECTED},
     *                      429 {@code RATE_LIMITED}, 429 {@code TRANSLATION_DAILY_LIMIT_EXCEEDED},
     *                      503 {@code TRANSLATION_UNAVAILABLE}
     */
    TranslationResponse translate(AppUser user, @Nullable TranslationRequest request) {
        long started = System.nanoTime();
        Instant requestedAt = Instant.now(clock);
        String text = request != null ? request.text() : null;
        if (text == null || text.isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "text가 비어 있습니다.");
        }
        TranslationDirection direction = direction(request);
        // 기록을 넘기는 어떤 끝보다 앞이다 - 분당 제한을 넘긴 요청까지 남기면 만료 없는 버킷에 요청 수만큼 객체가 쌓인다.
        // 이 429의 수는 RateLimits의 지표가 센다.
        rateLimits.check(RateLimits.Scope.TRANSLATION, user.id().toString());
        Attempt attempt = new Attempt(UUID.randomUUID(), requestedAt, started, user.id(), direction, text);
        String input = text.strip();

        if (TranslationHarness.length(input) > AccenturyProperties.Translation.MAX_INPUT_LENGTH) {
            finish(attempt, TranslationResult.TOO_LONG, null, null);
            throw new ApiException(ErrorCode.TRANSLATION_TOO_LONG);
        }
        if (TranslationHarness.rejectsBeforeLlm(input)) {
            finish(attempt, TranslationResult.REJECTED, null, null);
            throw new ApiException(ErrorCode.TRANSLATION_REJECTED);
        }

        TranslationQuota.Slot slot;
        try {
            slot = quota.acquire(user.id());
        } catch (TranslationQuota.Exhausted e) {
            finish(attempt, TranslationResult.LIMITED, null, null);
            throw e.callLimit()
                    ? new ApiException(ErrorCode.TRANSLATION_DAILY_LIMIT_EXCEEDED, CALL_LIMIT_MESSAGE)
                    : new ApiException(ErrorCode.TRANSLATION_DAILY_LIMIT_EXCEEDED);
        } catch (TranslationQuota.StoreUnavailable e) {
            finish(attempt, TranslationResult.LLM_FAILED, null, null);
            throw new ApiException(ErrorCode.TRANSLATION_UNAVAILABLE);
        }
        boolean succeeded = false;
        try {
            TranslationResponse response = translateWithin(attempt, input, slot);
            succeeded = true;
            return response;
        } finally {
            if (!succeeded) {
                quota.release(slot);
            }
        }
    }

    /** 요청의 방향 - 없으면 기본값({@code toDialect}), 모르는 값은 400이다 (KAN-316). 400은 기록하지 않는다. */
    private static TranslationDirection direction(TranslationRequest request) {
        String value = request.direction();
        if (value == null) {
            return TranslationDirection.TO_DIALECT;
        }
        return TranslationDirection.fromApiValue(value)
                .orElseThrow(() -> new ApiException(ErrorCode.VALIDATION_FAILED,
                        "direction은 toDialect 또는 toStandard여야 합니다."));
    }

    /** 한도 칸을 잡은 뒤의 LLM 호출과 응답 검사 - 여기서 성공으로 끝나지 않으면 부르는 쪽이 칸을 돌려준다. */
    private TranslationResponse translateWithin(Attempt attempt, String input, TranslationQuota.Slot slot) {
        long llmStarted = System.nanoTime();
        DialectTranslator.Reply reply;
        try {
            reply = translator.translate(input, attempt.direction());
        } catch (DialectTranslator.Unavailable e) {
            finish(attempt, TranslationResult.LLM_FAILED, null, elapsedMs(llmStarted));
            throw new ApiException(ErrorCode.TRANSLATION_UNAVAILABLE);
        }
        long llmMs = elapsedMs(llmStarted);

        String translated = reply.output();
        if (reply.kind() != DialectTranslator.Kind.TRANSLATED || translated == null
                || !TranslationHarness.acceptsOutput(input, translated)) {
            log.info("번역 불가 판정 kind={} direction={}", reply.kind(), attempt.direction().apiValue());
            finish(attempt, TranslationResult.REJECTED, null, llmMs);
            throw new ApiException(ErrorCode.TRANSLATION_REJECTED);
        }
        String output = translated.strip();
        finish(attempt, TranslationResult.SUCCESS, output, llmMs);
        return TranslationResponse.of(attempt.direction(), output, quota.remaining(slot));
    }

    /**
     * 끝 처리 - 지표, 로그(텍스트 없이), 기록. 기록 저장소는 예외를 내지 않고 기다리게 하지 않는다.
     * <p>
     * 길이 초과면 기록의 입력을 앞 100자로 자른다 (PR #34 리뷰 P2) - JSON 본문에는 크기 상한이 없어 원문째 넘기면 수 MB
     * 입력이 만료 없는 버킷과 기록 대기열(1000칸)에 그대로 쌓인다. 원래 길이는 {@code inputLength}로 남는다.
     */
    private void finish(Attempt attempt, TranslationResult result, @Nullable String output, @Nullable Long llmMs) {
        long totalMs = elapsedMs(attempt.started());
        int inputLength = TranslationHarness.length(attempt.text());
        String input = result == TranslationResult.TOO_LONG
                ? leading(attempt.text(), AccenturyProperties.Translation.MAX_INPUT_LENGTH)
                : attempt.text();
        requests.get(attempt.direction()).get(result).increment();
        log.info("번역 requestId={} direction={} result={} inputLength={} totalMs={} llmMs={}", attempt.requestId(),
                attempt.direction().apiValue(), result, inputLength, totalMs, llmMs);
        records.save(new TranslationRecord(attempt.requestId(), attempt.requestedAt(), attempt.userId(),
                attempt.direction(), input, inputLength, output, result, totalMs, llmMs, translator.model()));
    }

    /** 앞에서 코드 포인트 {@code count}개 - 서로게이트 쌍 가운데를 자르지 않는다. */
    private static String leading(String text, int count) {
        if (TranslationHarness.length(text) <= count) {
            return text;
        }
        return text.substring(0, text.offsetByCodePoints(0, count));
    }

    private static long elapsedMs(long startedNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos);
    }

    private record Attempt(UUID requestId, Instant requestedAt, long started, UUID userId,
                           TranslationDirection direction, String text) {
    }
}
