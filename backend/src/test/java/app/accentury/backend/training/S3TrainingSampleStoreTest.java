package app.accentury.backend.training;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** S3 왕복 없이 객체 규약(키, Content-Type, 메타 필드), 대응표 기록 순서, 실패 삼킴을 본다 (KAN-201, KAN-269). */
class S3TrainingSampleStoreTest {

    private static final Instant SAVED_AT = Instant.parse("2026-09-11T06:00:00Z");
    private static final Instant CONSENTED_AT = Instant.parse("2026-09-11T05:00:00Z");
    private static final UUID OWNER = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final VoiceConsent ANONYMOUS = new VoiceConsent("2026-10-04", CONSENTED_AT, null);

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RecordingOwners owners = new RecordingOwners();
    private final ToggleConsents consents = new ToggleConsents();

    @Test
    void WAV와_JSON이_같은_키_접두에_나란히_놓인다() throws IOException {
        RecordingS3 s3 = new RecordingS3();
        store(s3).save(completed());

        assertEquals(2, s3.puts.size());
        Put wav = s3.puts.get(0);
        Put json = s3.puts.get(1);
        assertEquals("training-bucket", wav.request().bucket());
        assertEquals("staging/GYEONGNAM/gn-2026.09.2/s_1/v3/a_9.wav", wav.request().key());
        assertEquals("audio/wav", wav.request().contentType());
        assertArrayEquals(new byte[] {82, 73, 70, 70}, wav.body(), "업로드 받은 바이트 그대로다");
        assertEquals("staging/GYEONGNAM/gn-2026.09.2/s_1/v3/a_9.json", json.request().key());
        assertEquals("application/json", json.request().contentType());
        assertEquals(1.0, registry.get("accentury.training.samples").tag("result", "saved").counter().count());
    }

    @Test
    void 성공_샘플의_메타는_원점수와_AI_버전을_싣고_오류_코드는_없다() throws IOException {
        RecordingS3 s3 = new RecordingS3();
        store(s3).save(completed());

        JsonNode meta = objectMapper.readTree(s3.puts.get(1).body());
        assertEquals("a_9", meta.get("analysisJobId").asString());
        assertEquals("s_1", meta.get("sessionId").asString());
        assertEquals("v3", meta.get("itemId").asString());
        assertEquals("GYEONGNAM", meta.get("region").asString());
        assertEquals("1|3", meta.get("scriptKey").asString());
        assertEquals("gn-2026.09.2", meta.get("testVersion").asString());
        assertEquals("sv-0.4", meta.get("scoreVersion").asString());
        assertEquals(2450, meta.get("durationMs").asLong());
        assertEquals("COMPLETED", meta.get("outcome").asString());
        assertEquals(78, meta.get("intonationScore").asInt());
        assertEquals("OK", meta.get("qualityCode").asString());
        assertEquals("rmvpe-0.2", meta.get("modelVersion").asString());
        assertEquals("sv-ai-0.1", meta.get("aiScoreVersion").asString());
        assertFalse(meta.has("errorCode"));
        assertEquals("c_abc", meta.get("correlationId").asString());
        assertEquals("2026-09-11T06:00:00Z", meta.get("savedAt").asString());
        // 동의 증빙 (KAN-269) - 익명 세션의 동의 기록은 세션 행과 함께 사라지므로 음성 옆에 남긴다.
        assertEquals("2026-10-04", meta.get("voiceConsentVersion").asString());
        assertEquals("2026-09-11T05:00:00Z", meta.get("voiceConsentAt").asString());
        assertFalse(meta.has("ownerId"), "소유 계정은 라벨에 싣지 않는다 - 대응표에만 있다");
        // 이 JSON 옆에 WAV가 있다는 표시 (KAN-274) - 라벨 전용 건과 파일만 보고도 갈린다.
        assertTrue(meta.get("audioStored").asBoolean());
    }

