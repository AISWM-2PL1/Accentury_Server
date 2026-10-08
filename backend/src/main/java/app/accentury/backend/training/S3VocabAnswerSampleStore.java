package app.accentury.backend.training;

import app.accentury.backend.observability.ServiceMetrics;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
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
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/**
 * 단어 답안 정오를 S3에 메타 JSON 하나로 남긴다 (KAN-276). 키는 {@link VocabAnswerSample#key} -
 * 음성 학습 샘플의 라벨 전용 건과 같은 {@code _no-audio} 트리다.
 * <p>
 * <b>비동기다.</b> 음성 샘플은 분석 워커가 동기로 쓰지만 단어 답안은 사용자 요청 스레드에서 저장되므로, S3 왕복을
 * 응답 경로에 두지 않으려고 전용 실행기({@code executor})에 넘긴다. 실행기의 큐가 차거나 종료 중이면 그 건은 버리고
 * 지표로만 드러낸다 - 답안과 채점은 DB에 이미 확정됐고, 기록은 난이도 집계용 부산물이다.
 * <p>
 * <b>실패는 삼킨다</b> - WARN 로그 1줄과 카운터({@link ServiceMetrics#TRAINING_SAMPLES}, 태그 {@code result}의
 * {@code vocab_saved}와 {@code failed})로만 드러낸다. 답안 내용(고른 선택지와 정오)은 로그에 남기지 않는다
 * ({@code VocabAnswerService}와 같은 이유 - 결과 유추 차단).
 */
public class S3VocabAnswerSampleStore implements VocabAnswerSampleStore {

    private static final Logger log = LoggerFactory.getLogger(S3VocabAnswerSampleStore.class);

    private final S3Client s3;
    private final String bucket;
    private final String envPrefix;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final Executor executor;
    private final Counter saved;
    private final Counter failed;

    public S3VocabAnswerSampleStore(S3Client s3, String bucket, String envPrefix, ObjectMapper objectMapper,
                                    Clock clock, Executor executor, MeterRegistry meterRegistry) {
        this.s3 = s3;
        this.bucket = bucket;
        this.envPrefix = envPrefix;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.executor = executor;
        this.saved = counter(meterRegistry, "vocab_saved");
        this.failed = counter(meterRegistry, "failed");
    }

    private static Counter counter(MeterRegistry registry, String result) {
        // 이름과 태그가 같으면 S3TrainingSampleStore가 등록한 카운터를 돌려받는다 - failed는 둘이 함께 센다.
        return Counter.builder(ServiceMetrics.TRAINING_SAMPLES)
                .description("학습 샘플 저장 시도 - 태그 result는 saved | label_saved | vocab_saved | failed | skipped "
                        + "(KAN-201, KAN-269, KAN-274, KAN-276)")
                .tag("result", result)
                .register(registry);
    }

    @Override
    public void save(VocabAnswerSample sample) {
        try {
            executor.execute(() -> put(sample));
        } catch (RejectedExecutionException e) {
            failed.increment();
            log.warn("단어 정오 기록을 버린다 - 기록 실행기가 가득 찼거나 종료 중 answerId={}", sample.answerId());
        }
    }

    private void put(VocabAnswerSample sample) {
        String key = sample.key(envPrefix);
        try {
            s3.putObject(PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(key)
                            .contentType(S3TrainingSampleStore.JSON_CONTENT_TYPE)
                            .build(),
                    RequestBody.fromString(objectMapper.writeValueAsString(metadata(sample))));
            saved.increment();
            log.info("단어 정오 기록 저장 answerId={} key={}", sample.answerId(), key);
        } catch (RuntimeException e) {
            failed.increment();
            log.warn("단어 정오 기록 저장 실패 - 답안에는 영향 없음 answerId={} key={} 사유={}",
                    sample.answerId(), key, e.toString());
        }
    }

    /** 메타 JSON 본문 - 필드 순서는 티켓 4-2절 표와 같다. 키와 같은 값도 본문에 둬 객체를 옮겨도 자립한다. */
    Map<String, Object> metadata(VocabAnswerSample sample) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("answerId", sample.answerId());
        body.put("sessionId", sample.sessionId());
        body.put("itemId", sample.itemId());
        body.put("region", sample.region());
        body.put("testVersion", sample.testVersion());
        body.put("scoreVersion", sample.scoreVersion());
        body.put("itemType", VocabAnswerSample.ITEM_TYPE);
        body.put("choiceId", sample.choiceId());
        body.put("correctChoiceId", sample.correctChoiceId());
        body.put("correct", sample.correct());
        body.put("answeredAt", sample.answeredAt().toString());
        body.put("audioStored", false);
        body.put("savedAt", Instant.now(clock).toString());
        return body;
    }
}
