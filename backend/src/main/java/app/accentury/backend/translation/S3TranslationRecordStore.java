package app.accentury.backend.translation;

import app.accentury.backend.observability.ServiceMetrics;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/**
 * 번역 기록을 prod 번역 기록 버킷에 JSON 객체 하나로 남긴다 (KAN-266, 명세서 §3.18). 키는 {@link TranslationRecord#key}다.
 * <p>
 * <b>비동기다.</b> 요청 스레드는 실행기({@code executor})에 넘기기만 하고, 대체 ID 조회(DB)와 PutObject는 실행기 스레드가
 * 한다 - 번역 응답은 기록을 기다리지 않는다. 큐가 차거나 종료 중이면 그 건은 버리고 지표로만 드러낸다.
 * <p>
 * <b>실패는 삼킨다</b> - WARN 로그 1줄과 카운터({@link ServiceMetrics#TRANSLATION_RECORDS}, 태그 {@code result}의
 * {@code failed})로만 드러낸다. 로그에는 요청 ID와 예외 종류만 남긴다 - 입력과 출력 텍스트는 남기지 않는다 (§2.6).
 */
class S3TranslationRecordStore implements TranslationRecordStore {

    private static final Logger log = LoggerFactory.getLogger(S3TranslationRecordStore.class);

    static final String JSON_CONTENT_TYPE = "application/json; charset=utf-8";

    private final S3Client s3;
    private final String bucket;
    private final TranslationSubjects subjects;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final Executor executor;
    private final Counter saved;
    private final Counter failed;

    S3TranslationRecordStore(S3Client s3, String bucket, TranslationSubjects subjects, ObjectMapper objectMapper,
                             Clock clock, Executor executor, MeterRegistry meterRegistry) {
        this.s3 = s3;
        this.bucket = bucket;
        this.subjects = subjects;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.executor = executor;
        this.saved = counter(meterRegistry, "saved");
        this.failed = counter(meterRegistry, "failed");
    }

    private static Counter counter(MeterRegistry registry, String result) {
        return Counter.builder(ServiceMetrics.TRANSLATION_RECORDS)
                .description("번역 기록 저장 시도 - 태그 result는 saved | failed (KAN-266)")
                .tag("result", result)
                .register(registry);
    }

    @Override
    public void save(TranslationRecord record) {
        try {
            executor.execute(() -> put(record));
        } catch (RejectedExecutionException e) {
            failed.increment();
            log.warn("번역 기록을 버린다 - 기록 실행기가 가득 찼거나 종료 중 requestId={}", record.requestId());
        }
    }

    private void put(TranslationRecord record) {
        try {
            UUID subjectId = subjects.resolve(record.userId(), Instant.now(clock));
            s3.putObject(PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(record.key())
                            .contentType(JSON_CONTENT_TYPE)
                            .build(),
                    RequestBody.fromString(objectMapper.writeValueAsString(body(record, subjectId))));
            saved.increment();
            log.info("번역 기록 저장 requestId={} result={}", record.requestId(), record.result());
        } catch (RuntimeException e) {
            // 예외 메시지는 남기지 않는다 - 직렬화 예외는 값 일부를 실을 수 있다. 종류만으로 원인(S3 권한, DB)을 가른다.
            failed.increment();
            log.warn("번역 기록 저장 실패 - 번역 응답에는 영향 없음 requestId={} ({})", record.requestId(),
                    e.getClass().getSimpleName());
        }
    }

    /** 객체 본문 - 필드 순서는 명세서 §3.18 표와 같다. 계정 ID는 싣지 않는다. */
    static Map<String, Object> body(TranslationRecord record, @Nullable UUID subjectId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("requestId", record.requestId().toString());
        body.put("requestedAt", record.requestedAtKst());
        body.put("subjectId", subjectId != null ? subjectId.toString() : null);
        body.put("input", record.input());
        body.put("output", record.output());
        body.put("result", record.result().name());
        body.put("totalMs", record.totalMs());
        body.put("llmMs", record.llmMs());
        body.put("model", record.model());
        return body;
    }
}
