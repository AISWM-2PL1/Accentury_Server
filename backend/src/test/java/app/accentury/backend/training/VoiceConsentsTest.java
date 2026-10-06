package app.accentury.backend.training;

import app.accentury.backend.analytics.Traffic;
import app.accentury.backend.auth.AppUserRepository;
import app.accentury.backend.session.TestSession;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 동의를 확인할 수 없으면 저장하지 않는다 (KAN-269, 안전한 기본값) - DB가 죽은 경우를 본다. 정상 경로는
 * {@code VoiceConsentApiTest}가 실제 DB로 본다.
 */
class VoiceConsentsTest {

    private static final Instant NOW = Instant.parse("2026-10-04T00:00:00Z");
    private static final UUID OWNER = UUID.fromString("11111111-2222-3333-4444-555555555555");

    private final AppUserRepository users = mock(AppUserRepository.class);
    private final VoiceConsents consents = new VoiceConsents(users);

    @Test
    void 계정_조회가_실패하면_업로드_때_동의가_없는_것으로_본다() {
        when(users.findActive(any())).thenThrow(new DataAccessResourceFailureException("DB 불가"));

        assertNull(consents.forSession(session(OWNER)));
    }

    @Test
    void 계정_조회가_실패하면_저장_직전에도_유효하지_않은_것으로_본다() {
        when(users.findActive(any())).thenThrow(new DataAccessResourceFailureException("DB 불가"));

        assertFalse(consents.stillInEffect(new VoiceConsent("2026-10-04", NOW, OWNER)));
    }

    @Test
    void 익명_세션의_동의는_계정을_조회하지_않고_유효하다() {
        assertTrue(consents.stillInEffect(new VoiceConsent("2026-10-04", NOW, null)));
        verifyNoInteractions(users);
    }

    private static TestSession session(UUID userId) {
        return new TestSession("s_1", "hash", "gn-2026.10.1", "sv-0.5", 1, null, null, null, null,
                Traffic.REAL, userId, NOW, NOW.plusSeconds(1800));
    }
}
