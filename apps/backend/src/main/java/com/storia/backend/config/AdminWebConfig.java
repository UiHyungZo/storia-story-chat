package com.storia.backend.config;

import com.storia.backend.admin.AdminAuthInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 운영 콘솔 인증 게이트. CORS를 다루는 {@link WebConfig}와는 관심사가 달라 별도 클래스로 둔다.
 * 로그인 자체는 세션이 없는 상태에서 호출돼야 하므로 인터셉터 대상에서 제외한다.
 */
@Configuration
@RequiredArgsConstructor
public class AdminWebConfig implements WebMvcConfigurer {

    private final AdminAuthInterceptor adminAuthInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(adminAuthInterceptor)
                .addPathPatterns("/api/admin/**")
                .excludePathPatterns("/api/admin/auth/login");
    }
}
