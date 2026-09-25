package app.accentury.backend.auth;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 컨트롤러 인자에 붙이면 {@code Authorization: Bearer {accessToken}}을 검증해 살아 있는 {@link AppUser}를 넣어 준다
 * (명세서 §2.1). 토큰이 없거나 틀리거나 계정이 없으면 핸들러가 불리기 전에 401 {@code AUTH_TOKEN_INVALID}다.
 * <p>
 * 인자 타입은 {@link AppUser}여야 한다 ({@link AuthenticatedUserResolver}).
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AuthenticatedUser {
}
