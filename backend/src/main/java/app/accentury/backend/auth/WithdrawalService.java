package app.accentury.backend.auth;

import app.accentury.backend.common.ApiException;
import app.accentury.backend.common.ErrorCode;
import app.accentury.backend.learning.LearningRecords;
import app.accentury.backend.session.TestSessionRepository;
import app.accentury.backend.translation.TranslationReports;
import app.accentury.backend.translation.TranslationSubjects;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.UUID;

/**
 * 회원 탈퇴 (FR-AC-09, KAN-241, 명세서 §3.14).
 * <p>
 * 순서: 계정 파기(한 트랜잭션) → Refresh 전부 폐기 → 로그인 제공자 연결 해제. 앞이 필수이고 뒤 둘은 최선이다.
 * <ol>
 *   <li><b>계정 파기</b> - 계정 행을 잠그고 PII 열을 null로 덮은 뒤 {@code deleted_at}을 찍고({@link AppUser#withdraw}),
 *       그 계정 세션의 {@code user_id}를 끊고, 단어 학습 기록(시도, 답안, 오답)을 지운다 (KAN-265, §3.16).
 *       번역 기록의 대체 ID 대응표 행도 지운다 (KAN-266, §3.18) - S3의 번역 기록은 남고 누구의 것인지 알 수 없게 된다.
 *       번역 신고는 계정 id만 비운다 (KAN-343, §3.22) - 신고 내용은 보존 기간까지 남는다. 커밋되는 순간 같은 Access는 {@code findActive}가 거절하고(블랙리스트
 *       없이, INFO-2), 같은 Refresh도 회전 뒤 계정 확인에서 거절된다 ({@code AuthService.refresh}).</li>
 *   <li><b>Refresh 폐기</b> - 모든 기기의 패밀리를 Redis에서 지운다. 위 계정 확인이 이미 막으므로 여기가 실패해도(Redis
 *       장애) 탈퇴는 성공으로 답하고 WARN만 남긴다 - 남은 키는 30일 TTL로 사라진다. 커밋 뒤에 503을 내면 앱이 재시도하고,
 *       그 재시도는 401이 되어 사용자가 탈퇴가 됐는지 알 수 없다.</li>
 *   <li><b>제공자 연결 해제</b> - 애플은 토큰 revoke({@link AppleTokenRevoker}), 카카오와 네이버는 연결 해제
 *       ({@link IdpUnlinker}, KAN-343)다. 실패해도 WARN이다. 구글과 게스트는 대상이 아니다.</li>
 * </ol>
 * 로그에는 계정 id와 provider만 남긴다.
 */
@Service
public class WithdrawalService {

    private static final Logger log = LoggerFactory.getLogger(WithdrawalService.class);

    private final AppUserRepository users;
    private final TestSessionRepository sessions;
    private final LearningRecords learningRecords;
    private final TranslationSubjects translationSubjects;
    private final RefreshTokens refreshTokens;
    private final AppleTokenRevoker appleTokenRevoker;
    private final IdpUnlinker idpUnlinker;
    private final TranslationReports translationReports;
    private final TransactionTemplate transactionTemplate;

    WithdrawalService(AppUserRepository users, TestSessionRepository sessions, LearningRecords learningRecords,
                      TranslationSubjects translationSubjects,
                      RefreshTokens refreshTokens, AppleTokenRevoker appleTokenRevoker, IdpUnlinker idpUnlinker,
                      TranslationReports translationReports, TransactionTemplate transactionTemplate) {
        this.users = users;
        this.sessions = sessions;
        this.learningRecords = learningRecords;
        this.translationSubjects = translationSubjects;
        this.refreshTokens = refreshTokens;
        this.appleTokenRevoker = appleTokenRevoker;
        this.idpUnlinker = idpUnlinker;
        this.translationReports = translationReports;
        this.transactionTemplate = transactionTemplate;
    }

    private record Withdrawn(Provider provider, String subject, int detachedSessions, int purgedLearningRecords,
                             int purgedTranslationSubjects) {
    }

    /** 계정 파기의 본체 - 잠근 계정을 받아 한 트랜잭션 안에서 부른다. 탈퇴와 게스트 정리가 같은 절차를 쓴다. */
    private Withdrawn destroy(AppUser locked) {
        // IdP 사용자 id는 치환하기 전에 잡아 둔다 - 애플 revoke가 교환 결과와 맞춰 본다.
        String subject = locked.providerUserId();
        locked.withdraw(Instant.now());
        int detached = sessions.detachUser(locked.id());
        // 학습 기록은 세션과 달리 귀속만 끊지 않고 지운다 - 계정 없이는 의미가 없는 개인 기록이다 (§5.5).
        int purged = learningRecords.purge(locked.id());
        // 번역 기록은 S3에 남기고 계정 연결만 끊는다 (2026-10-08 결정, §3.18).
        int unlinked = translationSubjects.purge(locked.id());
        // 번역 신고는 내용을 보존 기간까지 남기고 계정 연결만 끊는다 (2026-11-10 결정, §3.22).
        translationReports.unlink(locked.id());
        return new Withdrawn(locked.provider(), subject, detached, purged, unlinked);
    }

