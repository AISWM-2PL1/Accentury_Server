package app.accentury.backend.training;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.jspecify.annotations.Nullable;
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
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * S3 왕복 없이 객체 규약(키, Content-Type, 메타 필드)과 실패 삼킴을 본다 (KAN-201), 동의 테스터 한정과
 * 세션 ID 가명화도 본다 (KAN-239).
 */
class S3TrainingSampleStoreTest {

    private static final Instant SAVED_AT = Instant.parse("2026-09-11T06:00:00Z");
    private static final UUID TESTER = UUID.fromString("0f8c2a4e-6d1b-4c3a-9e57-2b1d8f6a4c90");
    private static final String PSEUDONYM_KEY = "k".repeat(32) + "-training-pseudonym-key-for-test";
    /** HMAC-SHA256("s_1", PSEUDONYM_KEY)의 hex - 파이썬 hmac 모듈로 따로 계산한 값이다. */
    private static final String SPEAKER = "ee3ee940d55979008253a32fef21a4e4a5b50a570b433d486ea7177c8ea291e5";
    /** HMAC-SHA256("a_9", PSEUDONYM_KEY)의 hex - 같은 방법으로 계산했다. */
    private static final String SAMPLE_ID = "6ecbba021e89b8ddcb79341ab8fa7c6c29b15eb2bb50f100d3aea3292849e1fb";

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void WAV와_JSON이_같은_키_접두에_나란히_놓인다() throws IOException {
        RecordingS3 s3 = new RecordingS3();
        store(s3).save(completed());

        assertEquals(2, s3.puts.size());
        Put wav = s3.puts.get(0);
        Put json = s3.puts.get(1);
        assertEquals("training-bucket", wav.request().bucket());
        assertEquals("GYEONGNAM/gn-2026.09.2/" + SPEAKER + "/v3/" + SAMPLE_ID + ".wav", wav.request().key());
        assertEquals("audio/wav", wav.request().contentType());
        assertArrayEquals(new byte[] {82, 73, 70, 70}, wav.body(), "업로드 받은 바이트 그대로다");
        assertEquals("GYEONGNAM/gn-2026.09.2/" + SPEAKER + "/v3/" + SAMPLE_ID + ".json", json.request().key());
        assertEquals("application/json", json.request().contentType());
        assertEquals(1.0, registry.get("accentury.training.samples").tag("result", "saved").counter().count());
    }

    @Test
    void 성공_샘플의_메타는_원점수와_AI_버전을_싣고_오류_코드는_없다() throws IOException {
        RecordingS3 s3 = new RecordingS3();
        store(s3).save(completed());

        JsonNode meta = objectMapper.readTree(s3.puts.get(1).body());
        assertEquals(SAMPLE_ID, meta.get("sampleId").asString());
        assertFalse(meta.has("analysisJobId"), "작업 ID 원문은 analysis_job을 거쳐 계정까지 조인된다 (Codex 리뷰 P2)");
        assertEquals(SPEAKER, meta.get("speaker").asString());
        assertFalse(meta.has("sessionId"), "세션 ID 원문은 싣지 않는다 (KAN-239)");
        assertFalse(meta.has("ownerId"), "소유 계정도 싣지 않는다");
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
        assertFalse(meta.has("correlationId"), "AI 호출 상관 ID는 로그와 이어지는 고리라 싣지 않는다");
        assertEquals("2026-09-11T06:00:00Z", meta.get("savedAt").asString());
    }

