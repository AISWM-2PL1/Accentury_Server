package app.accentury.backend.auth;

import app.accentury.backend.common.AccenturyProperties;
import app.accentury.backend.common.ApiException;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 탈퇴한 카카오, 네이버 계정의 연결 해제 (KAN-343, 명세서 §3.14, 애플 심사 지침 5.1.1(v)).
 * <p>
 * 탈퇴는 우리 쪽 계정을 파기하지만, 로그인 제공자 쪽에는 "이 사용자가 우리 앱에 동의했다"는 연결이 남는다. 그 연결을
 * 끊어야 제공자의 「연결된 서비스」 목록에서 우리 앱이 사라지고, 다시 로그인할 때 동의 화면부터 시작한다. 애플은
 * {@link AppleTokenRevoker}가 맡는다. 구글은 서버가 가진 토큰이 없고 끊는 API가 사용자 토큰을 요구해 대상이 아니다.
 *
 * <h4>카카오</h4>
 * 서비스 앱 어드민 키로 {@code POST /v1/user/unlink}를 부른다 ({@code target_id_type=user_id}, {@code target_id}는
 * 회원번호 = 계정의 IdP 사용자 id). 사용자 토큰이 필요 없어 앱이 따로 보낼 것이 없다. 어드민 키는 카카오 공유 웹훅
 * 검증에 쓰는 키와 같은 값이다 ({@code accentury.share.kakao-admin-key}) - 한 카카오 앱의 키라서다.
 *
 * <h4>네이버</h4>
 * 네이버의 토큰 삭제({@code POST /oauth2.0/token}, {@code grant_type=delete})는 사용자의 access token을 요구하고,
 * 서버는 로그인 때 네이버 토큰을 보관하지 않는다. 그래서 앱이 탈퇴 요청에 SDK의 refresh token을 싣는다 (로그인 때
 * 보내는 것과 같은 값). 서버는 그것을 우리 클라이언트로 교환해 새 access token을 받고(다른 앱의 토큰이면 여기서
 * 거절된다), 그 토큰의 주인이 탈퇴하는 계정인지 확인한 뒤 삭제를 부른다 - 남의 토큰으로 남의 연결을 끊지 않게 한다
 * ({@link AppleTokenRevoker}와 같은 규칙). 교환으로 받은 토큰은 이 요청 안에서만 쓰고 버린다.
 * <p>
 * <b>이 클래스는 예외를 던지지 않는다.</b> 설정이 없거나, 토큰이 없거나, 제공자가 거절하거나 닿지 않으면 WARN만 남기고
 * 돌아간다 - 탈퇴와 개인 정보 파기는 우리 쪽에서 이미 끝났고, 연결은 사용자가 제공자의 설정 화면에서도 끊을 수 있다.
 * 로그에는 토큰과 키를 남기지 않는다. 호출은 탈퇴 요청 스레드에서 동기로 돈다 (카카오 1회, 네이버 3회, 호출마다
 * {@code idp-timeout}까지).
 */
final class IdpUnlinker {

    private static final Logger log = LoggerFactory.getLogger(IdpUnlinker.class);

    /** 카카오 회원번호 - 숫자만이다. 가짜 IdP의 sub처럼 숫자가 아닌 값은 카카오에 보내지 않는다. */
    private static final Pattern KAKAO_USER_ID = Pattern.compile("[0-9]{1,20}");

    private final IdpHttp kakao;
    private final @Nullable String kakaoAdminKey;
    private final IdpHttp naverApi;
    private final IdpHttp naverAuth;
    private final @Nullable String naverClientId;
    private final @Nullable String naverClientSecret;

    /**
     * @param kakaoAdminKey 카카오 서비스 앱 어드민 키 ({@code accentury.share.kakao-admin-key}) - 없거나 자리 표시 값이면
     *                      카카오 연결 해제만 꺼진다
     */
    IdpUnlinker(AccenturyProperties.Auth auth, @Nullable String kakaoAdminKey, ObjectMapper objectMapper) {
        this.kakao = new IdpHttp("카카오", auth.kakaoApiBaseUrl(), auth.idpTimeout(), objectMapper);
        this.kakaoAdminKey = JwksIdTokens.configured(kakaoAdminKey) ? kakaoAdminKey : null;
        this.naverApi = new IdpHttp("네이버", auth.naverApiBaseUrl(), auth.idpTimeout(), objectMapper);
        this.naverAuth = new IdpHttp("네이버", auth.naverAuthBaseUrl(), auth.idpTimeout(), objectMapper);
        boolean naverConfigured = JwksIdTokens.configured(auth.naverClientId())
                && JwksIdTokens.configured(auth.naverClientSecret());
        this.naverClientId = naverConfigured ? auth.naverClientId() : null;
        this.naverClientSecret = naverConfigured ? auth.naverClientSecret() : null;
        if (kakaoAdminKey == null) {
            log.warn("카카오 연결 해제 미설정 - 어드민 키가 없어 탈퇴 때 연결 해제를 건너뛴다");
        }
        if (!naverConfigured) {
            log.warn("네이버 연결 해제 미설정 - Client ID나 Secret이 없어 탈퇴 때 연결 해제를 건너뛴다");
        }
    }

