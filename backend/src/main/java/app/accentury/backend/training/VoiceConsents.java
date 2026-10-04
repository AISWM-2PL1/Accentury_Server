package app.accentury.backend.training;

import app.accentury.backend.auth.AppUser;
import app.accentury.backend.auth.AppUserRepository;
import app.accentury.backend.session.TestSession;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 한 세션의 음성 저장 동의를 판정한다 (KAN-269) - 업로드마다 한 번 부른다.
 * <p>
 * 계정 세션은 세션에 남은 값이 아니라 <b>지금</b> 계정의 동의를 본다 - 세션 도중에 철회하면 다음 문항부터
 * 저장되지 않는다. 익명 세션은 세션 생성 때 기록한 동의를 본다(세션 안에서는 철회할 길이 없다).
 * <p>
 * 확인할 수 없으면 동의가 없는 것으로 본다 (안전한 기본값) - 계정 조회가 실패해도 업로드와 분석은 그대로
 * 진행되고 음성만 저장되지 않는다.
 */
@Component
public class VoiceConsents {

    private static final Logger log = LoggerFactory.getLogger(VoiceConsents.class);

    private final AppUserRepository users;

    VoiceConsents(AppUserRepository users) {
        this.users = users;
    }

    /** 이 세션에 유효한 음성 저장 동의 - 없으면 null이다. */
    public @Nullable VoiceConsent forSession(TestSession session) {
        UUID userId = session.userId();
        if (userId == null) {
            String version = session.voiceConsentVersion();
            return version != null && session.voiceConsentAt() != null
                    ? new VoiceConsent(version, session.voiceConsentAt(), null)
                    : null;
        }
        try {
            AppUser user = users.findActive(userId).orElse(null);
            if (user == null || !user.hasVoiceConsent()) {
                return null;
            }
            return new VoiceConsent(user.voiceConsentVersion(), user.voiceConsentAt(), user.id());
        } catch (RuntimeException e) {
            // 계정 id는 남기지 않는다 - 세션 id와 한 줄에 있으면 로그가 대응표가 된다 (KAN-240).
            log.warn("음성 저장 동의를 확인하지 못해 저장하지 않는다 sessionId={} 사유={}",
                    session.id(), e.getClass().getSimpleName());
            return null;
        }
    }

    /**
     * 업로드 때 받은 동의가 저장 직전에도 유효한가 - 업로드와 저장 사이(큐 대기와 분석 시간)에 계정이 동의를
     * 철회하거나 탈퇴했으면 false다. 익명 세션의 동의는 세션 안에서 철회할 길이 없어 언제나 true다.
     * 확인할 수 없으면 false다 (안전한 기본값).
     */
    public boolean stillInEffect(VoiceConsent consent) {
        UUID ownerId = consent.ownerId();
        if (ownerId == null) {
            return true;
        }
        try {
            return users.findActive(ownerId).map(AppUser::hasVoiceConsent).orElse(false);
        } catch (RuntimeException e) {
            log.warn("저장 직전에 음성 저장 동의를 확인하지 못해 저장하지 않는다 사유={}", e.getClass().getSimpleName());
            return false;
        }
    }
}
