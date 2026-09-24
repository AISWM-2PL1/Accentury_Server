package app.accentury.backend.auth;

import app.accentury.backend.common.AccenturyProperties;
import com.nimbusds.jwt.JWTClaimsSet;
import org.jspecify.annotations.Nullable;

import java.text.ParseException;
import java.util.Set;

/**
 * 구글 ID 토큰 (명세서 §3.9).
 * <p>
 * {@code aud}는 서버용(웹) OAuth 클라이언트 ID 하나다 - Android(Credential Manager)와 iOS(GoogleSignIn)가 모두 그 값을
 * serverClientId로 지정하므로 플랫폼마다 aud가 갈리지 않는다 (KAN-224). {@code iss}는 구글 문서대로 두 표기를 받는다.
 * <p>
 * 이메일은 {@code email_verified}가 참일 때만 받는다 - 구글 계정에는 확인되지 않은 보조 이메일이 있을 수 있다.
 */
final class GoogleIdpVerifier implements IdpVerifier {

    static final Set<String> ISSUERS = Set.of("https://accounts.google.com", "accounts.google.com");

    private final JwksIdTokens idTokens;

    GoogleIdpVerifier(AccenturyProperties.Auth auth) {
        this(new JwksIdTokens("구글", auth.googleJwksUrl(), auth.idpTimeout(), auth.googleClientId(), ISSUERS));
    }

    GoogleIdpVerifier(JwksIdTokens idTokens) {
        this.idTokens = idTokens;
    }

    @Override
    public Provider provider() {
        return Provider.GOOGLE;
    }

    @Override
    public IdpProfile verify(IdpCredential credential) {
        JWTClaimsSet claims = idTokens.verify(credential.token());
        return new IdpProfile(Provider.GOOGLE, claims.getSubject(),
                Boolean.TRUE.equals(bool(claims, "email_verified")) ? string(claims, "email") : null,
                string(claims, "name"), null, null, null, string(claims, "picture"));
    }

    static @Nullable String string(JWTClaimsSet claims, String name) {
        try {
            return claims.getStringClaim(name);
        } catch (ParseException e) {
            return null;    // 타입이 다른 클레임은 없는 것으로 본다 - 보조 정보라 로그인을 막을 이유가 없다.
        }
    }

    /** 불리언 클레임 - 애플처럼 문자열 {@code "true"}로 보내는 IdP도 있어 둘 다 받는다. */
    static @Nullable Boolean bool(JWTClaimsSet claims, String name) {
        Object value = claims.getClaim(name);
        if (value instanceof Boolean b) {
            return b;
        }
        if (value instanceof String s) {
            return Boolean.parseBoolean(s);
        }
        return null;
    }
}
