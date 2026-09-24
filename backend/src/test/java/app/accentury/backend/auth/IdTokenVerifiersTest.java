package app.accentury.backend.auth;

import app.accentury.backend.common.ApiException;
import app.accentury.backend.common.ErrorCode;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import mockwebserver3.Dispatcher;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import mockwebserver3.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 구글과 애플 ID 토큰 검증 (KAN-223 요구 7, 명세서 §3.9). 테스트가 만든 RSA 키로 서명하고, 그 공개 키를 MockWebServer가
 * JWKS로 내준다 - 서명, iss, aud, exp, nonce 각각의 실패 경로와 JWKS 장애를 가른다.
 */
class IdTokenVerifiersTest {

    private static final String GOOGLE_CLIENT_ID = "1234-server.apps.googleusercontent.com";
    private static final String APPLE_BUNDLE_ID = "app.accentury.ios";
    private static final String RAW_NONCE = "raw-nonce-7f3a9c";

    private MockWebServer jwksServer;
    private RSAKey signingKey;
    private volatile int jwksStatus = 200;

    @BeforeEach
    void setUp() throws Exception {
        signingKey = new RSAKeyGenerator(2048).keyID("test-key").generate();
        String jwks = new JWKSet(signingKey.toPublicJWK()).toString();
        jwksServer = new MockWebServer();
        jwksServer.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                return jwksStatus == 200
                        ? new MockResponse.Builder().code(200).setHeader("Content-Type", "application/json").body(jwks).build()
                        : new MockResponse.Builder().code(jwksStatus).build();
            }
        });
        jwksServer.start();
    }

    @AfterEach
    void tearDown() {
        jwksServer.close();
    }

    private GoogleIdpVerifier google(String audience) {
        return new GoogleIdpVerifier(new JwksIdTokens("구글", jwksServer.url("/certs").toString(), Duration.ofSeconds(2),
                audience, GoogleIdpVerifier.ISSUERS));
    }

    private AppleIdpVerifier apple() {
        return new AppleIdpVerifier(new JwksIdTokens("애플", jwksServer.url("/auth/keys").toString(), Duration.ofSeconds(2),
                APPLE_BUNDLE_ID, AppleIdpVerifier.ISSUERS));
    }

    // === 구글 ===

    @Test
    void 구글_정상_토큰은_sub와_확인된_이메일과_이름을_준다() throws Exception {
        String token = sign(signingKey, googleClaims(b -> b.claim("email", "user@example.com")
                .claim("email_verified", true).claim("name", "홍길동").claim("picture", "https://lh3.example/p.jpg")));

        IdpProfile profile = google(GOOGLE_CLIENT_ID).verify(new IdpCredential(Provider.GOOGLE, token, null, null));

        assertEquals("google-sub-1", profile.subject());
        assertEquals("user@example.com", profile.email());
        assertEquals("홍길동", profile.name());
        assertEquals("https://lh3.example/p.jpg", profile.profileImageUrl());
    }

    @Test
    void 구글_이메일이_확인되지_않았으면_이메일을_받지_않는다() throws Exception {
        String token = sign(signingKey, googleClaims(b -> b.claim("email", "user@example.com").claim("email_verified", false)));

        assertNull(google(GOOGLE_CLIENT_ID).verify(new IdpCredential(Provider.GOOGLE, token, null, null)).email());
    }

    @Test
    void 구글_다른_키로_서명한_토큰은_401이다() throws Exception {
        RSAKey attacker = new RSAKeyGenerator(2048).keyID("test-key").generate();

        assertIdpInvalid(() -> google(GOOGLE_CLIENT_ID).verify(
                new IdpCredential(Provider.GOOGLE, sign(attacker, googleClaims(b -> { })), null, null)));
    }

    @Test
    void 구글_다른_앱의_aud는_401이다() throws Exception {
        String token = sign(signingKey, googleClaims(b -> b.audience("someone-elses-client-id")));

        assertIdpInvalid(() -> google(GOOGLE_CLIENT_ID).verify(new IdpCredential(Provider.GOOGLE, token, null, null)));
    }

    @Test
    void 구글_iss가_다르면_401이다() throws Exception {
        String token = sign(signingKey, googleClaims(b -> b.issuer("https://evil.example")));

        assertIdpInvalid(() -> google(GOOGLE_CLIENT_ID).verify(new IdpCredential(Provider.GOOGLE, token, null, null)));
    }

    @Test
    void 구글_iss는_두_표기를_다_받는다() throws Exception {
        String token = sign(signingKey, googleClaims(b -> b.issuer("accounts.google.com")));

        assertEquals("google-sub-1",
                google(GOOGLE_CLIENT_ID).verify(new IdpCredential(Provider.GOOGLE, token, null, null)).subject());
    }

    @Test
    void 구글_만료된_토큰은_401이다() throws Exception {
        Instant past = Instant.now().minus(Duration.ofHours(2));
        String token = sign(signingKey, googleClaims(b -> b.issueTime(Date.from(past))
                .expirationTime(Date.from(past.plus(Duration.ofHours(1))))));

        assertIdpInvalid(() -> google(GOOGLE_CLIENT_ID).verify(new IdpCredential(Provider.GOOGLE, token, null, null)));
    }

    @Test
    void 구글_형식이_JWT가_아니면_401이다() {
        assertIdpInvalid(() -> google(GOOGLE_CLIENT_ID).verify(new IdpCredential(Provider.GOOGLE, "not-a-jwt", null, null)));
    }

    @Test
    void 구글_클라이언트_ID가_자리_표시_값이면_모든_토큰이_401이다() throws Exception {
        String token = sign(signingKey, googleClaims(b -> { }));

        assertIdpInvalid(() -> google("unset-put-parameter-after-apply")
                .verify(new IdpCredential(Provider.GOOGLE, token, null, null)));
    }

    @Test
    void JWKS를_받지_못하면_502다() throws Exception {
        jwksStatus = 503;
        String token = sign(signingKey, googleClaims(b -> { }));

        ApiException e = assertThrows(ApiException.class,
                () -> google(GOOGLE_CLIENT_ID).verify(new IdpCredential(Provider.GOOGLE, token, null, null)));
        assertEquals(ErrorCode.AUTH_IDP_UNAVAILABLE, e.code());
    }

    // === 애플 ===

    @Test
    void 애플_정상_토큰은_nonce가_맞으면_sub와_이메일과_본문의_이름을_준다() throws Exception {
        String token = sign(signingKey, appleClaims(AppleIdpVerifier.sha256Hex(RAW_NONCE),
                b -> b.claim("email", "abc@privaterelay.appleid.com").claim("email_verified", "true")));

        IdpProfile profile = apple().verify(new IdpCredential(Provider.APPLE, token, RAW_NONCE, "김애플"));

        assertEquals("apple-sub-1", profile.subject());
        assertEquals("abc@privaterelay.appleid.com", profile.email());
        assertEquals("김애플", profile.name());
    }

    @Test
    void 애플_nonce가_다르면_401이다() throws Exception {
        String token = sign(signingKey, appleClaims(AppleIdpVerifier.sha256Hex("another-nonce"), b -> { }));

        assertIdpInvalid(() -> apple().verify(new IdpCredential(Provider.APPLE, token, RAW_NONCE, null)));
    }

    @Test
    void 애플_토큰에_nonce가_없으면_401이다() throws Exception {
        String token = sign(signingKey, appleClaims(null, b -> { }));

        assertIdpInvalid(() -> apple().verify(new IdpCredential(Provider.APPLE, token, RAW_NONCE, null)));
    }

    @Test
    void 애플_원문_nonce를_그대로_토큰에_넣은_것은_401이다() throws Exception {
        // 해시가 아니라 원문이 토큰에 들어 있으면 클라이언트가 SHA-256을 빠뜨린 것이다 - 재생 방어가 무너지므로 받지 않는다.
        String token = sign(signingKey, appleClaims(RAW_NONCE, b -> { }));

        assertIdpInvalid(() -> apple().verify(new IdpCredential(Provider.APPLE, token, RAW_NONCE, null)));
    }

    @Test
    void 애플_다른_앱의_aud는_401이다() throws Exception {
        String token = sign(signingKey, appleClaims(AppleIdpVerifier.sha256Hex(RAW_NONCE), b -> b.audience("com.other.app")));

        assertIdpInvalid(() -> apple().verify(new IdpCredential(Provider.APPLE, token, RAW_NONCE, null)));
    }

    @Test
    void 애플_iss가_다르면_401이다() throws Exception {
        String token = sign(signingKey, appleClaims(AppleIdpVerifier.sha256Hex(RAW_NONCE),
                b -> b.issuer("https://accounts.google.com")));

        assertIdpInvalid(() -> apple().verify(new IdpCredential(Provider.APPLE, token, RAW_NONCE, null)));
    }

    private static JWTClaimsSet googleClaims(Consumer<JWTClaimsSet.Builder> customize) {
        Instant now = Instant.now();
        JWTClaimsSet.Builder builder = new JWTClaimsSet.Builder()
                .subject("google-sub-1")
                .issuer("https://accounts.google.com")
                .audience(GOOGLE_CLIENT_ID)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plus(Duration.ofHours(1))));
        customize.accept(builder);
        return builder.build();
    }

    private static JWTClaimsSet appleClaims(String nonce, Consumer<JWTClaimsSet.Builder> customize) {
        Instant now = Instant.now();
        JWTClaimsSet.Builder builder = new JWTClaimsSet.Builder()
                .subject("apple-sub-1")
                .issuer("https://appleid.apple.com")
                .audience(APPLE_BUNDLE_ID)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plus(Duration.ofMinutes(10))))
                .jwtID(UUID.randomUUID().toString());
        if (nonce != null) {
            builder.claim("nonce", nonce);
        }
        customize.accept(builder);
        return builder.build();
    }

    private static String sign(RSAKey key, JWTClaimsSet claims) throws Exception {
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(), claims);
        jwt.sign(new RSASSASigner(key));
        return jwt.serialize();
    }

    private static void assertIdpInvalid(Executable call) {
        ApiException e = assertThrows(ApiException.class, call);
        assertEquals(ErrorCode.AUTH_IDP_TOKEN_INVALID, e.code());
    }
}
