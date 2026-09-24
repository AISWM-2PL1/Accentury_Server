package app.accentury.backend.auth;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/** {@link AuthenticatedUser} 인자 리졸버 등록 (KAN-223). */
@Configuration(proxyBeanMethods = false)
class AuthWebConfig implements WebMvcConfigurer {

    private final AuthenticatedUserResolver resolver;

    AuthWebConfig(AuthenticatedUserResolver resolver) {
        this.resolver = resolver;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(resolver);
    }
}
