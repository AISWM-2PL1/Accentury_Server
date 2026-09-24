package app.accentury.backend.auth;

import app.accentury.backend.PropertiesFixture;
import app.accentury.backend.common.AccenturyProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 가짜 IdP 스위치의 배포 차단 (KAN-223 요구 6) - 켜진 채 배포되면 {@code fake:<아무 sub>}로 누구의 계정에든 들어간다.
 */
class AuthConfigTest {

    @Test
    void 가짜_IdP를_배포_프로파일에서_켜면_기동이_실패한다() {
        MockEnvironment deploy = new MockEnvironment();
        deploy.setActiveProfiles("deploy");

        assertThrows(IllegalStateException.class,
                () -> new AuthConfig().idpVerifiers(withFakeIdp(true), JsonMapper.builder().build(), deploy));
    }

    @Test
    void 로컬에서는_가짜_IdP를_켤_수_있고_배포에서도_꺼져_있으면_뜬다() {
        MockEnvironment deploy = new MockEnvironment();
        deploy.setActiveProfiles("deploy");

        assertDoesNotThrow(() -> new AuthConfig().idpVerifiers(withFakeIdp(true), JsonMapper.builder().build(),
                new MockEnvironment()));
        assertDoesNotThrow(() -> new AuthConfig().idpVerifiers(withFakeIdp(false), JsonMapper.builder().build(), deploy));
    }

    private static AccenturyProperties withFakeIdp(boolean fakeIdp) {
        AccenturyProperties base = PropertiesFixture.defaults();
        AccenturyProperties.Auth a = base.auth();
        AccenturyProperties.Auth auth = new AccenturyProperties.Auth(a.jwtSecret(), a.issuer(), a.accessTokenTtl(),
                a.refreshTokenTtl(), a.rateLimitPerMinute(), fakeIdp, a.googleClientId(), a.appleBundleId(),
                a.kakaoAppId(), a.googleJwksUrl(), a.appleJwksUrl(), a.kakaoApiBaseUrl(), a.naverApiBaseUrl(),
                Duration.ofSeconds(5));
        return new AccenturyProperties(base.session(), base.analysis(), base.upload(), base.vocab(), base.completion(),
                base.cors(), base.result(), base.analytics(), base.admin(), base.share(), base.feedback(),
                base.training(), auth, base.trustedProxies());
    }
}
