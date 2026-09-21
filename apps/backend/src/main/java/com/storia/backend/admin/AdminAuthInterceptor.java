package com.storia.backend.admin;

import com.storia.backend.exception.UnauthorizedException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Arrays;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/** {@code /api/admin/**} 요청에 유효한 ADMIN_SESSION 쿠키가 있는지 검사한다 (로그인 엔드포인트는 제외, AdminWebConfig 참고). */
@Component
@RequiredArgsConstructor
public class AdminAuthInterceptor implements HandlerInterceptor {

    public static final String SESSION_COOKIE_NAME = "ADMIN_SESSION";

    private final AdminSessionStore adminSessionStore;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Cookie[] cookies = request.getCookies();
        String token = cookies == null
                ? null
                : Arrays.stream(cookies)
                        .filter(cookie -> SESSION_COOKIE_NAME.equals(cookie.getName()))
                        .map(Cookie::getValue)
                        .findFirst()
                        .orElse(null);

        if (!adminSessionStore.isValid(token)) {
            throw new UnauthorizedException("인증이 필요합니다.");
        }
        return true;
    }
}
