package app.accentury.backend.auth;

import app.accentury.backend.common.AccenturyProperties;
import app.accentury.backend.common.ApiException;
import app.accentury.backend.common.ErrorCode;
import com.nimbusds.jwt.JWTClaimsSet;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Set;

/**
 * 애플 identityToken (명세서 §3.9, iOS만).
 * <p>
 * {@code aud}는 iOS 번들 ID다. <b>nonce가 핵심이다</b> - 클라이언트가 만든 원문 nonce의 SHA-256(hex)을 애플 요청에
 * 실었고, 애플은 그 값을 토큰의 {@code nonce}에 그대로 넣는다. 서버가 원문으로 다시 계산해 맞춰 보면, 가로챈 토큰을
 * 다른 요청에 재생해도 원문을 모르므로 통과하지 못한다.
 * <p>
 * 이름은 토큰에 없다. 애플은 최초 로그인 한 번만 이름을 SDK 응답으로 주므로 클라이언트가 요청 본문 {@code user.name}에
 * 싣는다. 그 요청이 실패하면 이름은 다시 오지 않으니 추가 정보 화면이 받는다 (KAN-224). 이메일은 릴레이 주소
 * ({@code privaterelay.appleid.com})일 수 있고 그대로 둔다.
 */
final class AppleIdpVerifier implements IdpVerifier {

    static final Set<String> ISSUERS = Set.of("https://appleid.apple.com");

    private final JwksIdTokens idTokens;

    AppleIdpVerifier(AccenturyProperties.Auth auth) {
        this(new JwksIdTokens("애플", auth.appleJwksUrl(), auth.idpTimeout(), auth.appleBundleId(), ISSUERS));
    }

    AppleIdpVerifier(JwksIdTokens idTokens) {
        this.idTokens = idTokens;
    }

    @Override
    public Provider provider() {
        return Provider.APPLE;
    }

    @Override
    public IdpProfile verify(IdpCredential credential) {
        JWTClaimsSet claims = idTokens.verify(credential.token());
        String rawNonce = credential.rawNonce();
        String nonce = GoogleIdpVerifier.string(claims, "nonce");
        if (rawNonce == null || nonce == null
                || !MessageDigest.isEqual(sha256Hex(rawNonce).getBytes(StandardCharsets.US_ASCII),
                nonce.getBytes(StandardCharsets.US_ASCII))) {
            throw new ApiException(ErrorCode.AUTH_IDP_TOKEN_INVALID);
        }
        // 애플 이메일은 애플이 확인한 주소다. email_verified가 명시적으로 거짓일 때만 버린다.
        Boolean verified = GoogleIdpVerifier.bool(claims, "email_verified");
        return new IdpProfile(Provider.APPLE, claims.getSubject(),
                Boolean.FALSE.equals(verified) ? null : GoogleIdpVerifier.string(claims, "email"),
                credential.appleName(), null, null, null, null);
    }

    static String sha256Hex(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256을 지원하지 않는 JVM", e);
        }
    }
}
