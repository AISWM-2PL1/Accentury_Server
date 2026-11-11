package app.accentury.backend.auth;

import app.accentury.backend.common.AccenturyProperties;
import app.accentury.backend.common.ApiException;
import app.accentury.backend.common.ErrorCode;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.proc.BadJOSEException;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.text.ParseException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Set;
import java.util.UUID;

/**
 * Access 토큰 = JWT HS256 (KAN-223, 명세서 §2.1, NFR-SC-03).
 * <p>
 * 클레임은 {@code sub}(사용자 id), {@code iat}, {@code exp}, {@code jti}, {@code iss}뿐이다. 프로필 완료 여부는
 * 넣지 않는다 - 넣으면 프로필을 채운 직후 토큰을 다시 받아야 하고, 요청마다 DB의 계정 행을 어차피 읽는다.
 * 이메일과 이름도 넣지 않는다: JWT 본문은 서명만 됐지 암호화되지 않아 누구나 읽는다.
 * <p>
 * 검증 실패의 원인(형식, 서명, 만료, 발급자)은 구분하지 않고 전부 401 {@code AUTH_TOKEN_INVALID}다.
 * 알고리즘은 HS256만 받는다 - 키 선택기가 헤더의 {@code alg}를 HS256으로 고정하므로 {@code none}이나
 * 비대칭 알고리즘으로 바꿔치기한 토큰은 키를 찾지 못해 떨어진다.
 */
@Component
public class AccessTokens {

    private static final Logger log = LoggerFactory.getLogger(AccessTokens.class);

    /** HS256 키의 최소 길이 - 알고리즘의 출력 크기(256bit)보다 짧은 키는 RFC 7518 §3.2가 금한다. */
    static final int MIN_SECRET_BYTES = 32;

    private final byte[] secret;
    private final String issuer;
    private final Duration ttl;
    private final Clock clock;
    private final DefaultJWTProcessor<SecurityContext> processor;

    @Autowired
    AccessTokens(AccenturyProperties properties) {
        this(secretOrEphemeral(properties.auth().jwtSecret()), properties.auth().issuer(),
                properties.auth().accessTokenTtl(), Clock.systemUTC());
    }

    AccessTokens(byte[] secret, String issuer, Duration ttl, Clock clock) {
        if (secret.length < MIN_SECRET_BYTES) {
            // 길이만 알리고 값은 알리지 않는다 - 기동 로그도 로그다.
            throw new IllegalStateException("accentury.auth.jwt-secret이 너무 짧다: "
                    + secret.length + "바이트 (최소 " + MIN_SECRET_BYTES + "바이트, 무작위 발급)");
        }
        this.secret = secret.clone();
        this.issuer = issuer;
        this.ttl = ttl;
        this.clock = clock;
        this.processor = new DefaultJWTProcessor<>();
        processor.setJWSKeySelector(new JWSVerificationKeySelector<>(JWSAlgorithm.HS256,
                new ImmutableSecret<>(this.secret)));
        processor.setJWTClaimsSetVerifier(new DefaultJWTClaimsVerifier<>(
                new JWTClaimsSet.Builder().issuer(issuer).build(),
                Set.of("sub", "iat", "exp", "jti")));
    }

    /**
     * 설정의 키를 바이트로. 비어 있으면 기동마다 새 난수 키다 - 로컬 개발 전용 경로이고, 배포 프로파일은
     * 비어 있으면 이 빈이 만들어지기 전에 기동이 선다 ({@code DeploymentConfigGuard}).
     */
    private static byte[] secretOrEphemeral(@Nullable String configured) {
        if (configured != null && !configured.isBlank()) {
            return configured.getBytes(StandardCharsets.UTF_8);
        }
        log.warn("accentury.auth.jwt-secret 미설정 - 기동마다 새 난수 키를 쓴다. 재기동하면 발급한 Access 토큰이 전부 무효다 (로컬 전용)");
        byte[] random = new byte[MIN_SECRET_BYTES];
        new SecureRandom().nextBytes(random);
        return random;
    }

    /**
     * 서명 키에서 다른 용도의 키를 끌어낸다 - {@code HMAC-SHA256(서명 키, 용도)}다. 서버만 아는 비밀값이 필요한 곳이
     * 시크릿을 하나 더 두지 않고 쓴다 (게스트 발급 상한의 IP 해시, {@link GuestQuota}, KAN-329). 용도 문자열이 다르면
     * 키가 달라 서로의 값을 만들 수 없고, 끌어낸 키로는 서명 키를 되짚지 못한다 - 끌어낸 키가 새도 토큰 위조로 이어지지
     * 않는다. 서명 키가 설정되지 않은 로컬에서는 기동마다 다른 키가 나온다.
     */
    byte[] deriveKey(String purpose) {
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(secret, "HmacSHA256"));
            return mac.doFinal(purpose.getBytes(StandardCharsets.UTF_8));
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA256을 쓸 수 없는 JVM", e);
        }
    }

    /** Access 토큰 수명(초) - 응답의 {@code accessTokenExpiresInSec} (§3.9, §3.12). */
    public long ttlSeconds() {
        return ttl.toSeconds();
    }

    /** 사용자 id를 {@code sub}로 담은 새 Access 토큰. */
    public String issue(UUID userId) {
        Instant now = clock.instant();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(userId.toString())
                .issuer(issuer)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plus(ttl)))
                .jwtID(UUID.randomUUID().toString())
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        try {
            jwt.sign(new MACSigner(secret));
        } catch (JOSEException e) {
            // 키 길이는 생성자에서 이미 확인했다 - 여기 오면 라이브러리 쪽 문제다.
            throw new IllegalStateException("Access 토큰 서명 실패", e);
        }
        return jwt.serialize();
    }

    /**
     * 토큰을 검증하고 사용자 id를 돌려준다.
     *
     * @throws ApiException 401 {@code AUTH_TOKEN_INVALID} - 형식, 서명, 만료, 발급자, sub 형식 어느 것이든
     */
    public UUID verify(String token) {
        try {
            JWTClaimsSet claims = processor.process(token, null);
            return UUID.fromString(claims.getSubject());
        } catch (ParseException | BadJOSEException | JOSEException | IllegalArgumentException e) {
            // 원인은 로그에도 자세히 남기지 않는다 - 토큰 조각이 예외 메시지에 섞여 나올 수 있다.
            throw new ApiException(ErrorCode.AUTH_TOKEN_INVALID);
        }
    }
}
