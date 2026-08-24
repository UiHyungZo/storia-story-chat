# HANDOFF

다음 세션(또는 다른 작업자)이 이 프로젝트를 이어받을 때 필요한 현재 상태 요약. 요구사항 전체는 [`PRD/Storia_PRD_v3.md`](./PRD/Storia_PRD_v3.md), 구조는 [`docs/architecture/README.md`](./docs/architecture/README.md) 참고.

## 현재 상태 (2026-08-24 기준)

PRD v3 마일스톤 **1~2주차 코드 작성 완료, 로컬 실행 검증은 아직 안 함** (이번 세션들은 docker/서버 기동 없이 코드만 작성).

- **백엔드**: Spring Boot 3.x + JPA. `User`/`Character`/`Conversation`/`Message` 엔티티, 캐릭터 3종 시딩(`CharacterSeeder`), Swagger UI, `WebConfig`(로컬 개발용 CORS 전체 허용, `/api/**`).
  - REST: `GET /api/characters`, `GET /api/conversations/{characterId}/messages`, `POST /api/conversations/{characterId}/messages`(유저 메시지 저장 + Gemini 동기 호출로 어시스턴트 응답까지 한 번에 반환 — WS 실패 시 폴백 경로).
  - **신규(2주차)**: WebSocket(STOMP) — `WebSocketConfig`(`/ws` 엔드포인트, SockJS 없이 raw STOMP), `ConversationStompController`(`/app/conversation/{characterId}/send` 수신 → Gemini 스트리밍 청크를 `/topic/conversation/{characterId}`로 발행). `GeminiService`가 WebClient(SSE)로 Gemini `streamGenerateContent` 호출 — `GEMINI_API_KEY` 환경변수 필요, 없으면 WS는 ERROR 이벤트, REST는 고정 안내 문구로 우아하게 저하됨.
- **클라이언트**: 캐릭터 목록/채팅방 화면이 실제 백엔드를 호출. `src/api/`: `config.ts`(base URL 자동 분기), `deviceId.ts`(AsyncStorage UUID), `client.ts`(fetch 래퍼), `characters.ts`/`conversations.ts`(REST DTO 매핑), **신규** `websocket.ts`(`@stomp/stompjs` 기반 STOMP 클라이언트, 연결/구독/발행/해제). `useConversationStore`는 화면 진입 시 WS 연결을 1회 시도해 성공하면 스트리밍(`streamingByCharacterId`로 타이핑 효과 렌더링), 실패하면 그 세션 동안 REST(`postMessage`, 논스트리밍 전체 응답)로 전환. `ChatRoomScreen`은 화면 이탈 시 소켓을 정리(`disconnect`).
- **DB**: 로컬 MariaDB(`docker-compose.yml`, 포트 3307). PostgreSQL → MariaDB 마이그레이션 완료 (커밋 `395b2ca` 이후).
- **WebRTC/TTS**: 전혀 구현 안 됨 (5~6주차 범위).

## 다음 작업 (바로 이어서 할 것)

**아직 실행 검증이 안 됐다** — docker compose up, `GEMINI_API_KEY` 환경변수 설정 후 `gradlew bootRun`, `expo start`를 순서대로 띄워서 다음을 확인할 것:

- 캐릭터 목록 조회 → 채팅방 진입 → 메시지 전송 → 스트리밍 타이핑 효과 → 히스토리 복원까지 왕복.
- 백엔드를 내려서 WS 연결 실패를 강제한 뒤 REST 폴백(전체 응답 한 번에 반환)이 동작하는지.
- `@stomp/stompjs`가 RN(Hermes)에서 폴리필 없이 붙는지 (문제 시 `TextEncoder`/`TextDecoder` 폴리필 검토).
- 실기기 테스트라면 `apps/client/.env`에 `EXPO_PUBLIC_API_BASE_URL=http://<개발머신 LAN IP>:8080` 설정 필요 (WebSocket URL도 이 값에서 `http`→`ws`로 자동 치환됨).
- 백엔드에 전역 예외 처리기가 없어 잘못된 `characterId` 등은 500으로 노출됨 (`docs/api.md` Error Handling Policy 참고) — 계속 범위 밖으로 남겨둠.
- WS 스트리밍 도중 연결이 끊기는 시나리오의 재연결/재시도 로직은 3주차 범위로 남겨둠 (현재는 화면 진입 시 1회만 연결 시도).

이후 순서는 [`TODO.md`](./TODO.md) 참고 (3주차: 안정성 & 동기화 — 로딩/오류/재시도 UI, WebSocket 재연결).

## 중요한 결정 사항 / 함정

- **MariaDB 예약어 회피**: `User` → `app_user` 테이블, `Character` → `story_character` 테이블로 매핑. 새 엔티티 추가 시 MariaDB/MySQL 예약어(`USER`, `CHARACTER`, `GROUP`, `ORDER` 등)와 충돌하는 이름은 `@Table(name = ...)`로 명시적으로 회피할 것.
- **로컬 DB 포트는 3307**: 로컬 머신에 Homebrew mysqld가 이미 `127.0.0.1:3306`을 점유하고 있어서 docker-compose가 `3307:3306`으로 매핑됨. `application.yml`의 JDBC URL도 `localhost:3307` 기준. 다른 환경에서 세팅할 때 3306이 비어있다면 그대로 3307을 써도 되고, 필요하면 포트를 맞춰 바꿔도 무방 (배포 환경에서는 무관).
- **DB 인증 방식**: `jdbc:mariadb://...?allowPublicKeyRetrieval=true&useSSL=false` 옵션은 로컬 개발용. 배포 시 SSL 설정 재검토 필요.
- **디바이스 ID 기반 익명 세션**: 정식 로그인/JWT 없음. `X-Device-Id` 헤더로 유저 식별 (`ConversationController`). 클라이언트에서 디바이스 ID 생성/영속화 로직이 아직 없음 — REST 연동 작업 시 같이 필요.
- **캐릭터당 대화 1개 제약**: `Conversation(user_id, character_id)` UNIQUE — 여러 대화 세션 개념 없음 (PRD상 의도된 범위 밖 항목).
- **음성 통화 기능 범위**: PRD 3.9 참고, B안(RN STT + 서버 LLM/TTS, WebRTC 미사용) + C안(WebRTC 시그널링 최소 데모) 조합이 기본 전략. A안(풀 WebRTC 파이프라인)은 시간 여유 시 확장 목표 — 처음부터 A안으로 설계하지 말 것.
- **Gemini API 키**: `GEMINI_API_KEY` 환경변수로 주입(`application.yml`의 `gemini.api-key`). 커밋된 파일에는 키가 없음 — 로컬에서 `export GEMINI_API_KEY=...` 하고 백엔드를 띄울 것. 모델명은 `gemini.model`(기본 `gemini-2.0-flash`)로 분리해뒀으니 모델이 바뀌면 `application.yml`만 수정하면 됨.
- **WebClient는 MVC 앱에 부분 도입**: `spring-boot-starter-webflux`(전체 리액티브 스택) 대신 `spring-webflux` + `reactor-netty-http`만 추가해 WebClient만 사용. 앱은 여전히 Servlet(MVC) 스택 — REST 폴백 컨트롤러에서는 `.block()`으로 동기 변환해서 씀 (포트폴리오 스코프에서 허용 가능한 트레이드오프, `docs/decisions.md` ADR-006 참고).

## 로컬 실행

`README.md`의 "로컬 실행" 섹션 참고 (DB → 백엔드 → 클라이언트 순).
