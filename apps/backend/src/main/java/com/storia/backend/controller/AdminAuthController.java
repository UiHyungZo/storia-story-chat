package com.storia.backend.controller;

import com.storia.backend.admin.AdminAuthInterceptor;
import com.storia.backend.admin.AdminSessionStore;
import com.storia.backend.config.AdminProperties;
import com.storia.backend.dto.AdminLoginRequest;
import com.storia.backend.dto.AdminLoginResponse;
import com.storia.backend.exception.UnauthorizedException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Arrays;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/auth")
@RequiredArgsConstructor
public class AdminAuthController {

    private final AdminProperties adminProperties;
    private final AdminSessionStore adminSessionStore;

    @PostMapping("/login")
    public AdminLoginResponse login(@Valid @RequestBody AdminLoginRequest request, HttpServletResponse response) {
        if (!adminProperties.isConfigured() || !constantTimeEquals(adminProperties.password(), request.password())) {
            throw new UnauthorizedException("비밀번호가 올바르지 않습니다.");
        }

        String token = adminSessionStore.issue();
        response.addHeader(HttpHeaders.SET_COOKIE, buildCookie(token, Duration.ofDays(7)).toString());
        return new AdminLoginResponse(token);
    }

    @PostMapping("/logout")
    public void logout(HttpServletRequest request, HttpServletResponse response) {
        Cookie[] cookies = request.getCookies();
        String token = cookies == null
                ? null
                : Arrays.stream(cookies)
                        .filter(cookie -> AdminAuthInterceptor.SESSION_COOKIE_NAME.equals(cookie.getName()))
                        .map(Cookie::getValue)
                        .findFirst()
                        .orElse(null);
        adminSessionStore.invalidate(token);
        response.addHeader(HttpHeaders.SET_COOKIE, buildCookie("", Duration.ZERO).toString());
    }

    /** 세션 유효성만 확인하는 프로브. AdminAuthInterceptor가 이미 검증했으므로 본문은 필요 없다. */
    @GetMapping("/session")
    public void session() {
    }

    private ResponseCookie buildCookie(String value, Duration maxAge) {
        return ResponseCookie.from(AdminAuthInterceptor.SESSION_COOKIE_NAME, value)
                .httpOnly(true)
                .secure(false)
                .sameSite("Lax")
                .path("/")
                .maxAge(maxAge)
                .build();
    }

    private boolean constantTimeEquals(String expected, String actual) {
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8));
    }
}
