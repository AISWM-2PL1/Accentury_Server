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
 * 억양 학습 녹음을 음성 버킷의 학습 트리에 WAV 1개 + 메타 JSON 1개로 남긴다 (KAN-267, 명세서 §3.19).
 * <p>
 * 규칙은 레벨테스트 음성({@link S3TrainingSampleStore})과 같다 - 저장 직전에 동의를 다시 보고, 대응표
 * ({@link TrainingVoiceOwners})를 먼저 남긴 뒤 WAV, JSON 순으로 올리며, 오디오는 복사하지 않고 실패는 삼킨다. 다른 것은
 * 키({@link LearningVoiceSample#keyPrefix})와 메타 필드뿐이다. 대응표의 {@code session_id} 열에는 학습 시도 id를 넣는다
 * (2026-10-10 결정 - 표 하나로 "이 음성 묶음은 이 계정"을 찾게 한다).
 * <p>
 * 지표는 레벨테스트와 같은 이름({@link ServiceMetrics#TRAINING_SAMPLES})이고 태그 {@code result} 값에 {@code learning_}을
 * 붙여 가른다 - 태그 키 집합을 레벨테스트 계측과 같게 둬야 한 이름의 지표가 레지스트리에서 충돌하지 않는다.
 */
public class S3LearningVoiceStore implements LearningVoiceStore {

    private static final Logger log = LoggerFactory.getLogger(S3LearningVoiceStore.class);

    private final S3Client s3;
    private final String bucket;
    private final String envPrefix;
    private final TrainingVoiceOwners owners;
    private final VoiceConsents consents;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final Counter saved;
    private final Counter failed;
    private final Counter skipped;

    public S3LearningVoiceStore(S3Client s3, String bucket, String envPrefix, TrainingVoiceOwners owners,
                                VoiceConsents consents, ObjectMapper objectMapper, Clock clock,
                                MeterRegistry meterRegistry) {
        this.s3 = s3;
        this.bucket = bucket;
        this.envPrefix = envPrefix;
        this.owners = owners;
        this.consents = consents;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.saved = counter(meterRegistry, "learning_saved");
        this.failed = counter(meterRegistry, "learning_failed");
        this.skipped = counter(meterRegistry, "learning_skipped");
    }

    private static Counter counter(MeterRegistry registry, String result) {
        return Counter.builder(ServiceMetrics.TRAINING_SAMPLES)
                .description("학습 샘플 저장 시도 - 태그 result는 saved | label_saved | failed | skipped "
                        + "(KAN-201, KAN-269, KAN-274), 억양 학습 녹음은 learning_ 접두 (KAN-267)")
                .tag("result", result)
                .register(registry);
    }

    @Override
    public void save(LearningVoiceSample sample) {
        String prefix = sample.keyPrefix(envPrefix);
        VoiceConsent consent = sample.consent();
        // 동의는 업로드 때 판정했지만 저장은 분석이 끝난 뒤다 - 그 사이에 철회하거나 탈퇴했으면 남기지 않는다.
        if (!consents.stillInEffect(consent)) {
            skipped.increment();
            return;
        }
        if (consent.ownerId() != null) {
            try {
                owners.record(sample.attemptId(), consent.ownerId(), Instant.now(clock));
            } catch (RuntimeException e) {
                failed.increment();
                // 예외 메시지는 남기지 않는다 - DB 드라이버의 메시지에 계정 id가 실리면 시도 id와 한 줄에 놓인다 (KAN-240).
                log.warn("대응표 기록 실패로 학습 녹음을 저장하지 않는다 - 채점 결과에는 영향 없음 attemptId={} 사유={}",
                        sample.attemptId(), e.getClass().getSimpleName());
                return;
            }
        }
        byte[] audio = sample.audio();
        try {
            s3.putObject(PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(prefix + ".wav")
                            .contentType(S3TrainingSampleStore.WAV_CONTENT_TYPE)
                            .build(),
                    RequestBody.fromContentProvider(() -> new ByteArrayInputStream(audio), audio.length,
                            S3TrainingSampleStore.WAV_CONTENT_TYPE));
            s3.putObject(PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(prefix + ".json")
                            .contentType(S3TrainingSampleStore.JSON_CONTENT_TYPE)
                            .build(),
                    RequestBody.fromString(objectMapper.writeValueAsString(metadata(sample))));
            saved.increment();
            log.info("학습 녹음 저장 attemptId={} key={} bytes={}", sample.attemptId(), prefix, audio.length);
        } catch (RuntimeException e) {
            failed.increment();
            log.warn("학습 녹음 저장 실패 - 채점 결과에는 영향 없음 attemptId={} key={} 사유={}",
                    sample.attemptId(), prefix, e.toString());
        }
    }

    /**
     * 메타 JSON 본문 (§3.19) - 레벨테스트 메타(§3.3)의 세션, 문항 자리를 시도와 카드가 대신한다. 없는 값은 키를 내지
     * 않는다. 소유 계정 id는 싣지 않는다 (대응표에만 있다).
     */
    Map<String, Object> metadata(LearningVoiceSample sample) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("attemptId", sample.attemptId());
        body.put("cardId", sample.cardId());
        body.put("region", sample.region());
        body.put("scriptKey", sample.scriptKey());
        body.put("contentVersion", sample.contentVersion());
        body.put("scoreVersion", sample.scoreVersion());
        body.put("itemType", LearningVoiceSample.ITEM_TYPE);
        body.put("durationMs", sample.durationMs());
        body.put("outcome", sample.outcome().name());
        putIfPresent(body, "intonationScore", sample.intonationScore());
        putIfPresent(body, "qualityCode", sample.qualityCode());
        putIfPresent(body, "modelVersion", sample.modelVersion());
        putIfPresent(body, "errorCode", sample.errorCode());
        body.put("correlationId", sample.correlationId());
        body.put("audioStored", true);
        body.put("voiceConsentVersion", sample.consent().version());
        body.put("voiceConsentAt", sample.consent().consentedAt().toString());
        body.put("savedAt", Instant.now(clock).toString());
        return body;
    }

    private static void putIfPresent(Map<String, Object> body, String key, @Nullable Object value) {
        if (value != null) {
            body.put(key, value);
        }
    }
}
