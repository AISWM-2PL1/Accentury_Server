package app.accentury.backend.auth;

import app.accentury.backend.common.ApiException;
import app.accentury.backend.common.ErrorCode;
import app.accentury.backend.session.Region;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Access 토큰 → 살아 있는 계정 (KAN-223, 명세서 §2.1). 컨트롤러 인자({@link AuthenticatedUser})와 세션 생성의
 * 계정 귀속(§3.1)이 같은 판정을 쓴다 - 두 곳이 따로 판정하면 한쪽만 탈퇴 계정을 통과시키는 식으로 갈라진다.
 */
@Component
public class AccountTokens {

    private final AccessTokens accessTokens;
    private final AppUserRepository users;

    AccountTokens(AccessTokens accessTokens, AppUserRepository users) {
        this.accessTokens = accessTokens;
        this.users = users;
    }

    /**
     * {@code Authorization} 헤더 전체를 받아 계정을 돌려준다.
     *
     * @throws ApiException 401 {@code AUTH_TOKEN_INVALID} - 헤더 없음, Bearer 아님, 토큰 무효, 계정 없음
     */
    AppUser authenticate(@Nullable String authorizationHeader) {
        if (authorizationHeader == null || authorizationHeader.length() <= 7
                || !authorizationHeader.regionMatches(true, 0, "Bearer ", 0, 7)) {
            throw new ApiException(ErrorCode.AUTH_TOKEN_INVALID);
        }
        return byToken(authorizationHeader.substring(7).strip());
    }

    private AppUser byToken(String accessToken) {
        UUID userId = accessTokens.verify(accessToken);
        return users.findActive(userId).orElseThrow(() -> new ApiException(ErrorCode.AUTH_TOKEN_INVALID));
    }

    /**
     * 세션 생성에 쓸 계정 (§3.1) - 토큰이 유효하고 프로필이 완료된 계정만.
     *
     * @param accessToken {@code Bearer } 뒤의 토큰 원문 (세션 토큰 {@code st_}가 아닌 것으로 이미 갈랐다)
     * @throws ApiException 401 {@code AUTH_TOKEN_INVALID} / 403 {@code AUTH_PROFILE_INCOMPLETE},
     *                      {@code AUTH_GUEST_NOT_ALLOWED}
     */
    public SessionAccount forSession(String accessToken) {
        AppUser user = byToken(accessToken);
        if (user.isGuest()) {
            // 게스트의 레벨테스트는 토큰 없이 익명 세션으로 본다 (KAN-329, §3.21). 프로필 미완료(아래)로 답하면 앱이
            // 추가 정보 화면으로 가는데, 게스트에게는 그 화면이 없다.
            throw new ApiException(ErrorCode.AUTH_GUEST_NOT_ALLOWED);
        }
        Region region = user.region();
        if (!user.isProfileComplete() || region == null) {
            throw new ApiException(ErrorCode.AUTH_PROFILE_INCOMPLETE);
        }
        return new SessionAccount(user.id(), region);
    }

    /**
     * 세션에 싣는 계정 정보 - id와 출신지역뿐이다. 세션은 계정의 다른 값을 알 필요가 없다.
     *
     * @param userId 계정 id ({@code test_session.user_id})
     * @param region 계정의 출신지역 - 계정 세션의 {@code test_session.region}이 된다
     */
    public record SessionAccount(UUID userId, Region region) {
    }
}
