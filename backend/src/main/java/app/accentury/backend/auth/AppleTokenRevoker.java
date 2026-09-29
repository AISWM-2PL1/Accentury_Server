package app.accentury.backend.auth;

import app.accentury.backend.common.AccenturyProperties;
import app.accentury.backend.common.ApiException;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.ECPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.text.ParseException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

/**
 * 탈퇴한 애플 계정의 토큰 revoke (KAN-241, 명세서 §3.14, 애플 심사 지침 5.1.1(v)).
 * <p>
 * 서버는 로그인 때 애플 토큰을 보관하지 않는다(KAN-223은 identityToken만 확인한다). 그래서 앱이 탈퇴 직전에 애플
 * 로그인을 한 번 더 해서 받은 {@code authorizationCode}를 탈퇴 요청에 싣고, 서버가 그 자리에서
 * {@code POST /auth/token}으로 교환한 refresh token을 {@code POST /auth/revoke}에 넘긴다. 교환으로 받은 토큰은 이
 * 요청 안에서만 쓰고 버린다.
 * <p>
 * 교환 응답의 {@code id_token}에서 {@code sub}를 읽어 탈퇴하는 계정의 애플 사용자 id와 같을 때만 revoke한다 - 다른
 * 애플 계정의 코드로 남의 연결을 끊지 않게 한다. 이 {@code id_token}은 애플과 TLS로 직접 주고받은 응답이라 서명을
 * 다시 확인하지 않는다 (OpenID Connect Core §3.1.3.7).
 * <p>
 * <b>이 클래스는 예외를 던지지 않는다.</b> 설정이 없거나, 코드가 없거나, 애플이 거절하거나 닿지 않으면 WARN만 남기고
 * 돌아간다 - 탈퇴와 개인 정보 파기는 우리 쪽에서 이미 끝났고, 애플 연결은 사용자가 iOS 설정에서도 끊을 수 있다
 * (2026-09-29 확정). 로그에는 코드와 토큰을 남기지 않는다.
 * <p>
 * 애플 호출 둘(교환, revoke)은 탈퇴 요청 스레드에서 동기로 돈다. 호출마다 연결과 읽기가 각각 {@code idp-timeout}(5초)까지라
 * 애플이 응답하지 않으면 탈퇴 응답이 최대 약 20초 늦어진다 - 앱은 이 요청의 타임아웃을 20초보다 길게 잡는다 (명세서 §3.14,
 * KAN-224 전달).
 * <p>
 * client_secret은 애플 키(.p8)로 서명한 ES256 JWT다 ({@code iss} 팀 ID, {@code sub} 번들 ID, {@code aud}
 * {@code https://appleid.apple.com}, 헤더 {@code kid} 키 ID). 수명은 애플 상한(6개월)보다 훨씬 짧은 5분으로 요청마다
 * 새로 만든다.
 */
final class AppleTokenRevoker {

    private static final Logger log = LoggerFactory.getLogger(AppleTokenRevoker.class);

    private static final String AUDIENCE = "https://appleid.apple.com";
    private static final Duration SECRET_TTL = Duration.ofMinutes(5);

    /** 앱이 보내는 authorization code 길이 상한 - 애플 코드는 100자 안팎이다. */
    static final int CODE_MAX = 1024;

    private final IdpHttp http;
    private final Clock clock;
    private final @Nullable Signing signing;

    private record Signing(String teamId, String keyId, String bundleId, ECDSASigner signer) {
    }

    AppleTokenRevoker(AccenturyProperties.Auth auth, ObjectMapper objectMapper) {
        this(auth, objectMapper, Clock.systemUTC());
    }

    AppleTokenRevoker(AccenturyProperties.Auth auth, ObjectMapper objectMapper, Clock clock) {
        this.http = new IdpHttp("애플", auth.appleAuthBaseUrl(), auth.idpTimeout(), objectMapper);
        this.clock = clock;
        this.signing = signing(auth);
    }

    private static @Nullable Signing signing(AccenturyProperties.Auth auth) {
        if (!JwksIdTokens.configured(auth.appleTeamId()) || !JwksIdTokens.configured(auth.appleKeyId())
                || !JwksIdTokens.configured(auth.applePrivateKey()) || !JwksIdTokens.configured(auth.appleBundleId())) {
            log.warn("애플 토큰 revoke 미설정 - 팀 ID, 키 ID, 키, 번들 ID 중 빈 값이 있어 탈퇴 때 revoke를 건너뛴다");
            return null;
        }
        try {
            return new Signing(auth.appleTeamId(), auth.appleKeyId(), auth.appleBundleId(),
                    new ECDSASigner(privateKey(auth.applePrivateKey())));
        } catch (GeneralSecurityException | IllegalArgumentException | JOSEException e) {
            // 키 원문은 남기지 않는다. 기동은 막지 않는다 - revoke만 꺼지고 로그인과 탈퇴는 그대로다.
            log.warn("애플 토큰 revoke 미설정 - 키를 읽지 못했다 ({})", e.getClass().getSimpleName());
            return null;
        }
    }

