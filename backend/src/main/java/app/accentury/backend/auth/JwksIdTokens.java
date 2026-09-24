package app.accentury.backend.auth;

import app.accentury.backend.common.ApiException;
import app.accentury.backend.common.ErrorCode;
import app.accentury.backend.common.SsmPlaceholder;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.KeySourceException;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.JWKSourceBuilder;
import com.nimbusds.jose.proc.BadJOSEException;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jose.util.DefaultResourceRetriever;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.text.ParseException;
import java.time.Duration;
import java.util.Set;

/**
 * JWKS로 서명을 검증하는 ID 토큰 (구글과 애플, 명세서 §3.9).
 * <p>
 * 확인하는 것: RS256 서명(IdP가 공개한 키 집합으로), {@code exp}, {@code aud}가 우리 값과 정확히 같음, {@code iss}가
 * 허용 목록 안, {@code sub} 존재. 공개 키 집합은 라이브러리가 캐시하고, 모르는 {@code kid}가 오면 한 번 다시 받는다
 * (IdP의 키 교체).
 * <p>
 * 실패 분류: 키 집합을 받지 못한 것({@link KeySourceException})만 502 {@code AUTH_IDP_UNAVAILABLE}이고, 나머지(형식,
 * 서명, 만료, 대상, 발급자)는 전부 401 {@code AUTH_IDP_TOKEN_INVALID}다.
 */
final class JwksIdTokens {

    private static final Logger log = LoggerFactory.getLogger(JwksIdTokens.class);

    /** JWKS 응답 크기 상한 - 구글과 애플의 키 집합은 수 KB다. */
    private static final int JWKS_SIZE_LIMIT = 64 * 1024;

    private final String idpName;
    private final @Nullable String audience;
    private final Set<String> issuers;
    private final DefaultJWTProcessor<SecurityContext> processor;

    /**
     * @param audience 우리 클라이언트 ID(구글) 또는 번들 ID(애플). null이거나 자리 표시 값이면 이 IdP는 아직 설정 전이라
     *                 모든 토큰을 거절한다 - 아무 aud나 받아 주는 것보다 안전하다.
     */
    JwksIdTokens(String idpName, String jwksUrl, Duration timeout, @Nullable String audience, Set<String> issuers) {
        this.idpName = idpName;
        this.audience = configured(audience) ? audience : null;
        this.issuers = Set.copyOf(issuers);
        int timeoutMs = Math.toIntExact(timeout.toMillis());
        JWKSource<SecurityContext> keys = JWKSourceBuilder
                .<SecurityContext>create(url(jwksUrl), new DefaultResourceRetriever(timeoutMs, timeoutMs, JWKS_SIZE_LIMIT))
                .build();
        this.processor = new DefaultJWTProcessor<>();
        processor.setJWSKeySelector(new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, keys));
        // aud는 정확 일치 하나, iss는 목록이라(구글은 두 표기) 아래 verify에서 따로 본다.
        processor.setJWTClaimsSetVerifier(new DefaultJWTClaimsVerifier<>(
                this.audience != null ? this.audience : "",
                new JWTClaimsSet.Builder().build(),
                Set.of("sub", "iss", "exp", "iat")));
        if (this.audience == null) {
            log.warn("{} 로그인 미설정 - aud로 쓸 값이 없어 모든 토큰을 거절한다", idpName);
        }
    }

    static boolean configured(@Nullable String value) {
        return value != null && !value.isBlank() && !SsmPlaceholder.UNSET.equals(value);
    }

    /**
     * @throws ApiException 401 {@code AUTH_IDP_TOKEN_INVALID} 또는 502 {@code AUTH_IDP_UNAVAILABLE}
     */
    JWTClaimsSet verify(String idToken) {
        if (audience == null) {
            throw new ApiException(ErrorCode.AUTH_IDP_TOKEN_INVALID);
        }
        JWTClaimsSet claims;
        try {
            claims = processor.process(idToken, null);
        } catch (KeySourceException e) {
            log.warn("{} 공개 키 집합(JWKS) 조회 실패 - {}", idpName, e.getClass().getSimpleName());
            throw new ApiException(ErrorCode.AUTH_IDP_UNAVAILABLE);
        } catch (ParseException | BadJOSEException | JOSEException e) {
            throw new ApiException(ErrorCode.AUTH_IDP_TOKEN_INVALID);
        }
        if (!issuers.contains(claims.getIssuer())) {
            throw new ApiException(ErrorCode.AUTH_IDP_TOKEN_INVALID);
        }
        return claims;
    }

    private static URL url(String value) {
        try {
            return URI.create(value).toURL();
        } catch (MalformedURLException | IllegalArgumentException e) {
            throw new IllegalStateException("JWKS 주소가 URL이 아니다: " + value, e);
        }
    }
}
