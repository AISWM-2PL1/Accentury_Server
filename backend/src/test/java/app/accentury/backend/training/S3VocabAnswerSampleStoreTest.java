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
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** S3 왕복 없이 단어 정오 기록의 객체 규약(키, 본문)과 실패 삼킴, 기록 대상 버전을 본다 (KAN-276). */
class S3VocabAnswerSampleStoreTest {

    private static final Instant SAVED_AT = Instant.parse("2026-10-08T06:00:00Z");
    private static final Instant ANSWERED_AT = Instant.parse("2026-10-08T05:59:58Z");

    /** 넘긴 작업을 그 자리에서 돌린다 - 비동기를 걷어내고 본문만 본다. */
    private static final Executor INLINE = Runnable::run;

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void 정오_기록은_음성_라벨과_같은_no_audio_트리에_JSON_하나로_놓인다() throws IOException {
        RecordingS3 s3 = new RecordingS3();
        store(s3, INLINE).save(sample(true));

        assertEquals(1, s3.puts.size());
        Put put = s3.puts.get(0);
        assertEquals("voice-bucket", put.request().bucket());
        assertEquals("prod/_no-audio/GYEONGNAM/gn-2026.10.2/s_1/w12/va_9.json", put.request().key());
        assertEquals("application/json", put.request().contentType());
        assertEquals(1.0, registry.get("accentury.training.samples").tag("result", "vocab_saved").counter().count());
    }

    @Test
    void 본문은_정오와_정답과_종류를_싣고_계정이나_동의는_없다() throws IOException {
        RecordingS3 s3 = new RecordingS3();
        store(s3, INLINE).save(sample(false));

        JsonNode meta = objectMapper.readTree(s3.puts.get(0).body());
        assertEquals(List.of("answerId", "sessionId", "itemId", "region", "testVersion", "scoreVersion", "itemType",
                "choiceId", "correctChoiceId", "correct", "answeredAt", "audioStored", "savedAt"),
                new ArrayList<>(meta.propertyNames()));
        assertEquals("VOCABULARY", meta.get("itemType").asString());
        assertEquals("w12b", meta.get("choiceId").asString());
        assertEquals("w12c", meta.get("correctChoiceId").asString());
        assertFalse(meta.get("correct").asBoolean());
        assertFalse(meta.get("audioStored").asBoolean());
        assertEquals("2026-10-08T05:59:58Z", meta.get("answeredAt").asString());
        assertEquals("2026-10-08T06:00:00Z", meta.get("savedAt").asString());
    }

    @Test
    void S3_실패는_삼키고_지표로만_남긴다() {
        S3VocabAnswerSampleStore store = store(new FailingS3(), INLINE);

        assertDoesNotThrow(() -> store.save(sample(true)));
        assertEquals(1.0, registry.get("accentury.training.samples").tag("result", "failed").counter().count());
    }

    @Test
    void 실행기가_거절하면_기록을_버리고_지표로만_남긴다() {
        RecordingS3 s3 = new RecordingS3();
        S3VocabAnswerSampleStore store = store(s3, task -> {
            throw new RejectedExecutionException("가득 참");
        });

        assertDoesNotThrow(() -> store.save(sample(true)));
        assertTrue(s3.puts.isEmpty());
        assertEquals(1.0, registry.get("accentury.training.samples").tag("result", "failed").counter().count());
    }

    @Test
    void 기록은_gn_2026_10_2와_그_뒤_버전만이다() {
        assertFalse(VocabAnswerSampleStore.recorded("gn-2026.10.1"));
        assertFalse(VocabAnswerSampleStore.recorded("gn-2026.09.4"));
        assertTrue(VocabAnswerSampleStore.recorded("gn-2026.10.2"));
        // 순번이 두 자리여도 숫자로 비교한다 - 문자열 비교면 10.10이 10.2보다 앞이다.
        assertTrue(VocabAnswerSampleStore.recorded("gn-2026.10.10"));
        assertTrue(VocabAnswerSampleStore.recorded("gn-2027.01.1"));
        assertFalse(VocabAnswerSampleStore.recorded("gn-2026.09.t7"));
        assertFalse(VocabAnswerSampleStore.recorded("gn-2026.08.1"));
    }

    private S3VocabAnswerSampleStore store(S3Client s3, Executor executor) {
        return new S3VocabAnswerSampleStore(s3, "voice-bucket", "prod", objectMapper,
                Clock.fixed(SAVED_AT, ZoneOffset.UTC), executor, registry);
    }

    private static VocabAnswerSample sample(boolean correct) {
        return new VocabAnswerSample("va_9", "s_1", "w12", "GYEONGNAM", "gn-2026.10.2", "sv-0.5",
                correct ? "w12c" : "w12b", "w12c", correct, ANSWERED_AT);
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
            return SERVICE_NAME;
        }

        @Override
        public void close() {
        }
    }

    private static class FailingS3 implements S3Client {
        @Override
        public PutObjectResponse putObject(PutObjectRequest request, RequestBody body) {
            throw S3Exception.builder().message("AccessDenied").statusCode(403).build();
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
