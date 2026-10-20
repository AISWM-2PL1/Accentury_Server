package app.accentury.backend.translation;

import app.accentury.backend.auth.AppUser;
import app.accentury.backend.common.AccenturyProperties;
import app.accentury.backend.common.ApiException;
import app.accentury.backend.common.ErrorCode;
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
 * 사투리 텍스트 번역 (KAN-266, 명세서 §3.18).
 * <p>
 * 순서: 형식 검사(400) → 길이(422 {@code TRANSLATION_TOO_LONG}) → 서버 사전 검사(422 {@code TRANSLATION_REJECTED}) →
 * 일일 한도(429 {@code TRANSLATION_DAILY_LIMIT_EXCEEDED}) → LLM 호출(실패면 503 {@code TRANSLATION_UNAVAILABLE}) →
 * 응답 검사(어기면 422 {@code TRANSLATION_REJECTED}) → 200.
 * 400을 뺀 모든 끝에서 결과 종류를 지표로 세고 번역 기록을 넘긴다.
 * <p>
 * <b>입력과 출력 텍스트는 로그에 남기지 않는다</b> (§2.6) - 로그는 결과 종류, 입력 길이, 소요 시간뿐이다.
 * 계정당 하루 {@value AccenturyProperties.Translation#DAILY_LIMIT}건이고 성공한 번역만 센다 (2026-10-15 결정 - 10-04의
 * 「제한 없음」을 바꿨다). 한도 칸은 LLM 호출 전에 잡고 성공이 아니면 돌려준다 ({@link TranslationQuota}).
 */
@Service
class TranslationService {

    private static final Logger log = LoggerFactory.getLogger(TranslationService.class);

    private final DialectTranslator translator;
    private final TranslationQuota quota;
    private final TranslationRecordStore records;
    private final Clock clock;
    private final Map<TranslationResult, Counter> requests = new EnumMap<>(TranslationResult.class);

    @Autowired
    TranslationService(DialectTranslator translator, TranslationQuota quota,
                       ObjectProvider<TranslationRecordStore> records, MeterRegistry meterRegistry) {
        this(translator, quota, records.getIfAvailable(() -> TranslationRecordStore.NONE), Clock.systemUTC(),
                meterRegistry);
    }

    TranslationService(DialectTranslator translator, TranslationQuota quota, TranslationRecordStore records,
                       Clock clock, MeterRegistry meterRegistry) {
        this.translator = translator;
        this.quota = quota;
        this.records = records;
        this.clock = clock;
        for (TranslationResult result : TranslationResult.values()) {
            requests.put(result, Counter.builder(ServiceMetrics.TRANSLATION_REQUESTS)
                    .description("번역 요청 - 태그 result는 success | rejected | too_long | limited | llm_failed (KAN-266)")
                    .tag("result", result.metricTag())
                    .register(meterRegistry));
        }
    }

    /**
     * @throws ApiException 400 {@code VALIDATION_FAILED}, 422 {@code TRANSLATION_TOO_LONG}, 422 {@code TRANSLATION_REJECTED},
     *                      429 {@code TRANSLATION_DAILY_LIMIT_EXCEEDED}, 503 {@code TRANSLATION_UNAVAILABLE}
     */
    TranslationResponse translate(AppUser user, @Nullable TranslationRequest request) {
        long started = System.nanoTime();
        Instant requestedAt = Instant.now(clock);
        String text = request != null ? request.text() : null;
        if (text == null || text.isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "text가 비어 있습니다.");
        }
        Attempt attempt = new Attempt(UUID.randomUUID(), requestedAt, started, user.id(), text);
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
            throw new ApiException(ErrorCode.TRANSLATION_DAILY_LIMIT_EXCEEDED);
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

    /** 한도 칸을 잡은 뒤의 LLM 호출과 응답 검사 - 여기서 성공으로 끝나지 않으면 부르는 쪽이 칸을 돌려준다. */
    private TranslationResponse translateWithin(Attempt attempt, String input, TranslationQuota.Slot slot) {
        long llmStarted = System.nanoTime();
        DialectTranslator.Reply reply;
        try {
            reply = translator.translate(input);
        } catch (DialectTranslator.Unavailable e) {
            finish(attempt, TranslationResult.LLM_FAILED, null, elapsedMs(llmStarted));
            throw new ApiException(ErrorCode.TRANSLATION_UNAVAILABLE);
        }
        long llmMs = elapsedMs(llmStarted);

        String dialect = reply.dialect();
        if (reply.kind() != DialectTranslator.Kind.TRANSLATED || dialect == null
                || !TranslationHarness.acceptsOutput(input, dialect)) {
            log.info("번역 불가 판정 kind={}", reply.kind());
            finish(attempt, TranslationResult.REJECTED, null, llmMs);
            throw new ApiException(ErrorCode.TRANSLATION_REJECTED);
        }
        String output = dialect.strip();
        finish(attempt, TranslationResult.SUCCESS, output, llmMs);
        return new TranslationResponse(output, quota.remaining(slot));
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
        requests.get(result).increment();
        log.info("번역 requestId={} result={} inputLength={} totalMs={} llmMs={}", attempt.requestId(), result,
                inputLength, totalMs, llmMs);
        records.save(new TranslationRecord(attempt.requestId(), attempt.requestedAt(), attempt.userId(),
                input, inputLength, output, result, totalMs, llmMs, translator.model()));
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

    private record Attempt(UUID requestId, Instant requestedAt, long started, UUID userId, String text) {
    }
}