    /**
     * 탈퇴한 카카오 계정의 연결을 끊는다. 결과는 로그로만 남는다.
     *
     * @param subject 탈퇴한 계정의 카카오 회원번호
     * @param userId  로그용 계정 id
     */
    void unlinkKakao(String subject, UUID userId) {
        if (kakaoAdminKey == null) {
            log.warn("카카오 연결 해제 건너뜀 - 미설정 userId={}", userId);
            return;
        }
        if (!KAKAO_USER_ID.matcher(subject).matches()) {
            log.warn("카카오 연결 해제 건너뜀 - 회원번호 형식이 아니다 userId={}", userId);
            return;
        }
        try {
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("target_id_type", "user_id");
            form.add("target_id", subject);
            JsonNode unlinked = kakao.postForm("/v1/user/unlink", form, "KakaoAK " + kakaoAdminKey);
            if (!subject.equals(KakaoIdpVerifier.text(unlinked.get("id")))) {
                log.warn("카카오 연결 해제 실패 - 응답의 회원번호가 다르다 userId={}", userId);
                return;
            }
            log.info("카카오 연결 해제 userId={}", userId);
        } catch (ApiException e) {
            // 이미 끊긴 사용자도 카카오가 4xx로 답한다 - 상태 코드는 IdpHttp가 남겼다.
            log.warn("카카오 연결 해제 실패 userId={} code={}", userId, e.code());
        } catch (RuntimeException e) {
            log.warn("카카오 연결 해제 실패 - 예상 밖 오류 userId={} ({})", userId, e.getClass().getSimpleName());
        }
    }

    /**
     * 탈퇴한 네이버 계정의 토큰을 삭제한다. 결과는 로그로만 남는다.
     *
     * @param refreshToken 앱이 탈퇴 요청에 실은 네이버 SDK refresh token - 없으면 건너뛴다
     * @param subject      탈퇴한 계정의 네이버 사용자 id - 교환한 토큰의 주인과 같아야 삭제한다
     * @param userId       로그용 계정 id
     */
    void unlinkNaver(@Nullable String refreshToken, String subject, UUID userId) {
        if (naverClientId == null || naverClientSecret == null) {
            log.warn("네이버 연결 해제 건너뜀 - 미설정 userId={}", userId);
            return;
        }
        if (refreshToken == null || refreshToken.isBlank() || refreshToken.length() > AuthService.TOKEN_MAX) {
            log.warn("네이버 연결 해제 건너뜀 - naverRefreshToken 없음 userId={}", userId);
            return;
        }
        try {
            String accessToken = NaverIdpVerifier.exchange(naverAuth, naverClientId, naverClientSecret, refreshToken);
            JsonNode me = naverApi.get("/v1/nid/me", accessToken);
            if (!subject.equals(KakaoIdpVerifier.text(me.path("response").get("id")))) {
                log.warn("네이버 연결 해제 건너뜀 - 토큰이 탈퇴한 계정의 것이 아니다 userId={}", userId);
                return;
            }
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("grant_type", "delete");
            form.add("client_id", naverClientId);
            form.add("client_secret", naverClientSecret);
            form.add("access_token", accessToken);
            form.add("service_provider", "NAVER");
            JsonNode deleted = naverAuth.postForm("/oauth2.0/token", form);
            if (!"success".equals(KakaoIdpVerifier.text(deleted.get("result")))) {
                // 네이버는 오류를 200 + {"error": ...}로 주기도 한다 - 오류 코드만 남긴다.
                log.warn("네이버 연결 해제 실패 userId={} error={}", userId, KakaoIdpVerifier.text(deleted.get("error")));
                return;
            }
            log.info("네이버 연결 해제 userId={}", userId);
        } catch (ApiException e) {
            log.warn("네이버 연결 해제 실패 userId={} code={}", userId, e.code());
        } catch (RuntimeException e) {
            log.warn("네이버 연결 해제 실패 - 예상 밖 오류 userId={} ({})", userId, e.getClass().getSimpleName());
        }
    }
}
