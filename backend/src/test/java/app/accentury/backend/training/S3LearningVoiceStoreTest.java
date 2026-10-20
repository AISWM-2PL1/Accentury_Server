package app.accentury.backend.training;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 억양 학습 녹음의 객체 규약(학습 트리 키, 메타 필드), 대응표, 동의 재확인을 S3 왕복 없이 본다 (KAN-267, §3.19). */
class S3LearningVoiceStoreTest {

    private static final Instant SAVED_AT = Instant.parse("2026-10-10T06:00:00Z");
    private static final Instant CONSENTED_AT = Instant.parse("2026-10-09T05:00:00Z");
    private static final UUID OWNER = UUID.fromString("11111111-2222-3333-4444-555555555555");

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RecordingOwners owners = new RecordingOwners();
    private final ToggleConsents consents = new ToggleConsents();

    @Test
    void 학습_트리에_WAV와_JSON이_나란히_놓이고_대응표에_시도_id가_남는다() {
        RecordingS3 s3 = new RecordingS3();
        store(s3).save(completed());

        assertEquals(List.of("la_7=" + OWNER), owners.records);
        assertEquals(2, s3.puts.size());
        assertEquals("staging/_learning/GYEONGNAM/in-gn-2026.10.1/ic01c01/la_7.wav", s3.puts.get(0).request().key());
        assertEquals("audio/wav", s3.puts.get(0).request().contentType());
        assertArrayEquals(new byte[] {82, 73, 70, 70}, s3.puts.get(0).body(), "업로드 받은 바이트 그대로다");
        assertEquals("staging/_learning/GYEONGNAM/in-gn-2026.10.1/ic01c01/la_7.json", s3.puts.get(1).request().key());
        assertEquals(1.0, registry.get("accentury.training.samples").tag("result", "learning_saved").counter().count());
    }

    @Test
    void 메타는_시도와_카드와_원점수를_싣고_계정_id는_없다() throws IOException {
        RecordingS3 s3 = new RecordingS3();
        store(s3).save(completed());

        JsonNode meta = objectMapper.readTree(s3.puts.get(1).body());
        assertEquals("la_7", meta.get("attemptId").asString());
        assertEquals("ic01c01", meta.get("cardId").asString());
        assertEquals("GYEONGNAM", meta.get("region").asString());
        assertEquals("1|10", meta.get("scriptKey").asString());
        assertEquals("in-gn-2026.10.1", meta.get("contentVersion").asString());
        assertEquals("sv-0.5", meta.get("scoreVersion").asString());
        assertEquals("LEARNING_VOICE", meta.get("itemType").asString());
        assertEquals(3000, meta.get("durationMs").asLong());
        assertEquals("COMPLETED", meta.get("outcome").asString());
        assertEquals(95, meta.get("intonationScore").asInt());
        assertEquals("track1-test", meta.get("modelVersion").asString());
        assertFalse(meta.has("errorCode"));
        assertTrue(meta.get("audioStored").asBoolean());
        assertEquals("2026-10-04", meta.get("voiceConsentVersion").asString());
        assertEquals("2026-10-09T05:00:00Z", meta.get("voiceConsentAt").asString());
        assertEquals("2026-10-10T06:00:00Z", meta.get("savedAt").asString());
        assertFalse(meta.has("ownerId"));
        assertFalse(meta.has("sessionId"));
    }

    @Test
    void 저장_직전에_동의가_철회됐으면_아무것도_남기지_않는다() {
        RecordingS3 s3 = new RecordingS3();
        consents.inEffect = false;

        store(s3).save(completed());

        assertTrue(s3.puts.isEmpty());
        assertTrue(owners.records.isEmpty());
        assertEquals(1.0, registry.get("accentury.training.samples").tag("result", "learning_skipped").counter().count());
    }

    @Test
    void 대응표_기록이_실패하면_음성을_올리지_않고_예외도_내지_않는다() {
        RecordingS3 s3 = new RecordingS3();
        owners.failing = true;

        store(s3).save(completed());

        assertTrue(s3.puts.isEmpty());
        assertEquals(1.0, registry.get("accentury.training.samples").tag("result", "learning_failed").counter().count());
    }

    private S3LearningVoiceStore store(S3Client s3) {
        return new S3LearningVoiceStore(s3, "voice-bucket", "staging", owners, consents, objectMapper,
                Clock.fixed(SAVED_AT, ZoneOffset.UTC), registry);
    }

    private static LearningVoiceSample completed() {
        return new LearningVoiceSample("la_7", "ic01c01", "GYEONGNAM", "1|10", "in-gn-2026.10.1", "sv-0.5", 3000,
                TrainingSample.Outcome.COMPLETED, 95, "OK", "track1-test", null, "c_abc",
                new VoiceConsent("2026-10-04", CONSENTED_AT, OWNER), new byte[] {82, 73, 70, 70});
    }

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
            return "s3";
        }

        @Override
        public void close() {
        }
    }
}