    /**
     * 돌아올 수 없게 된 게스트 계정을 탈퇴와 같은 절차로 파기한다 (KAN-329, §3.21). {@link GuestAccountRetention}이
     * 살아 있는 로그인이 없는 것을 확인하고 부른다 - 그래서 Refresh 폐기 단계가 없다.
     * <p>
     * 게스트인지는 잠근 뒤에 다시 본다. 여기는 요청이 아니라 잡이 부르는 자리라, 호출 쪽의 실수 하나가 소셜 계정을
     * 지우는 길이 되지 않게 한다.
     *
     * @return 파기했는가 - 그사이 이미 사라졌거나(다른 태스크의 같은 잡, 탈퇴) 게스트가 아니면 false
     */
    boolean purgeGuest(UUID userId) {
        Withdrawn withdrawn = transactionTemplate.execute(tx -> users.lockActive(userId)
                .filter(AppUser::isGuest)
                .map(this::destroy)
                .orElse(null));
        if (withdrawn == null) {
            return false;
        }
        log.info("게스트 계정 파기 userId={} purgedTranslationSubjects={}", userId,
                withdrawn.purgedTranslationSubjects());
        return true;
    }

    /**
     * @throws ApiException 401 {@code AUTH_TOKEN_INVALID} - 그사이 이미 탈퇴했다 (동시에 두 번 누른 경우의 뒤의 것)
     */
    void withdraw(AppUser user, @Nullable WithdrawalRequest request) {
        Withdrawn withdrawn = transactionTemplate.execute(tx -> {
            // 인자로 받은 계정은 인증 단계에서 읽은 것이라 트랜잭션 밖이다 - 잠금과 함께 다시 읽는다. 프로필 저장이나 재로그인의
            // 빈 열 채우기와 겹쳐도 그쪽이 먼저 커밋하면 이쪽이 그 값을 덮고, 이쪽이 먼저면 그쪽은 lockActive에서 빈다.
            AppUser locked = users.lockActive(user.id())
                    .orElseThrow(() -> new ApiException(ErrorCode.AUTH_TOKEN_INVALID));
            return destroy(locked);
        });
        if (withdrawn == null) {
            throw new IllegalStateException("탈퇴 트랜잭션이 결과 없이 끝났다");
        }
        log.info("탈퇴 userId={} provider={} detachedSessions={} purgedLearningRecords={} purgedTranslationSubjects={}",
                user.id(), withdrawn.provider(), withdrawn.detachedSessions(), withdrawn.purgedLearningRecords(),
                withdrawn.purgedTranslationSubjects());

        try {
            long revoked = refreshTokens.revokeAll(user.id());
            log.info("탈퇴 Refresh 폐기 userId={} families={}", user.id(), revoked);
        } catch (ApiException e) {
            log.warn("탈퇴 Refresh 폐기 실패 - 계정 확인이 막으므로 탈퇴는 성공으로 둔다 userId={} code={}",
                    user.id(), e.code());
        } catch (RuntimeException e) {
            // 예상 밖 오류도 같다 - 커밋 뒤의 단계가 500을 내면 앱은 탈퇴 결과를 알 수 없다 (PR #9 리뷰).
            log.warn("탈퇴 Refresh 폐기 실패 - 예상 밖 오류 userId={} ({})", user.id(), e.getClass().getSimpleName());
        }

        // 제공자 쪽 연결을 끊는다 - 셋 다 실패해도 던지지 않는다. 구글은 서버가 끊을 수단이 없다 (IdpUnlinker).
        switch (withdrawn.provider()) {
            case APPLE -> appleTokenRevoker.revoke(request != null ? request.appleAuthorizationCode() : null,
                    withdrawn.subject(), user.id());
            case KAKAO -> idpUnlinker.unlinkKakao(withdrawn.subject(), user.id());
            case NAVER -> idpUnlinker.unlinkNaver(request != null ? request.naverRefreshToken() : null,
                    withdrawn.subject(), user.id());
            case GOOGLE, GUEST -> {
            }
        }
    }
}
