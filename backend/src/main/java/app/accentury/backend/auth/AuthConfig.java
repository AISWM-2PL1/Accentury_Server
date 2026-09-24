package app.accentury.backend.auth;

import app.accentury.backend.common.AccenturyProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

/**
 * IdP 검증기 조립 (KAN-223).
 * <p>
 * 검증기는 빈으로 두지 않고 여기서 한 번에 만든다 - 테스트는 {@link IdpVerifiers} 빈 하나를 가짜로 바꾸면 되고,
 * IdP별 설정(주소, 타임아웃, aud)은 전부 {@link AccenturyProperties.Auth} 한 곳에서 온다.
 */
@Configuration(proxyBeanMethods = false)
class AuthConfig {

    /** 배포 프로파일 이름 - {@code DeploymentConfigGuard.PROFILE}과 같은 값이다 (패키지가 달라 문자열로 둔다). */
    static final String DEPLOY_PROFILE = "deploy";

    @Bean
    IdpVerifiers idpVerifiers(AccenturyProperties properties, ObjectMapper objectMapper, Environment environment) {
        AccenturyProperties.Auth auth = properties.auth();
        if (auth.fakeIdp() && environment.acceptsProfiles(Profiles.of(DEPLOY_PROFILE))) {
            // 켜진 채 배포되면 fake:<아무 sub>로 누구의 계정에든 들어갈 수 있다. 조용히 끄지 않고 세운다 -
            // 설정이 잘못 들어간 것을 모르고 지나가면 다음 배포에서 다시 켜진다.
            throw new IllegalStateException("accentury.auth.fake-idp는 배포 프로파일(" + DEPLOY_PROFILE + ")에서 켤 수 없다");
        }
        return new IdpVerifiers(List.of(
                new GoogleIdpVerifier(auth),
                new AppleIdpVerifier(auth),
                new KakaoIdpVerifier(auth, objectMapper),
                new NaverIdpVerifier(auth, objectMapper)), auth.fakeIdp());
    }
}
