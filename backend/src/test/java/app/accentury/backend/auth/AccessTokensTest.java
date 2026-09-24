package app.accentury.backend.auth;

import app.accentury.backend.common.ApiException;
import app.accentury.backend.common.ErrorCode;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.PlainHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Access JWT 발급과 검증 (KAN-223, 명세서 §2.1). 실패 원인은 전부 401 {@code AUTH_TOKEN_INVALID} 하나다.
 */
class AccessTokensTest {

    private static final byte[] SECRET = "access-tokens-test-secret-0123456789abcdef".getBytes(StandardCharsets.UTF_8);

    private final AccessTokens tokens = new AccessTokens(SECRET, "accentury", Duration.ofMinutes(30), Clock.systemUTC());

    @Test
    void 발급한_토큰은_사용자_id로_돌아온다() {
        UUID userId = UUID.randomUUID();

        assertEquals(userId, tokens.verify(tokens.issue(userId)));
        assertEquals(1800, tokens.ttlSeconds());
    }

    @Test
    void 만료된_토큰은_401이다() {
        // 한 시간 전 시계로 30분짜리 토큰을 만들면 이미 만료다 - 라이브러리의 허용 오차(60초)보다 멀다.
        AccessTokens past = new AccessTokens(SECRET, "accentury", Duration.ofMinutes(30),
                Clock.fixed(Instant.now().minus(Duration.ofHours(1)), ZoneOffset.UTC));

        assertInvalid(past.issue(UUID.randomUUID()));
    }

    @Test
    void 다른_키로_서명한_토큰은_401이다() {
        AccessTokens other = new AccessTokens("another-secret-0123456789abcdef0123456789".getBytes(StandardCharsets.UTF_8),
                "accentury", Duration.ofMinutes(30), Clock.systemUTC());

        assertInvalid(other.issue(UUID.randomUUID()));
    }

    @Test
    void 발급자가_다르면_401이다() {
        AccessTokens otherIssuer = new AccessTokens(SECRET, "someone-else", Duration.ofMinutes(30), Clock.systemUTC());

        assertInvalid(otherIssuer.issue(UUID.randomUUID()));
    }

    @Test
    void 서명_없는_alg_none_토큰은_401이다() {
        // 서명을 떼어 낸 위조 - 키 선택기가 HS256만 받으므로 키를 찾지 못한다.
        PlainJWT unsigned = new PlainJWT(new PlainHeader(), claims(UUID.randomUUID().toString()));

        assertInvalid(unsigned.serialize());
    }

    @Test
    void sub가_UUID가_아니면_401이다() throws Exception {
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims("not-a-uuid"));
        jwt.sign(new MACSigner(SECRET));

        assertInvalid(jwt.serialize());
    }

    @Test
    void 형식이_JWT가_아니면_401이다() {
        assertInvalid("st_this-is-a-session-token");
        assertInvalid("");
    }

    @Test
    void 서명_키가_32바이트보다_짧으면_기동이_실패한다() {
        assertThrows(IllegalStateException.class, () -> new AccessTokens(
                "short".getBytes(StandardCharsets.UTF_8), "accentury", Duration.ofMinutes(30), Clock.systemUTC()));
    }

    private static JWTClaimsSet claims(String subject) {
        Instant now = Instant.now();
        return new JWTClaimsSet.Builder()
                .subject(subject)
                .issuer("accentury")
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plus(Duration.ofMinutes(30))))
                .jwtID(UUID.randomUUID().toString())
                .build();
    }

    private void assertInvalid(String token) {
        ApiException e = assertThrows(ApiException.class, () -> tokens.verify(token));
        assertEquals(ErrorCode.AUTH_TOKEN_INVALID, e.code());
    }
}