    @Test
    void 라벨_전용_샘플은_no_audio_접두에_JSON_하나만_놓인다() throws IOException {
        // 음성 저장에 동의하지 않은 익명 세션의 건이다 (KAN-274). 음성 트리와 접두를 갈라 「음성 트리의 칸은 언제나
        // WAV와 JSON 한 쌍」이라는 규약을 지킨다.
        RecordingS3 s3 = new RecordingS3();
        store(s3).save(labelOnly());

        assertEquals(1, s3.puts.size(), "라벨 전용 건에 WAV가 올라갔다");
        Put json = s3.puts.get(0);
        assertEquals("training-bucket", json.request().bucket());
        assertEquals("staging/_no-audio/GYEONGNAM/gn-2026.09.2/s_1/v3/a_9.json", json.request().key());
        assertEquals("application/json", json.request().contentType());
        assertEquals(1.0, registry.get("accentury.training.samples").tag("result", "label_saved").counter().count());
        assertEquals(0.0, registry.get("accentury.training.samples").tag("result", "saved").counter().count());
    }

    @Test
    void 라벨_전용_샘플의_메타는_점수와_지역을_싣고_동의_기록은_없다() throws IOException {
        RecordingS3 s3 = new RecordingS3();
        store(s3).save(labelOnly());

        JsonNode meta = objectMapper.readTree(s3.puts.get(0).body());
        assertFalse(meta.get("audioStored").asBoolean());
        assertEquals("a_9", meta.get("analysisJobId").asString());
        assertEquals("s_1", meta.get("sessionId").asString());
        assertEquals("v3", meta.get("itemId").asString());
        assertEquals("GYEONGNAM", meta.get("region").asString());
        assertEquals("COMPLETED", meta.get("outcome").asString());
        assertEquals(78, meta.get("intonationScore").asInt());
        assertEquals("rmvpe-0.2", meta.get("modelVersion").asString());
        assertEquals("2026-09-11T06:00:00Z", meta.get("savedAt").asString());
        assertFalse(meta.has("voiceConsentVersion"), "동의하지 않은 세션에 동의 버전이 실렸다");
        assertFalse(meta.has("voiceConsentAt"), "동의하지 않은 세션에 동의 시각이 실렸다");
    }

    @Test
    void 라벨_전용_샘플은_동의_재확인과_대응표를_거치지_않는다() {
        // 계정이 없는 세션이라 볼 동의도 이을 계정도 없다. 재확인이 false여도 남는다.
        consents.inEffect = false;
        RecordingS3 s3 = new RecordingS3();

        store(s3).save(labelOnly());

        assertEquals(1, s3.puts.size());
        assertTrue(owners.records.isEmpty());
        assertEquals(0.0, registry.get("accentury.training.samples").tag("result", "skipped").counter().count());
    }

    @Test
    void 라벨_전용_저장의_S3_실패도_삼키고_카운터만_올린다() {
        S3Client failing = new RecordingS3() {
            @Override
            public PutObjectResponse putObject(PutObjectRequest request, RequestBody body) {
                throw S3Exception.builder().message("AccessDenied").statusCode(403).build();
            }
        };

        assertDoesNotThrow(() -> store(failing).save(labelOnly()));

        assertEquals(1.0, registry.get("accentury.training.samples").tag("result", "failed").counter().count());
        assertEquals(0.0, registry.get("accentury.training.samples").tag("result", "label_saved").counter().count());
    }

    @Test
    void 음성과_동의는_함께_있거나_함께_없어야_한다() {
        // 동의 없는 음성이 저장소까지 가는 조합을 샘플을 만드는 자리에서 막는다 (KAN-274).
        assertThrows(IllegalArgumentException.class, () -> new TrainingSample("a_9", "s_1", "v3", "GYEONGNAM",
                "1|3", "gn-2026.09.2", "sv-0.4", 2450, TrainingSample.Outcome.COMPLETED, 78, "OK", "rmvpe-0.2",
                "sv-ai-0.1", null, "c_abc", null, new byte[] {82, 73, 70, 70}));
        assertThrows(IllegalArgumentException.class, () -> new TrainingSample("a_9", "s_1", "v3", "GYEONGNAM",
                "1|3", "gn-2026.09.2", "sv-0.4", 2450, TrainingSample.Outcome.COMPLETED, 78, "OK", "rmvpe-0.2",
                "sv-ai-0.1", null, "c_abc", ANONYMOUS, null));
    }

