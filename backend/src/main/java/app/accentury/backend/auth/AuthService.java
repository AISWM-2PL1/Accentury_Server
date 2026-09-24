package app.accentury.backend.auth;

import app.accentury.backend.common.ApiException;
import app.accentury.backend.common.ErrorCode;
import app.accentury.backend.common.RateLimits;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.util.regex.Pattern;

/**
 * 로그인, refresh, 로그아웃 (KAN-223, 명세서 §3.9, §3.12, §3.13).
 * <p>
 * 로그에는 provider, 사용자 id, 신규 여부, 실패 코드만 남긴다 (§2.6) - 실패 코드는 {@code GlobalExceptionHandler}가
 * 이미 남기므로 여기서는 성공만 적는다.
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    /** IdP 토큰 길이 상한 - 구글과 애플 ID 토큰은 1~2KB, 카카오와 네이버 access token은 100자 안팎이다. */
    static final int TOKEN_MAX = 8192;

    /** 애플 원문 nonce 길이 상한 - 클라이언트가 만드는 난수 문자열이다. */
    static final int NONCE_MAX = 256;

    /** 애플 이름 입력 상한 - 저장 전에 {@link IdpProfile}이 50자로 자르지만, 그보다 훨씬 긴 본문은 거절한다. */
    static final int APPLE_NAME_MAX = 200;

    /** 동의한 방침 버전 형식 - {@code app_user.privacy_policy_version} varchar(32). */
    private static final Pattern POLICY_VERSION = Pattern.compile("[A-Za-z0-9._-]{1,32}");

    private final IdpVerifiers idpVerifiers;
    private final AppUserRepository users;
    private final AccessTokens accessTokens;
    private final RefreshTokens refreshTokens;
    private final RateLimits rateLimits;
    private final TransactionTemplate transactionTemplate;

    AuthService(IdpVerifiers idpVerifiers, AppUserRepository users, AccessTokens accessTokens,
                RefreshTokens refreshTokens, RateLimits rateLimits, TransactionTemplate transactionTemplate) {
        this.idpVerifiers = idpVerifiers;
        this.users = users;
        this.accessTokens = accessTokens;
        this.refreshTokens = refreshTokens;
        this.rateLimits = rateLimits;
        this.transactionTemplate = transactionTemplate;
    }

    /**
     * 소셜 로그인 = 가입 겸용 (§3.9).
     * <p>
     * 순서: 요청 제한 → 입력 검증 → IdP 확인 → 계정 찾기/만들기(한 트랜잭션) → 토큰 발급. 동의 검사는 IdP 확인 뒤다 -
     * 가입인지 재로그인인지는 IdP가 알려 준 사용자 id로 계정을 찾아봐야 알 수 있다.
     *
     * @throws ApiException 400 {@code VALIDATION_FAILED}, {@code AUTH_CONSENT_REQUIRED}, {@code AUTH_UNDER_AGE} / 401 {@code AUTH_IDP_TOKEN_INVALID} /
     *                      429 {@code RATE_LIMITED} / 502 {@code AUTH_IDP_UNAVAILABLE} / 503 {@code AUTH_STORE_UNAVAILABLE}
     */
    LoginResponse login(@Nullable LoginRequest request, String clientIp) {
        rateLimits.check(RateLimits.Scope.AUTH, clientIp);
        if (request == null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "요청 본문이 필요합니다.");
        }
        IdpCredential credential = credential(request);
        IdpProfile profile = idpVerifiers.verify(credential);
        rejectUnderAge(profile);

        Account account = findOrCreate(profile, request);
        String accessToken = accessTokens.issue(account.user().id());
        String refreshToken = refreshTokens.issue(account.user().id());
        log.info("로그인 provider={} userId={} newUser={}", profile.provider(), account.user().id(), account.created());
        return new LoginResponse(accessToken, refreshToken, accessTokens.ttlSeconds(), account.created(),
                ProfileStatus.of(account.user()), UserView.of(account.user()));
    }

    private record Account(AppUser user, boolean created) {
    }

    /**
     * IdP가 준 생년월일이 만 14세 미만이면 저장하기 전에 막는다 (Codex 리뷰 P2) - 추가 정보 화면(§3.10)만 막으면 카카오와
     * 네이버가 생년월일을 준 아동의 계정과 개인 정보가 로그인 단계에서 이미 저장된다. 가입과 재로그인 둘 다다(재로그인의
     * 빈 열 채우기도 같은 값을 저장한다). 생년월일을 주지 않은 IdP는 추가 정보 화면에서 같은 검사를 받는다.
     */
    private void rejectUnderAge(IdpProfile profile) {
        if (profile.birthDate() != null
                && ProfileRules.age(profile.birthDate(), LocalDate.now(UserService.ZONE)) < ProfileRules.MINIMUM_AGE) {
            throw new ApiException(ErrorCode.AUTH_UNDER_AGE);
        }
    }

    /**
     * 계정 찾기/만들기. 같은 사용자의 최초 로그인 두 개가 동시에 오면 둘 다 "없음"을 보고 INSERT하는데, 유일 제약이 뒤의
     * 것을 막는다 - 그때 한 번 더 돌면 이번에는 앞의 것이 만든 행을 찾아 재로그인이 된다.
     */
    private Account findOrCreate(IdpProfile profile, LoginRequest request) {
        try {
            return findOrCreateOnce(profile, request);
        } catch (DataIntegrityViolationException e) {
            return findOrCreateOnce(profile, request);
        }
    }

    private Account findOrCreateOnce(IdpProfile profile, LoginRequest request) {
        Account account = transactionTemplate.execute(tx -> {
            Instant now = Instant.now();
            AppUser existing = users.lockByProviderAndProviderUserId(profile.provider(), profile.subject())
                    .orElse(null);
            if (existing != null) {
                if (existing.deletedAt() != null) {
                    // 탈퇴(FR-AC-09)가 아직 없어 이 분기는 닿지 않는다. 탈퇴 티켓이 재가입 규칙을 정할 때까지 막아 둔다.
                    throw new ApiException(ErrorCode.AUTH_IDP_TOKEN_INVALID);
                }
                existing.fillBlanksFrom(profile, now);
                return new Account(existing, false);
            }
            String policyVersion = request.privacyPolicyVersion();
            if (!Boolean.TRUE.equals(request.privacyConsent())
                    || policyVersion == null || !POLICY_VERSION.matcher(policyVersion).matches()) {
                throw new ApiException(ErrorCode.AUTH_CONSENT_REQUIRED);
            }
            AppUser created = new AppUser(profile, policyVersion, now);
            users.saveAndFlush(created);
            return new Account(created, true);
        });
        if (account == null) {
            throw new IllegalStateException("로그인 트랜잭션이 결과 없이 끝났다");
        }
        return account;
    }

    /**
     * provider별 필수 필드를 확인하고 검증기에 넘길 모양으로 바꾼다. 모르는 provider와 빠진 토큰은 400이다 -
     * IdP에 묻기 전에 끊는다.
     */
    private static IdpCredential credential(LoginRequest request) {
        Provider provider = provider(request.provider());
        String token = provider.usesIdToken() ? request.idToken() : request.accessToken();
        if (token == null || token.isBlank() || token.length() > TOKEN_MAX) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    (provider.usesIdToken() ? "idToken" : "accessToken") + "이 필요합니다.");
        }
        String nonce = null;
        String appleName = null;
        if (provider == Provider.APPLE) {
            nonce = request.nonce();
            if (nonce == null || nonce.isBlank() || nonce.length() > NONCE_MAX) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED, "애플 로그인에는 nonce가 필요합니다.");
            }
            appleName = request.user() != null ? request.user().name() : null;
            if (appleName != null && appleName.length() > APPLE_NAME_MAX) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED, "user.name이 너무 깁니다.");
            }
        }
        return new IdpCredential(provider, token, nonce, appleName);
    }

    private static Provider provider(@Nullable String value) {
        if (value != null) {
            for (Provider provider : Provider.values()) {
                if (provider.name().equals(value)) {
                    return provider;
                }
            }
        }
        throw new ApiException(ErrorCode.VALIDATION_FAILED, "provider는 GOOGLE, KAKAO, NAVER, APPLE 중 하나여야 합니다.");
    }

    /**
     * Refresh 회전 (§3.12). 회전은 Redis가 원자적으로 하고, 그 뒤에 계정이 살아 있는지 본다 - 사라졌으면 방금 만든 새
     * 토큰까지 패밀리째 지우고 401이다.
     *
     * @throws ApiException 400 / 401 {@code AUTH_REFRESH_INVALID}, {@code AUTH_REFRESH_REUSED} / 429 / 503
     */
    TokenResponse refresh(@Nullable RefreshTokenRequest request, String clientIp) {
        rateLimits.check(RateLimits.Scope.AUTH, clientIp);
        String presented = refreshToken(request);
        RefreshTokens.Rotation rotation = refreshTokens.rotate(presented);
        if (users.findActive(rotation.userId()).isEmpty()) {
            refreshTokens.revokeFamily(rotation.token(), null);
            throw new ApiException(ErrorCode.AUTH_REFRESH_INVALID);
        }
        return new TokenResponse(accessTokens.issue(rotation.userId()), rotation.token(), accessTokens.ttlSeconds());
    }

    /**
     * 로그아웃 (§3.13) - 그 Refresh의 패밀리를 폐기한다. 모르는 토큰이나 남의 토큰은 조용히 지나간다(204).
     */
    void logout(AppUser user, @Nullable RefreshTokenRequest request) {
        boolean revoked = refreshTokens.revokeFamily(refreshToken(request), user.id());
        log.info("로그아웃 userId={} revoked={}", user.id(), revoked);
    }

    private static String refreshToken(@Nullable RefreshTokenRequest request) {
        String token = request != null ? request.refreshToken() : null;
        if (token == null || token.isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "refreshToken이 필요합니다.");
        }
        return token;
    }
}
