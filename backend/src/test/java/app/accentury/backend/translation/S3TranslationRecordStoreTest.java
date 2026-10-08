package app.accentury.backend.translation;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 번역 기록 저장소 (KAN-266, 명세서 §3.18) - 객체 키와 본문, 실패를 삼키는 계약.
 */
class S3TranslationRecordStoreTest {

    /** 2026-10-08 23:30 UTC - KST로는 10-09 08:30이라 날짜 경계가 KST인지 드러난다. */
    private static final Instant REQUESTED_AT = Instant.parse("2026-10-08T23:30:00Z");
    private static final UUID REQUEST_ID = UUID.fromString("6f1c9a3e-2b4d-4e8a-9c7f-1a2b3c4d5e6f");
    private static final UUID USER_ID = UUID.fromString("0f8c2a4e-6d1b-4c3a-9e57-2b1d8f6a4c90");
    private static final UUID SUBJECT_ID = UUID.fromString("a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d");

    private final ObjectMapper objectMapper = JsonMapper.builder().build();
    private final S3Client s3 = mock(S3Client.class);
    private final TranslationSubjects subjects = mock(TranslationSubjects.class);
    private final SimpleMeterRegistry metrics = new SimpleMeterRegistry();

    private S3TranslationRecordStore store(java.util.concurrent.Executor executor) {
        return new S3TranslationRecordStore(s3, "accentury-translator-prompt-325771561913", subjects, objectMapper,
                Clock.fixed(REQUESTED_AT, ZoneOffset.UTC), executor, metrics);
    }

    private static TranslationRecord record(TranslationResult result, String output, Long llmMs) {
        return new TranslationRecord(REQUEST_ID, REQUESTED_AT, USER_ID, "밥 먹었어?", output, result, 812, llmMs,
                "gemini-3.5-flash-lite");
    }

    private double count(String result) {
        return metrics.counter("accentury.translation.records", "result", result).count();
    }

    @Test
    void 요청_한_건이_KST_날짜_키의_JSON_객체_하나가_되고_계정_ID는_없다() throws Exception {
        when(subjects.resolve(any(), any())).thenReturn(SUBJECT_ID);

        store(Runnable::run).save(record(TranslationResult.SUCCESS, "밥 뭇나?", 640L));

        ArgumentCaptor<PutObjectRequest> request = ArgumentCaptor.forClass(PutObjectRequest.class);
        ArgumentCaptor<RequestBody> body = ArgumentCaptor.forClass(RequestBody.class);
        verify(s3).putObject(request.capture(), body.capture());
        assertEquals("accentury-translator-prompt-325771561913", request.getValue().bucket());
        assertEquals("translations/2026/10/09/" + REQUEST_ID + ".json", request.getValue().key());

        String json;
        try (InputStream in = body.getValue().contentStreamProvider().newStream()) {
            json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        JsonNode object = objectMapper.readTree(json);
        assertEquals(List.of("requestId", "requestedAt", "subjectId", "input", "output", "result", "totalMs", "llmMs",
                "model"), List.copyOf(object.propertyNames()));
        assertEquals(REQUEST_ID.toString(), object.get("requestId").asString());
        assertEquals("2026-10-09T08:30+09:00", object.get("requestedAt").asString());
        assertEquals(SUBJECT_ID.toString(), object.get("subjectId").asString());
        assertEquals("밥 먹었어?", object.get("input").asString());
        assertEquals("밥 뭇나?", object.get("output").asString());
        assertEquals("SUCCESS", object.get("result").asString());
        assertEquals(812, object.get("totalMs").asLong());
        assertEquals(640, object.get("llmMs").asLong());
        assertEquals("gemini-3.5-flash-lite", object.get("model").asString());
        assertFalse(json.contains(USER_ID.toString()), "객체에 계정 ID가 없다");
        assertEquals(1, count("saved"));
    }

    @Test
    void 번역_불가와_호출_전_거절은_output과_llmMs가_null이다() throws Exception {
        when(subjects.resolve(any(), any())).thenReturn(null);

        store(Runnable::run).save(record(TranslationResult.TOO_LONG, null, null));

        ArgumentCaptor<RequestBody> body = ArgumentCaptor.forClass(RequestBody.class);
        verify(s3).putObject(any(PutObjectRequest.class), body.capture());
        JsonNode object;
        try (InputStream in = body.getValue().contentStreamProvider().newStream()) {
            object = objectMapper.readTree(in.readAllBytes());
        }
        assertTrue(object.get("output").isNull());
        assertTrue(object.get("llmMs").isNull());
        assertTrue(object.get("subjectId").isNull(), "기록 전에 탈퇴한 계정은 대체 ID가 없다");
        assertEquals("TOO_LONG", object.get("result").asString());
    }

    @Test
    void S3가_실패해도_예외를_내지_않고_지표만_남긴다() {
        when(subjects.resolve(any(), any())).thenReturn(SUBJECT_ID);
        when(s3.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(S3Exception.builder().message("Access Denied").statusCode(403).build());

        store(Runnable::run).save(record(TranslationResult.SUCCESS, "밥 뭇나?", 640L));

        assertEquals(1, count("failed"));
        assertEquals(0, count("saved"));
    }

    @Test
    void 대체_ID_조회가_실패해도_예외를_내지_않는다() {
        when(subjects.resolve(any(), any())).thenThrow(new IllegalStateException("db down"));

        store(Runnable::run).save(record(TranslationResult.SUCCESS, "밥 뭇나?", 640L));

        assertEquals(1, count("failed"));
    }

    @Test
    void 실행기가_가득_차면_버리고_지표만_남긴다() {
        store(task -> {
            throw new RejectedExecutionException("full");
        }).save(record(TranslationResult.SUCCESS, "밥 뭇나?", 640L));

        assertEquals(1, count("failed"));
    }
}
