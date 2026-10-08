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
 * LLM 호출(실패면 503 {@code TRANSLATION_UNAVAILABLE}) → 응답 검사(어기면 422 {@code TRANSLATION_REJECTED}) → 200.
 * 400을 뺀 모든 끝에서 결과 종류를 지표로 세고 번역 기록을 넘긴다.
 * <p>
 * <b>입력과 출력 텍스트는 로그에 남기지 않는다</b> (§2.6) - 로그는 결과 종류, 입력 길이, 소요 시간뿐이다.
 * 사용량 제한은 두지 않는다 (2026-10-04 결정). 사업자 무료 한도를 넘으면 503이다.
 */
@Service
class TranslationService {

    private static final Logger log = LoggerFactory.getLogger(TranslationService.class);

    private final DialectTranslator translator;
    private final TranslationRecordStore records;
    private final Clock clock;
    private final Map<TranslationResult, Counter> requests = new EnumMap<>(TranslationResult.class);

    @Autowired
    TranslationService(DialectTranslator translator, ObjectProvider<TranslationRecordStore> records,
                       MeterRegistry meterRegistry) {
        this(translator, records.getIfAvailable(() -> TranslationRecordStore.NONE), Clock.systemUTC(), meterRegistry);
    }

    TranslationService(DialectTranslator translator, TranslationRecordStore records, Clock clock,
                       MeterRegistry meterRegistry) {
        this.translator = translator;
        this.records = records;
        this.clock = clock;
        for (TranslationResult result : TranslationResult.values()) {
            requests.put(result, Counter.builder(ServiceMetrics.TRANSLATION_REQUESTS)
                    .description("번역 요청 - 태그 result는 success | rejected | too_long | llm_failed (KAN-266)")
                    .tag("result", result.metricTag())
                    .register(meterRegistry));
        }
    }

    /**
     * @throws ApiException 400 {@code VALIDATION_FAILED}, 422 {@code TRANSLATION_TOO_LONG}, 422 {@code TRANSLATION_REJECTED},
     *                      503 {@code TRANSLATION_UNAVAILABLE}
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
        return new TranslationResponse(output);
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