    @Test
    void 익명_세션의_샘플은_대응표를_남기지_않는다() {
        RecordingS3 s3 = new RecordingS3();
        store(s3).save(completed());

        assertTrue(owners.records.isEmpty());
    }

    @Test
    void 계정_세션의_샘플은_음성을_올리기_전에_대응표부터_남긴다() {
        RecordingS3 s3 = new RecordingS3() {
            @Override
            public PutObjectResponse putObject(PutObjectRequest request, RequestBody body) {
                assertEquals(List.of("s_1=" + OWNER), owners.records, "대응표보다 음성이 먼저 올라갔다");
                return super.putObject(request, body);
            }
        };
        store(s3).save(completed(new VoiceConsent("2026-10-04", CONSENTED_AT, OWNER)));

        assertEquals(2, s3.puts.size());
        assertFalse(s3.puts.get(0).request().key().contains(OWNER.toString()), "객체 키에 계정 id가 들어갔다");
        assertFalse(new String(s3.puts.get(1).body(), java.nio.charset.StandardCharsets.UTF_8)
                .contains(OWNER.toString()), "라벨에 계정 id가 들어갔다");
    }

    @Test
    void 저장_직전에_동의가_철회돼_있으면_대응표도_음성도_남기지_않는다() {
        // 동의는 업로드 때 판정하고 저장은 분석 뒤다 (KAN-269) - 그 사이의 철회와 탈퇴를 저장 직전에 거른다.
        consents.inEffect = false;
        RecordingS3 s3 = new RecordingS3();

        store(s3).save(completed(new VoiceConsent("2026-10-04", CONSENTED_AT, OWNER)));

        assertTrue(s3.puts.isEmpty());
        assertTrue(owners.records.isEmpty());
        assertEquals(1.0, registry.get("accentury.training.samples").tag("result", "skipped").counter().count());
        assertEquals(0.0, registry.get("accentury.training.samples").tag("result", "failed").counter().count());
    }

    @Test
    void 대응표_기록이_실패하면_음성을_올리지_않는다() {
        // 누구 것인지 찾을 수 없는 음성을 남기지 않는다 - 음성 없는 대응표 행은 해가 없지만 그 반대는 아니다.
        owners.failing = true;
        RecordingS3 s3 = new RecordingS3();

        assertDoesNotThrow(() -> store(s3).save(completed(new VoiceConsent("2026-10-04", CONSENTED_AT, OWNER))));

        assertTrue(s3.puts.isEmpty());
        assertEquals(1.0, registry.get("accentury.training.samples").tag("result", "failed").counter().count());
    }

    @Test
    void 판정_실패_샘플의_메타는_오류_코드만_있고_점수_자리는_없다() throws IOException {
        RecordingS3 s3 = new RecordingS3();
        store(s3).save(new TrainingSample("a_9", "s_1", "v3", "UNKNOWN", null, "gn-2026.09.2", "sv-0.4",
                2450, TrainingSample.Outcome.RETRYABLE_FAILED, null, null, null, null, "AUDIO_TOO_QUIET",
                "c_abc", ANONYMOUS, new byte[] {82, 73, 70, 70}));

        JsonNode meta = objectMapper.readTree(s3.puts.get(1).body());
        assertEquals("RETRYABLE_FAILED", meta.get("outcome").asString());
        assertEquals("AUDIO_TOO_QUIET", meta.get("errorCode").asString());
        assertFalse(meta.has("intonationScore"));
        assertFalse(meta.has("qualityCode"));
        assertFalse(meta.has("modelVersion"));
        assertFalse(meta.has("aiScoreVersion"));
        assertFalse(meta.has("scriptKey"), "더미 정의의 문항은 대본 키가 없다");
        assertTrue(s3.puts.get(0).request().key().startsWith("staging/UNKNOWN/"));
    }

