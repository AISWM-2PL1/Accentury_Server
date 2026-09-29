package app.accentury.backend.auth;

import app.accentury.backend.PropertiesFixture;
import app.accentury.backend.common.AccenturyProperties;
import app.accentury.backend.common.SsmPlaceholder;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import mockwebserver3.RecordedRequest;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import tools.jackson.databind.json.JsonMapper;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 탈퇴한 애플 계정의 토큰 revoke (KAN-241). MockWebServer가 애플 인증 서버 자리에 서서 교환 성공, 교환 거절, revoke 장애를
 * 흉내 낸다. 어느 경우든 예외가 나오지 않아야 한다 - 탈퇴는 revoke 결과와 무관하게 성공한다.
 */
@ExtendWith(OutputCaptureExtension.class)
class AppleTokenRevokerTest {

    private static final String TEAM_ID = "TEAM012345";
    private static final String KEY_ID = "KEY0123456";
    private static final String BUNDLE_ID = "app.accentury.ios";
    private static final String SUBJECT = "001234.abcdef0123456789.0123";
    private static final Instant NOW = Instant.parse("2026-09-29T03:00:00Z");
    private static final UUID USER_ID = UUID.fromString("0f8c2a4e-6d1b-4c3a-9e57-2b1d8f6a4c90");

