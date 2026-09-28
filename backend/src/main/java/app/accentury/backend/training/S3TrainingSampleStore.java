package app.accentury.backend.training;

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

import java.io.ByteArrayInputStream;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 학습 샘플을 S3 버킷에 WAV 1개 + 메타 JSON 1개로 보존한다 (KAN-201 객체 규약).
 * <p>
 * <b>동의한 테스터 계정의 세션만 남긴다</b> (KAN-239, {@link TrainingSpeakers}). 익명 세션과 목록 밖 계정의
 * 세션은 S3를 부르지 않고 카운터({@code result=skipped})만 올린다 - 로그는 남기지 않는다(웹 업로드마다 한 줄이다).
 * <p>
 * 키는 {@code <region>/<testVersion>/<speaker>/<itemId>/<sampleId>.wav|.json}이다. speaker는 세션 ID의, sampleId는
 * 분석 작업 ID의 가명(HMAC)이라 같은 세션의 문항은 한 접두에 모이고 DB와 로그의 ID와는 이어지지 않는다. 로그에도
 * 작업 ID와 객체 키를 한 줄에 함께 남기지 않는다 - 그 한 줄이 가명과 원문의 대응표가 된다. 첫 조각이
 * 지역이라 지역별 데이터셋을 접두 나열 한 번으로 뽑고, 재녹음은 같은 문항에 새 분석 작업을 만들므로
 * 작업 ID가 키에 있어 덮어쓰지 않는다. WAV를 먼저 올리고 JSON을 나중에 올린다 - JSON이 있는데 WAV가
 * 없는 반쪽 샘플보다 WAV만 있고 JSON이 없는 쪽이 학습 데이터로 골라내기 쉽다(메타 없는 WAV는 버린다).
 * <p>
 * <b>실패는 삼킨다.</b> 상태 전이는 이미 끝난 뒤라 사용자 응답에는 영향이 없고, 여기서 예외가 새면
 * 호출부의 오류 경로가 종결을 한 번 더 시도한다. WARN 로그 1줄과 카운터
 * ({@link ServiceMetrics#TRAINING_SAMPLES}, 태그 {@code result})로만 드러낸다.
 * <p>
 * 오디오는 복사하지 않는다 - {@link RequestBody#fromBytes}는 배열을 복사해 원본 음성이 힙에 하나 더
 * 생기고 그 사본은 {@code wipeAudio()}가 못 지운다. 길이를 아는 스트림 공급자로 넘겨 원본 배열을 그대로
 * 읽게 한다. 이 호출이 돌아온 뒤 호출부가 원본을 0으로 덮는다.
 * <p>
 * 동기 호출이다 - 워커 스레드가 S3 왕복(수십 ms)만큼 더 점유되지만 사용자의 폴링 대기와는 무관하다.
 * 비동기로 넘기면 버퍼 소유권이 갈려 파기 시점을 못박을 수 없다 (KAN-27).
 */
public class S3TrainingSampleStore implements TrainingSampleStore {

    private static final Logger log = LoggerFactory.getLogger(S3TrainingSampleStore.class);

    static final String WAV_CONTENT_TYPE = "audio/wav";
    static final String JSON_CONTENT_TYPE = "application/json";

    private final S3Client s3;
    private final String bucket;
    private final TrainingSpeakers speakers;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final Counter saved;
    private final Counter failed;
    private final Counter skipped;

    public S3TrainingSampleStore(S3Client s3, String bucket, TrainingSpeakers speakers, ObjectMapper objectMapper,
                                 Clock clock, MeterRegistry meterRegistry) {
        this.s3 = s3;
        this.bucket = bucket;
        this.speakers = speakers;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.saved = counter(meterRegistry, "saved");
        this.failed = counter(meterRegistry, "failed");
        this.skipped = counter(meterRegistry, "skipped");
    }

    private static Counter counter(MeterRegistry registry, String result) {
        return Counter.builder(ServiceMetrics.TRAINING_SAMPLES)
                .description("staging 학습 샘플 저장 시도 - 태그 result는 saved | failed | skipped (KAN-201, KAN-239)")
                .tag("result", result)
                .register(registry);
    }

    @Override
    public void save(TrainingSample sample) {
        if (!speakers.consented(sample.ownerId())) {
            skipped.increment();
            return;
        }
        String speaker = speakers.speaker(sample.sessionId());
        String sampleId = speakers.sampleId(sample.analysisJobId());
        String prefix = sample.keyPrefix(speaker, sampleId);
        try {
            byte[] audio = sample.audio();
            s3.putObject(PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(prefix + ".wav")
                            .contentType(WAV_CONTENT_TYPE)
                            .build(),
                    RequestBody.fromContentProvider(() -> new ByteArrayInputStream(audio), audio.length,
                            WAV_CONTENT_TYPE));
            s3.putObject(PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(prefix + ".json")
                            .contentType(JSON_CONTENT_TYPE)
                            .build(),
                    RequestBody.fromString(objectMapper.writeValueAsString(metadata(sample, speaker, sampleId))));
            saved.increment();
            log.info("학습 샘플 저장 jobId={} bytes={}", sample.analysisJobId(), audio.length);
        } catch (RuntimeException e) {
            failed.increment();
            // 사유는 한 줄로 충분하다 - 권한, 네트워크, 직렬화 어느 쪽이든 메시지에 드러난다.
            log.warn("학습 샘플 저장 실패 - 분석 결과에는 영향 없음 jobId={} 사유={}", sample.analysisJobId(), e.toString());
        }
    }

    /**
     * 메타 JSON 본문 - 필드 순서는 티켓 표와 같고, 없는 값(판정 실패의 점수, 성공의 오류 코드)은 키를
     * 아예 내지 않는다. 키와 같은 값(sampleId, speaker, 문항 ID와 지역)을 본문에도 둔다 - 객체를 옮겨 담아
     * 키를 잃어도 자립한다. 세션 ID와 작업 ID의 원문, 소유 계정, AI 호출 상관 ID는 싣지 않는다 (KAN-239).
     */
    Map<String, Object> metadata(TrainingSample sample, String speaker, String sampleId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("sampleId", sampleId);
        body.put("speaker", speaker);
        body.put("itemId", sample.itemId());
        body.put("region", sample.region());
        putIfPresent(body, "scriptKey", sample.scriptKey());
        body.put("testVersion", sample.testVersion());
        body.put("scoreVersion", sample.scoreVersion());
        body.put("durationMs", sample.durationMs());
        body.put("outcome", sample.outcome().name());
        putIfPresent(body, "intonationScore", sample.intonationScore());
        putIfPresent(body, "qualityCode", sample.qualityCode());
        putIfPresent(body, "modelVersion", sample.modelVersion());
        putIfPresent(body, "aiScoreVersion", sample.aiScoreVersion());
        putIfPresent(body, "errorCode", sample.errorCode());
        body.put("savedAt", Instant.now(clock).toString());
        return body;
    }

    private static void putIfPresent(Map<String, Object> body, String key, @Nullable Object value) {
        if (value != null) {
            body.put(key, value);
        }
    }
}
