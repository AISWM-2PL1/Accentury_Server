package app.accentury.backend.auth;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 컨트롤러 인자에 붙이면 {@code Authorization: Bearer {accessToken}}을 검증해 살아 있는 {@link AppUser}를 넣어 준다
 * (명세서 §2.1). 토큰이 없거나 틀리거나 계정이 없으면 핸들러가 불리기 전에 401 {@code AUTH_TOKEN_INVALID}다.
 * 게스트 계정은 {@link #allowGuest()}를 켠 핸들러만 부를 수 있다.
 * <p>
 * 인자 타입은 {@link AppUser}여야 한다 ({@link AuthenticatedUserResolver}).
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AuthenticatedUser {

    /**
     * 게스트 계정(KAN-329, 명세서 §3.21)도 받는가. 기본은 받지 않는다 - 게스트가 이 값이 false인 핸들러를 부르면 403
     * {@code AUTH_GUEST_NOT_ALLOWED}다. 기본을 막는 쪽으로 둔 것은 새 API를 더할 때 아무 표시가 없으면 로그인 계정
     * 전용이 되게 하려는 것이다: 반대로 두면 표시를 잊은 API가 게스트에게 조용히 열린다.
     */
    boolean allowGuest() default false;
}
