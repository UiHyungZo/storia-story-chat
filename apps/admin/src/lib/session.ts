/**
 * 이 앱의 httpOnly 쿠키 이름. 백엔드가 Swagger UI 테스트용으로 세팅하는 ADMIN_SESSION
 * 쿠키와는 별개 — Next.js 서버는 로그인 응답 바디의 토큰 값을 여기 담아 자체 보관한다.
 */
export const ADMIN_SESSION_COOKIE = "admin_session";