    private MockWebServer server;
    private KeyPair keyPair;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        keyPair = generator.generateKeyPair();
    }

    @AfterEach
    void tearDown() {
        server.close();
    }

    /** .p8과 같은 모양 - PKCS#8 DER의 base64를 64자씩 끊고 머리와 꼬리 줄을 붙인다. */
    private String pem() {
        String base64 = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII))
                .encodeToString(keyPair.getPrivate().getEncoded());
        return "-----BEGIN PRIVATE KEY-----\n" + base64 + "\n-----END PRIVATE KEY-----\n";
    }

    private AppleTokenRevoker revoker(@Nullable String teamId, @Nullable String keyId, @Nullable String privateKey) {
        AccenturyProperties.Auth base = PropertiesFixture.auth();
        String url = server.url("").toString().replaceAll("/$", "");
        AccenturyProperties.Auth auth = new AccenturyProperties.Auth(base.jwtSecret(), base.issuer(),
                base.accessTokenTtl(), base.refreshTokenTtl(), base.rateLimitPerMinute(), false, null, BUNDLE_ID, null,
                null, null, teamId, keyId, privateKey, base.googleJwksUrl(), base.appleJwksUrl(),
                base.kakaoApiBaseUrl(), base.naverApiBaseUrl(), base.naverAuthBaseUrl(), url, Duration.ofSeconds(2),
                base.privacyPolicyVersion());
        return new AppleTokenRevoker(auth, JsonMapper.builder().build(), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private AppleTokenRevoker configured() {
        return revoker(TEAM_ID, KEY_ID, pem());
    }

    /** 애플 교환 응답의 id_token - 서명은 revoker가 보지 않으므로 아무 키로 서명한다. */
    private String idToken(String subject) throws Exception {
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.ES256),
                new JWTClaimsSet.Builder().issuer("https://appleid.apple.com").subject(subject).audience(BUNDLE_ID).build());
        jwt.sign(new ECDSASigner((ECPrivateKey) keyPair.getPrivate()));
        return jwt.serialize();
    }

    private void enqueueExchange(String subject, @Nullable String refreshToken) throws Exception {
        String refresh = refreshToken != null ? ", \"refresh_token\": \"" + refreshToken + "\"" : "";
        enqueue(200, "{\"access_token\": \"apple-access\", \"token_type\": \"Bearer\", \"expires_in\": 3600"
                + refresh + ", \"id_token\": \"" + idToken(subject) + "\"}");
    }

    private void enqueue(int code, String body) {
        server.enqueue(new MockResponse.Builder().code(code).setHeader("Content-Type", "application/json").body(body)
                .build());
    }

    private static Map<String, String> form(RecordedRequest request) {
        Map<String, String> form = new LinkedHashMap<>();
        for (String pair : request.getBody().utf8().split("&")) {
            String[] kv = pair.split("=", 2);
            form.put(URLDecoder.decode(kv[0], StandardCharsets.UTF_8), URLDecoder.decode(kv[1], StandardCharsets.UTF_8));
        }
        return form;
    }

    @Test
    void 코드를_교환해_받은_refresh_token을_revoke한다() throws Exception {
        enqueueExchange(SUBJECT, "apple-refresh");
        enqueue(200, "");

        configured().revoke("apple-code", SUBJECT, USER_ID);

        RecordedRequest exchange = server.takeRequest(1, TimeUnit.SECONDS);
        assertEquals("/auth/token", exchange.getUrl().encodedPath());
        Map<String, String> exchangeForm = form(exchange);
        assertEquals("authorization_code", exchangeForm.get("grant_type"));
        assertEquals("apple-code", exchangeForm.get("code"));
        assertEquals(BUNDLE_ID, exchangeForm.get("client_id"));

        // client_secret = 애플 키로 서명한 ES256 JWT (iss 팀 ID, sub 번들 ID, aud 애플, kid 키 ID, 5분).
        SignedJWT secret = SignedJWT.parse(exchangeForm.get("client_secret"));
        assertTrue(secret.verify(new ECDSAVerifier((ECPublicKey) keyPair.getPublic())));
        assertEquals(JWSAlgorithm.ES256, secret.getHeader().getAlgorithm());
        assertEquals(KEY_ID, secret.getHeader().getKeyID());
        JWTClaimsSet claims = secret.getJWTClaimsSet();
        assertEquals(TEAM_ID, claims.getIssuer());
        assertEquals(BUNDLE_ID, claims.getSubject());
        assertEquals(List.of("https://appleid.apple.com"), claims.getAudience());
        assertEquals(NOW, claims.getIssueTime().toInstant());
        assertEquals(NOW.plus(Duration.ofMinutes(5)), claims.getExpirationTime().toInstant());

        RecordedRequest revoke = server.takeRequest(1, TimeUnit.SECONDS);
        assertEquals("/auth/revoke", revoke.getUrl().encodedPath());
        Map<String, String> revokeForm = form(revoke);
        assertEquals("apple-refresh", revokeForm.get("token"));
        assertEquals("refresh_token", revokeForm.get("token_type_hint"));
        assertEquals(BUNDLE_ID, revokeForm.get("client_id"));
        assertNotNull(revokeForm.get("client_secret"));
    }

    @Test
    void refresh_token이_없으면_access_token을_revoke한다() throws Exception {
        enqueueExchange(SUBJECT, null);
        enqueue(200, "");

        configured().revoke("apple-code", SUBJECT, USER_ID);

        server.takeRequest(1, TimeUnit.SECONDS);
        Map<String, String> revokeForm = form(server.takeRequest(1, TimeUnit.SECONDS));
        assertEquals("apple-access", revokeForm.get("token"));
        assertEquals("access_token", revokeForm.get("token_type_hint"));
    }

    @Test
    void 코드가_다른_애플_계정의_것이면_revoke하지_않는다(CapturedOutput output) throws Exception {
        enqueueExchange("another-apple-user", "apple-refresh");

        configured().revoke("apple-code", SUBJECT, USER_ID);

        assertEquals(1, server.getRequestCount(), "교환만 하고 revoke는 부르지 않는다");
        assertTrue(output.getOut().contains("코드가 탈퇴한 계정의 것이 아니다"));
    }

    @Test
    void 교환이_거절되거나_애플이_장애여도_예외_없이_WARN만_남긴다(CapturedOutput output) {
        enqueue(400, "{\"error\": \"invalid_grant\"}");
        configured().revoke("expired-code", SUBJECT, USER_ID);

        enqueue(503, "");
        configured().revoke("apple-code", SUBJECT, USER_ID);

        assertEquals(2, server.getRequestCount());
        assertTrue(output.getOut().contains("애플 토큰 revoke 실패 - 탈퇴는 계속한다 userId=" + USER_ID
                + " code=AUTH_IDP_TOKEN_INVALID"));
        assertTrue(output.getOut().contains("code=AUTH_IDP_UNAVAILABLE"));
        assertFalse(output.getOut().contains("expired-code"), "코드는 로그에 남기지 않는다");
    }

    @Test
    void revoke가_실패해도_예외가_나오지_않는다(CapturedOutput output) throws Exception {
        enqueueExchange(SUBJECT, "apple-refresh");
        enqueue(500, "");

        configured().revoke("apple-code", SUBJECT, USER_ID);

        assertEquals(2, server.getRequestCount());
        assertTrue(output.getOut().contains("애플 토큰 revoke 실패"));
        assertFalse(output.getOut().contains("apple-refresh"), "토큰은 로그에 남기지 않는다");
    }

    @Test
    void 코드가_없으면_애플을_부르지_않는다(CapturedOutput output) {
        configured().revoke(null, SUBJECT, USER_ID);
        configured().revoke(" ", SUBJECT, USER_ID);
        configured().revoke("x".repeat(AppleTokenRevoker.CODE_MAX + 1), SUBJECT, USER_ID);

        assertEquals(0, server.getRequestCount());
        assertTrue(output.getOut().contains("authorizationCode 없음"));
    }

    @Test
    void 설정이_비었거나_자리_표시_값이거나_키가_깨졌으면_애플을_부르지_않는다(CapturedOutput output) {
        revoker(null, KEY_ID, pem()).revoke("apple-code", SUBJECT, USER_ID);
        revoker(TEAM_ID, SsmPlaceholder.UNSET, pem()).revoke("apple-code", SUBJECT, USER_ID);
        revoker(TEAM_ID, KEY_ID, SsmPlaceholder.UNSET).revoke("apple-code", SUBJECT, USER_ID);
        revoker(TEAM_ID, KEY_ID, "-----BEGIN PRIVATE KEY-----\nnot-a-key\n-----END PRIVATE KEY-----")
                .revoke("apple-code", SUBJECT, USER_ID);

        assertEquals(0, server.getRequestCount());
        assertTrue(output.getOut().contains("애플 토큰 revoke 건너뜀 - 미설정 userId=" + USER_ID));
        assertTrue(output.getOut().contains("키를 읽지 못했다"));
    }

    @Test
    void 한_줄로_넣은_키도_읽는다() throws Exception {
        String oneLine = pem().replace("\n", " ");

        assertNotNull(AppleTokenRevoker.privateKey(oneLine));
    }
}
