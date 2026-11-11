package app.accentury.backend.auth;

import app.accentury.backend.common.AccenturyProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
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

    private static final Logger log = LoggerFactory.getLogger(AuthConfig.class);

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

    /**
     * 탈퇴한 카카오, 네이버 계정의 연결 해제를 돌리는 전용 실행기 (KAN-343, PR #70 리뷰).
     * <p>
     * 연결 해제는 제공자 호출이 최대 수십 초라 탈퇴 요청 스레드에서 기다리지 않는다 ({@link IdpUnlinker}). 워커 1~2개는
     * 탈퇴가 드문 사건이라서다. 큐가 넘치거나 종료 중이면 <b>버리고 WARN을 남긴다</b> - 연결 해제는 최선 동작이라 거절
     * 예외를 받을 곳이 없고(탈퇴는 이미 커밋됐다), 무한 큐로 받으면 제공자 장애가 backend 메모리 문제로 번진다. 버려진
     * 계정의 연결은 사용자가 제공자의 설정에서 끊을 수 있다. 종료 때는 돌던 것과 큐에 든 것을 10초까지 기다린다.
     * <p>
     * 이름으로만 주입한다 ({@code @Qualifier}) - 이 앱에는 용도가 다른 실행기가 여럿 있다 ({@code FeedbackNotifyConfig}).
     */
    @Bean
    ThreadPoolTaskExecutor idpUnlinkExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("idp-unlink-");
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(100);
        executor.setRejectedExecutionHandler((task, pool) ->
                log.warn("제공자 연결 해제 작업을 버렸다 - 큐 포화 또는 종료 중 (queued={})", pool.getQueue().size()));
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        return executor;
    }

    /** 탈퇴한 카카오, 네이버 계정의 연결 해제 (KAN-343). 설정이 없으면 기동 때 WARN 한 번이고, 탈퇴는 연결 해제 없이 성공한다. */
    @Bean
    IdpUnlinker idpUnlinker(AccenturyProperties properties, ObjectMapper objectMapper) {
        return new IdpUnlinker(properties.auth(), properties.share().kakaoAdminKey(), objectMapper);
    }

    /** 탈퇴한 애플 계정의 토큰 revoke (KAN-241). 설정이 없으면 기동 때 WARN 한 번이고, 탈퇴는 revoke 없이 성공한다. */
    @Bean
    AppleTokenRevoker appleTokenRevoker(AccenturyProperties properties, ObjectMapper objectMapper) {
        return new AppleTokenRevoker(properties.auth(), objectMapper);
    }
}
