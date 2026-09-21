package com.storia.backend.dto;

/**
 * 세션 토큰을 응답 바디로도 내려준다. Next.js 서버(Node fetch)가 멀티밸류 Set-Cookie 헤더를
 * 파싱하는 대신 이 값을 그대로 자신의 httpOnly 쿠키에 담기 위함 (apps/admin/src/lib 참고).
 */
public record AdminLoginResponse(String token) {
}
