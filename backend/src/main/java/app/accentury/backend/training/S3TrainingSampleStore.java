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
 * 학습 샘플을 S3 버킷에 WAV 1개 + 메타 JSON 1개로 보존한다 (KAN-201 객체 규약, KAN-269).
 * <p>
 * 음성까지 남기는 샘플은 전부 음성 저장에 동의한 세션의 것이다. 동의가 없는 세션의 요청은 호출부
 * ({@code HttpAnalysisDispatcher})가 음성을 뺀 <b>라벨 전용</b> 샘플로 만들어 넘기고 (KAN-274, 계정 세션은 KAN-276부터),
 * 여기서는 메타 JSON 하나만 {@code <env>/_no-audio/<region>/...} 아래에 쓴다. 음성 트리와 접두를 갈라 「음성 트리의
 * 칸은 언제나 WAV와 JSON 한 쌍」이라는 규약을 지킨다 - 같은 트리에 JSON만 있는 칸이 섞이면 학습 쪽이 JSON을 보고
 * 없는 WAV를 찾는다. 단어 답안의 정오도 같은 트리에 JSON 하나로 쓴다 ({@link VocabAnswerSampleStore}, KAN-276).
 * <p>
 * 키는 {@code <env>/<region>/<testVersion>/<sessionId>/<itemId>/<analysisJobId>.wav|.json}이다. 첫 조각은
 * 환경 접두({@code staging}, {@code prod})다 - 두 환경이 음성 전용 버킷 하나를 나눠 쓰고, 태스크 역할은 자기
 * 접두에만 쓸 수 있다. 다음 조각이 지역이라 지역별 데이터셋을 접두 나열 한 번으로 뽑고, 재녹음은 같은 문항에 새 분석 작업을 만들므로
 * 작업 ID가 키에 있어 덮어쓰지 않는다. WAV를 먼저 올리고 JSON을 나중에 올린다 - JSON이 있는데 WAV가
 * 없는 반쪽 샘플보다 WAV만 있고 JSON이 없는 쪽이 학습 데이터로 골라내기 쉽다(메타 없는 WAV는 버린다).
 * <p>
 * 계정 세션이면 WAV를 올리기 전에 계정과 세션의 대응표({@link TrainingVoiceOwners})부터 남긴다. 순서가 반대면
 * 대응표 기록이 실패했을 때 누구 것인지 찾을 수 없는 음성이 버킷에 남는다 - 음성 없는 대응표 행은 해가 없다.
 * 그보다 먼저, 업로드 때 받은 동의가 지금도 유효한지 다시 본다 ({@link VoiceConsents#stillInEffect}) - 저장은
 * 큐 대기와 분석이 끝난 뒤라 그 사이의 철회와 탈퇴를 여기서 걸러야 한다.
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
    private final String envPrefix;
    private final TrainingVoiceOwners owners;
    private final VoiceConsents consents;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final Counter saved;
    private final Counter labelSaved;
    private final Counter failed;
    private final Counter skipped;

    public S3TrainingSampleStore(S3Client s3, String bucket, String envPrefix, TrainingVoiceOwners owners,
                                 VoiceConsents consents, ObjectMapper objectMapper, Clock clock,
                                 MeterRegistry meterRegistry) {
        this.s3 = s3;
        this.bucket = bucket;
        this.envPrefix = envPrefix;
        this.owners = owners;
        this.consents = consents;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.saved = counter(meterRegistry, "saved");
        this.labelSaved = counter(meterRegistry, "label_saved");
        this.failed = counter(meterRegistry, "failed");
        this.skipped = counter(meterRegistry, "skipped");
    }

    private static Counter counter(MeterRegistry registry, String result) {
        return Counter.builder(ServiceMetrics.TRAINING_SAMPLES)
                .description("학습 샘플 저장 시도 - 태그 result는 saved | label_saved | failed | skipped "
                        + "(KAN-201, KAN-269, KAN-274), 억양 학습 녹음은 learning_ 접두 (KAN-267)")
                .tag("result", result)
                .register(registry);
    }

    @Override
    public void save(TrainingSample sample) {
        String prefix = sample.keyPrefix(envPrefix);
        VoiceConsent consent = sample.consent();
        byte[] audio = sample.audio();
        if (consent == null || audio == null) {
            saveLabelOnly(sample, prefix);
            return;
        }
        // 동의는 업로드 때 판정했지만 저장은 분석이 끝난 뒤다 - 그 사이에 계정이 철회하거나 탈퇴했으면 남기지 않는다.
        if (!consents.stillInEffect(consent)) {
            skipped.increment();
            return;
        }
        if (consent.ownerId() != null) {
            try {
                owners.record(sample.sessionId(), consent.ownerId(), Instant.now(clock));
            } catch (RuntimeException e) {
                failed.increment();
                // 예외 메시지는 남기지 않는다 - DB 드라이버의 메시지에 계정 id가 실리면 세션 id와 한 줄에 놓인다 (KAN-240).
                log.warn("대응표 기록 실패로 학습 샘플을 저장하지 않는다 - 분석 결과에는 영향 없음 jobId={} 사유={}",
                        sample.analysisJobId(), e.getClass().getSimpleName());
                return;
            }
        }
        try {
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
                    RequestBody.fromString(objectMapper.writeValueAsString(metadata(sample))));
            saved.increment();
            log.info("학습 샘플 저장 jobId={} key={} bytes={}", sample.analysisJobId(), prefix, audio.length);
        } catch (RuntimeException e) {
            failed.increment();
            // 사유는 한 줄로 충분하다 - 권한, 네트워크, 직렬화 어느 쪽이든 메시지에 드러난다.
            log.warn("학습 샘플 저장 실패 - 분석 결과에는 영향 없음 jobId={} key={} 사유={}",
                    sample.analysisJobId(), prefix, e.toString());
        }
    }

    /**
     * 라벨 전용 건 - 음성 저장에 동의하지 않은 익명 세션의 분석 결과다 (KAN-274). WAV 없이 메타 JSON 하나만
     * {@code _no-audio} 접두 아래에 쓴다. 계정이 없는 세션이라 동의 재확인과 대응표 기록은 없다.
     */
    private void saveLabelOnly(TrainingSample sample, String prefix) {
        try {
            s3.putObject(PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(prefix + ".json")
                            .contentType(JSON_CONTENT_TYPE)
                            .build(),
                    RequestBody.fromString(objectMapper.writeValueAsString(metadata(sample))));
            labelSaved.increment();
            log.info("라벨 전용 샘플 저장 jobId={} key={}", sample.analysisJobId(), prefix);
        } catch (RuntimeException e) {
            failed.increment();
            log.warn("라벨 전용 샘플 저장 실패 - 분석 결과에는 영향 없음 jobId={} key={} 사유={}",
                    sample.analysisJobId(), prefix, e.toString());
        }
    }

    /**
     * 메타 JSON 본문 - 필드 순서는 티켓 표와 같고, 없는 값(판정 실패의 점수, 성공의 오류 코드)은 키를
     * 아예 내지 않는다. 키와 같은 값(작업, 세션, 문항 ID와 지역)을 본문에도 둔다 - 객체를 옮겨 담아
     * 키를 잃어도 자립한다. 동의 버전과 동의 시각도 싣는다 (KAN-269) - 익명 세션의 동의 기록은 세션 행과 함께
     * 만료 삭제되므로 음성 옆의 이 값이 동의 증빙이다. 소유 계정 id는 싣지 않는다 (대응표에만 있다).
     * <p>
     * {@code itemType}은 늘 VOICE다 (KAN-276) - 같은 {@code _no-audio} 트리에 단어 정오(VOCABULARY)가 함께 쌓여
     * 한 테이블에서 가르기 위해서다. 이 키가 없는 옛 JSON은 전부 음성이다.
     * <p>
     * {@code audioStored}는 이 JSON 옆에 WAV가 있는가다 (KAN-274). 라벨 전용 건은 false이고 동의 버전과 동의 시각 키가
     * 없다. 이 키가 없는 옛 JSON(KAN-274 이전)은 전부 음성이 있는 건이다.
     */
    Map<String, Object> metadata(TrainingSample sample) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("analysisJobId", sample.analysisJobId());
        body.put("sessionId", sample.sessionId());
        body.put("itemId", sample.itemId());
        body.put("region", sample.region());
        putIfPresent(body, "scriptKey", sample.scriptKey());
        body.put("testVersion", sample.testVersion());
        body.put("scoreVersion", sample.scoreVersion());
        body.put("itemType", "VOICE");
        body.put("durationMs", sample.durationMs());
        body.put("outcome", sample.outcome().name());
        putIfPresent(body, "intonationScore", sample.intonationScore());
        putIfPresent(body, "qualityCode", sample.qualityCode());
        putIfPresent(body, "modelVersion", sample.modelVersion());
        putIfPresent(body, "aiScoreVersion", sample.aiScoreVersion());
        putIfPresent(body, "errorCode", sample.errorCode());
        body.put("correlationId", sample.correlationId());
        body.put("audioStored", sample.audioStored());
        VoiceConsent consent = sample.consent();
        if (consent != null) {
            body.put("voiceConsentVersion", consent.version());
            body.put("voiceConsentAt", consent.consentedAt().toString());
        }
        body.put("savedAt", Instant.now(clock).toString());
        return body;
    }

    private static void putIfPresent(Map<String, Object> body, String key, @Nullable Object value) {
        if (value != null) {
            body.put(key, value);
        }
    }
}