    @Test
    void 판정_실패_샘플의_메타는_오류_코드만_있고_점수_자리는_없다() throws IOException {
        RecordingS3 s3 = new RecordingS3();
        store(s3).save(new TrainingSample("a_9", "s_1", TESTER, "v3", "UNKNOWN", null, "gn-2026.09.2", "sv-0.4",
                2450, TrainingSample.Outcome.RETRYABLE_FAILED, null, null, null, null, "AUDIO_TOO_QUIET",
                new byte[] {82, 73, 70, 70}));

        JsonNode meta = objectMapper.readTree(s3.puts.get(1).body());
        assertEquals("RETRYABLE_FAILED", meta.get("outcome").asString());
        assertEquals("AUDIO_TOO_QUIET", meta.get("errorCode").asString());
        assertFalse(meta.has("intonationScore"));
        assertFalse(meta.has("qualityCode"));
        assertFalse(meta.has("modelVersion"));
        assertFalse(meta.has("aiScoreVersion"));
        assertFalse(meta.has("scriptKey"), "더미 정의의 문항은 대본 키가 없다");
        assertTrue(s3.puts.get(0).request().key().startsWith("UNKNOWN/"));
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

    // === 수집 대상 한정과 가명화 (KAN-239) ===

    @Test
    void 익명_세션은_저장하지_않고_skipped만_올린다() {
        RecordingS3 s3 = new RecordingS3();
        store(s3).save(completed("s_1", null, "a_9"));

        assertEquals(List.of(), s3.puts);
        assertEquals(1.0, registry.get("accentury.training.samples").tag("result", "skipped").counter().count());
        assertEquals(0.0, registry.get("accentury.training.samples").tag("result", "saved").counter().count());
    }

    @Test
    void 목록_밖_계정의_세션은_저장하지_않는다() {
        RecordingS3 s3 = new RecordingS3();
        store(s3).save(completed("s_1", UUID.randomUUID(), "a_9"));

        assertEquals(List.of(), s3.puts);
        assertEquals(1.0, registry.get("accentury.training.samples").tag("result", "skipped").counter().count());
    }

    @Test
    void 목록이_비면_테스터였던_계정도_저장하지_않는다() {
        RecordingS3 s3 = new RecordingS3();
        store(s3, Set.of()).save(completed());

        assertEquals(List.of(), s3.puts);
    }

    @Test
    void 같은_세션의_문항은_같은_speaker_접두를_공유하고_다른_세션은_갈린다() {
        RecordingS3 s3 = new RecordingS3();
        S3TrainingSampleStore store = store(s3);
        store.save(completed("s_1", TESTER, "a_1"));
        store.save(completed("s_1", TESTER, "a_2"));
        store.save(completed("s_2", TESTER, "a_3"));

        String first = speakerOf(s3.puts.get(0));
        assertEquals(first, speakerOf(s3.puts.get(2)), "같은 세션의 두 번째 문항");
        assertNotEquals(first, speakerOf(s3.puts.get(4)), "다른 세션");
        assertTrue(s3.puts.stream().noneMatch(put -> put.request().key().contains("s_1")
                || put.request().key().contains("s_2")), "키에 세션 ID 원문이 없다");
        assertTrue(s3.puts.stream().noneMatch(put -> put.request().key().contains("a_")), "키에 작업 ID 원문이 없다");
        assertNotEquals(s3.puts.get(0).request().key(), s3.puts.get(2).request().key(),
                "같은 세션 같은 문항의 재녹음(다른 작업)이 덮어쓰지 않는다");
    }

    @Test
    void 키가_다르면_같은_세션도_다른_speaker다() {
        // 키를 모르면 세션 ID로 speaker를 다시 계산할 수 없다 - 사전 대입 방지의 전제다.
        TrainingSpeakers other = new TrainingSpeakers(Set.of(TESTER), "z".repeat(64));
        assertNotEquals(SPEAKER, other.speaker("s_1"));
    }

    private static String speakerOf(Put put) {
        return put.request().key().split("/")[2];
    }

    private S3TrainingSampleStore store(S3Client s3) {
        return store(s3, Set.of(TESTER));
    }

    private S3TrainingSampleStore store(S3Client s3, Set<UUID> testers) {
        return new S3TrainingSampleStore(s3, "training-bucket", new TrainingSpeakers(testers, PSEUDONYM_KEY),
                objectMapper, Clock.fixed(SAVED_AT, ZoneOffset.UTC), registry);
    }

    private static TrainingSample completed() {
        return completed("s_1", TESTER, "a_9");
    }

    private static TrainingSample completed(String sessionId, @Nullable UUID ownerId, String jobId) {
        return new TrainingSample(jobId, sessionId, ownerId, "v3", "GYEONGNAM", "1|3", "gn-2026.09.2", "sv-0.4",
                2450, TrainingSample.Outcome.COMPLETED, 78, "OK", "rmvpe-0.2", "sv-ai-0.1", null,
                new byte[] {82, 73, 70, 70});
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
