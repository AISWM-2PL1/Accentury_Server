package app.accentury.backend.auth;

import app.accentury.backend.PropertiesFixture;
import app.accentury.backend.common.ApiException;
import app.accentury.backend.common.ErrorCode;
import app.accentury.backend.common.RateLimits;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * IdP가 준 생년월일이 만 14세 미만이면 로그인 단계에서 막는다 (Codex 리뷰 P2, 명세서 §3.9) - 계정도 개인 정보도 저장되기
 * 전이다. 저장소와 토큰 발급기를 넘기지 않는 것은 의도다: 거기까지 가면 안 된다 (가면 null이라 이 테스트가 터진다).
 */
class AuthServiceUnderAgeTest {

    @Test
    void IdP_생년월일이_만_14세_미만이면_저장_전에_400이다() {
        LocalDate thirteen = LocalDate.now(UserService.ZONE).minusYears(14).plusDays(1);
        AuthService service = new AuthService(verifiersGiving(thirteen), null, null, null,
                new RateLimits(PropertiesFixture.defaults(), new SimpleMeterRegistry()), null);

        ApiException e = assertThrows(ApiException.class, () -> service.login(
                new LoginRequest("KAKAO", null, "kakao-token", null, null, true, "2026-09-24"), "1.2.3.4"));

        assertEquals(ErrorCode.AUTH_UNDER_AGE, e.code());
    }

    private static IdpVerifiers verifiersGiving(LocalDate birthDate) {
        return new IdpVerifiers(List.of(
                stub(Provider.GOOGLE, birthDate), stub(Provider.KAKAO, birthDate),
                stub(Provider.NAVER, birthDate), stub(Provider.APPLE, birthDate)), false);
    }

    private static IdpVerifier stub(Provider provider, LocalDate birthDate) {
        return new IdpVerifier() {
            @Override
            public Provider provider() {
                return provider;
            }

            @Override
            public IdpProfile verify(IdpCredential credential) {
                return new IdpProfile(provider, "child-sub", null, null, birthDate, null, null, null);
            }
        };
    }
}
