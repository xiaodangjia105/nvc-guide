package nvc.guide.common.config;

import lombok.RequiredArgsConstructor;
import nvc.guide.common.trace.HttpTraceInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final HttpTraceInterceptor httpTraceInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(httpTraceInterceptor)
            .addPathPatterns("/api/**")
            .excludePathPatterns(
                "/api/swagger-ui/**",
                "/api/v3/api-docs/**",
                "/api/actuator/**"
            );
    }
}
