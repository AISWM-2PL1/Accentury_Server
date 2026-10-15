package app.accentury.backend.translation;

import app.accentury.backend.auth.AppUser;
import app.accentury.backend.common.ApiException;
import app.accentury.backend.common.ErrorCode;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 번역과 일일 한도 칸의 짝 (KAN-266, 명세서 §3.18) - 성공만 칸을 쓰고, 그 밖의 끝은 잡은 칸을 돌려주며, 카운터 저장소
 * 장애는 LLM 없이 503이다. Redis 장애는 API 테스트로 만들기 어렵다 - Redis가 죽으면 로그인부터 503이라 Access 토큰을 받을
 * 수 없다.
 */
class TranslationServiceQuotaTest {

    private final UUID userId = UUID.randomUUID();
    private final FakeQuota quota = new FakeQuota();
    private final List<String> llmInputs = new CopyOnWriteArrayList<>();
    private final List<TranslationRecord> records = new CopyOnWriteArrayList<>();
    private Function<String, DialectTranslator.Reply> llm = DialectTranslator.Reply::translated;

    private final TranslationService service = new TranslationService(new DialectTranslator() {
        @Override
        public Reply translate(String text) {
            llmInputs.add(text);
            return llm.apply(text);
        }

        @Override
        public String model() {
            return "fake-model";
        }
    }, quota, records::add, Clock.systemUTC(), new SimpleMeterRegistry());

    @Test
    void 성공하면_칸을_돌려주지_않고_남은_수를_준다() {
        quota.used = 2;
        TranslationResponse response = translate("밥 먹었어?");
        assertEquals(2, response.remaining(), "이 번역이 세 번째다");
        assertEquals(1, quota.acquired);
        assertTrue(quota.released.isEmpty());
    }

    @Test
    void LLM_실패와_번역_불가는_잡은_칸을_돌려준다() {
        llm = text -> {
            throw new DialectTranslator.Unavailable("timeout");
        };
        assertCode(ErrorCode.TRANSLATION_UNAVAILABLE, () -> translate("밥 먹었어?"));
        llm = text -> DialectTranslator.Reply.of(DialectTranslator.Kind.REFUSED);
        assertCode(ErrorCode.TRANSLATION_REJECTED, () -> translate("심한 욕설이 들어간 문장"));

        assertEquals(2, quota.released.size());
    }

    @Test
    void 예상하지_못한_예외도_잡은_칸을_돌려준다() {
        llm = text -> {
            throw new IllegalStateException("버그");
        };
        assertThrows(IllegalStateException.class, () -> translate("밥 먹었어?"));
        assertEquals(1, quota.released.size());
    }

    @Test
    void 호출_전_거절은_칸을_잡지도_않는다() {
        assertCode(ErrorCode.TRANSLATION_TOO_LONG, () -> translate("가".repeat(101)));
        assertCode(ErrorCode.TRANSLATION_REJECTED, () -> translate("ㅓㅗㅁ니ㅏㅓㅗㅁㅇ니랑노라ㅣㅓ"));
        assertEquals(0, quota.acquired);
    }

    @Test
    void 카운터_저장소_장애는_LLM_없이_503이고_LLM_FAILED로_기록한다() {
        quota.down = true;
        assertCode(ErrorCode.TRANSLATION_UNAVAILABLE, () -> translate("밥 먹었어?"));

        assertTrue(llmInputs.isEmpty());
        assertEquals(1, records.size());
        assertEquals(TranslationResult.LLM_FAILED, records.get(0).result());
        assertNull(records.get(0).llmMs());
        assertTrue(quota.released.isEmpty(), "잡은 칸이 없으니 돌려줄 것도 없다");
    }

    @Test
    void 소진이면_LLM_없이_429이고_LIMITED로_기록한다() {
        quota.used = 5;
        assertCode(ErrorCode.TRANSLATION_DAILY_LIMIT_EXCEEDED, () -> translate("밥 먹었어?"));

        assertTrue(llmInputs.isEmpty());
        assertEquals(TranslationResult.LIMITED, records.get(0).result());
    }

    private TranslationResponse translate(String text) {
        AppUser user = mock(AppUser.class);
        when(user.id()).thenReturn(userId);
        return service.translate(user, new TranslationRequest(text));
    }

    private static void assertCode(ErrorCode expected, Runnable call) {
        ApiException e = assertThrows(ApiException.class, call::run);
        assertEquals(expected, e.code());
    }

    /** 메모리 카운터 - 잡고 돌려준 것을 적어 둔다. */
    private static final class FakeQuota implements TranslationQuota {

        int used;
        int acquired;
        boolean down;
        final List<Slot> released = new ArrayList<>();

        @Override
        public Slot acquire(UUID userId) {
            if (down) {
                throw new StoreUnavailable("down");
            }
            if (used >= 5) {
                throw new Exhausted();
            }
            acquired++;
            return new Slot("trq:test", ++used);
        }

        @Override
        public void release(Slot slot) {
            released.add(slot);
            used--;
        }

        @Override
        public int remaining(Slot slot) {
            return Slot.remainingAfter(used);
        }
    }
}