    @Test
    void S3_실패는_삼키고_카운터만_올린다() {
        S3Client failing = new RecordingS3() {
            @Override
            public PutObjectResponse putObject(PutObjectRequest request, RequestBody body) {
                throw S3Exception.builder().message("AccessDenied").statusCode(403).build();
            }
        };

        assertDoesNotThrow(() -> store(failing).save(completed()));

        assertEquals(1.0, registry.get("accentury.training.samples").tag("result", "failed").counter().count());
        assertEquals(0.0, registry.get("accentury.training.samples").tag("result", "saved").counter().count());
    }

    private S3TrainingSampleStore store(S3Client s3) {
        return new S3TrainingSampleStore(s3, "training-bucket", "staging", owners, consents, objectMapper,
                Clock.fixed(SAVED_AT, ZoneOffset.UTC), registry);
    }

    private static TrainingSample completed() {
        return completed(ANONYMOUS);
    }

    /** 음성 저장에 동의하지 않은 익명 세션의 건 - 음성과 동의가 없다 (KAN-274). */
    private static TrainingSample labelOnly() {
        return new TrainingSample("a_9", "s_1", "v3", "GYEONGNAM", "1|3", "gn-2026.09.2", "sv-0.4",
                2450, TrainingSample.Outcome.COMPLETED, 78, "OK", "rmvpe-0.2", "sv-ai-0.1", null,
                "c_abc", null, null);
    }

    private static TrainingSample completed(VoiceConsent consent) {
        return new TrainingSample("a_9", "s_1", "v3", "GYEONGNAM", "1|3", "gn-2026.09.2", "sv-0.4",
                2450, TrainingSample.Outcome.COMPLETED, 78, "OK", "rmvpe-0.2", "sv-ai-0.1", null,
                "c_abc", consent, new byte[] {82, 73, 70, 70});
    }

    /** 저장 직전의 동의 재확인을 흉내 낸다 - 실제 판정은 {@code VoiceConsentApiTest}와 {@code VoiceConsentsTest}가 본다. */
    private static final class ToggleConsents extends VoiceConsents {
        boolean inEffect = true;

        ToggleConsents() {
            super(null);
        }

        @Override
        public boolean stillInEffect(VoiceConsent consent) {
            return inEffect;
        }
    }

    /** DB 없이 대응표 기록을 받아 적는다 - 실제 SQL은 {@code VoiceConsentApiTest}가 본다. */
    private static final class RecordingOwners extends TrainingVoiceOwners {
        final List<String> records = new ArrayList<>();
        boolean failing;

        RecordingOwners() {
            super(null);
        }

        @Override
        public void record(String sessionId, UUID userId, Instant now) {
            if (failing) {
                throw new IllegalStateException("DB 불가");
            }
            records.add(sessionId + "=" + userId);
        }
    }

    private record Put(PutObjectRequest request, byte[] body) {
    }

    /** putObject 호출을 기록하는 가짜 클라이언트 - 본문은 스트림을 끝까지 읽어 둔다. */
    private static class RecordingS3 implements S3Client {
        final List<Put> puts = new ArrayList<>();

        @Override
        public PutObjectResponse putObject(PutObjectRequest request, RequestBody body) {
            try (InputStream in = body.contentStreamProvider().newStream()) {
                puts.add(new Put(request, in.readAllBytes()));
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
            return PutObjectResponse.builder().build();
        }

        @Override
        public String serviceName() {
            return SERVICE_NAME;
        }

        @Override
        public void close() {
        }
    }
}