    /** .p8 원문(PKCS#8 PEM) - 머리와 꼬리 줄, 공백을 걷고 base64를 푼다. SSM에 한 줄로 넣어도 읽힌다. */
    static ECPrivateKey privateKey(String pem) throws GeneralSecurityException {
        String base64 = pem.replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        byte[] der = Base64.getDecoder().decode(base64);
        return (ECPrivateKey) KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(der));
    }

    /**
     * 탈퇴한 애플 계정의 토큰을 revoke한다. 결과는 로그로만 남는다.
     *
     * @param authorizationCode 앱이 탈퇴 요청에 실은 애플 authorization code - 없으면 건너뛴다
     * @param subject           탈퇴한 계정의 애플 사용자 id - 교환 결과의 {@code sub}와 같아야 revoke한다
     * @param userId            로그용 계정 id
     */
    void revoke(@Nullable String authorizationCode, String subject, UUID userId) {
        if (signing == null) {
            log.warn("애플 토큰 revoke 건너뜀 - 미설정 userId={}", userId);
            return;
        }
        if (authorizationCode == null || authorizationCode.isBlank() || authorizationCode.length() > CODE_MAX) {
            log.warn("애플 토큰 revoke 건너뜀 - authorizationCode 없음 userId={}", userId);
            return;
        }
        try {
            String clientSecret = clientSecret(signing);
            JsonNode issued = http.postForm("/auth/token", form(signing, clientSecret,
                    "grant_type", "authorization_code", "code", authorizationCode));
            String issuedSubject = subject(KakaoIdpVerifier.text(issued.get("id_token")));
            if (!subject.equals(issuedSubject)) {
                log.warn("애플 토큰 revoke 건너뜀 - 코드가 탈퇴한 계정의 것이 아니다 userId={}", userId);
                return;
            }
            String refreshToken = KakaoIdpVerifier.text(issued.get("refresh_token"));
            String accessToken = KakaoIdpVerifier.text(issued.get("access_token"));
            if (refreshToken == null && accessToken == null) {
                log.warn("애플 토큰 revoke 건너뜀 - 교환 응답에 토큰이 없다 userId={}", userId);
                return;
            }
            // refresh token을 revoke하면 그 사용자와 우리 앱의 연결 전체가 끊긴다. 없을 때만 access token으로 한다.
            http.postFormWithoutBody("/auth/revoke", form(signing, clientSecret,
                    "token", refreshToken != null ? refreshToken : accessToken,
                    "token_type_hint", refreshToken != null ? "refresh_token" : "access_token"));
            log.info("애플 토큰 revoke userId={}", userId);
        } catch (ApiException e) {
            // IdpHttp가 상태 코드를 이미 남겼다. 교환 실패(만료된 코드, 5분)와 애플 장애가 여기로 온다.
            log.warn("애플 토큰 revoke 실패 - 탈퇴는 계속한다 userId={} code={}", userId, e.code());
        } catch (JOSEException e) {
            log.warn("애플 토큰 revoke 실패 - client_secret 서명 오류 userId={}", userId);
        } catch (RuntimeException e) {
            // 탈퇴는 이미 커밋됐다 - 여기서 새면 앱은 500을 받고, 재시도는 401이라 탈퇴 결과를 알 수 없다 (PR #9 리뷰).
            // 예외 메시지는 남기지 않는다 - 응답 조각이 들어 있을 수 있다.
            log.warn("애플 토큰 revoke 실패 - 예상 밖 오류 userId={} ({})", userId, e.getClass().getSimpleName());
        }
    }

    private String clientSecret(Signing signing) throws JOSEException {
        Instant now = clock.instant();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(signing.teamId())
                .subject(signing.bundleId())
                .audience(AUDIENCE)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plus(SECRET_TTL)))
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.ES256).keyID(signing.keyId()).build(), claims);
        jwt.sign(signing.signer());
        return jwt.serialize();
    }

    private static MultiValueMap<String, String> form(Signing signing, String clientSecret, String... pairs) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", signing.bundleId());
        form.add("client_secret", clientSecret);
        for (int i = 0; i < pairs.length; i += 2) {
            form.add(pairs[i], pairs[i + 1]);
        }
        return form;
    }

    private static @Nullable String subject(@Nullable String idToken) {
        if (idToken == null) {
            return null;
        }
        try {
            return SignedJWT.parse(idToken).getJWTClaimsSet().getSubject();
        } catch (ParseException e) {
            return null;
        }
    }
}
