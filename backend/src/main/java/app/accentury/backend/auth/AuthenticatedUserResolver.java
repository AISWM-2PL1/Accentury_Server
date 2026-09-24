package app.accentury.backend.auth;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;


/**
 * {@link AuthenticatedUser} 인자 풀이 (KAN-223).
 * <p>
 * 필터 대신 인자 리졸버인 것은 보호할 경로가 인자를 선언한 핸들러뿐이라서다 - 경로 목록을 따로 들고 있으면
 * 컨트롤러가 늘 때 목록이 뒤처져 그 경로만 조용히 열린다. 인자를 선언하지 않은 핸들러에는 아무 일도 하지 않는다.
 */
@Component
class AuthenticatedUserResolver implements HandlerMethodArgumentResolver {

    /**
     * 요청 때 꺼낸다 - {@code @WebMvcTest} 슬라이스는 WebMvcConfigurer와 인자 리졸버는 올리지만 서비스 빈은 올리지 않아,
     * 생성자에서 바로 받으면 계정과 무관한 슬라이스 테스트까지 기동이 깨진다.
     */
    private final ObjectProvider<AccountTokens> accountTokens;

    AuthenticatedUserResolver(ObjectProvider<AccountTokens> accountTokens) {
        this.accountTokens = accountTokens;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        if (!parameter.hasParameterAnnotation(AuthenticatedUser.class)) {
            return false;
        }
        if (!AppUser.class.equals(parameter.getParameterType())) {
            throw new IllegalStateException("@AuthenticatedUser는 AppUser 인자에만 붙인다: " + parameter);
        }
        return true;
    }

    @Override
    public AppUser resolveArgument(MethodParameter parameter, @Nullable ModelAndViewContainer mavContainer,
                                   NativeWebRequest webRequest, @Nullable WebDataBinderFactory binderFactory) {
        return accountTokens.getObject().authenticate(webRequest.getHeader(HttpHeaders.AUTHORIZATION));
    }
}
