# HANDOFF

다음 세션(또는 다른 작업자)이 이 프로젝트를 이어받을 때 필요한 현재 상태 요약. 요구사항 전체는 [`PRD/Storia_PRD_v3.md`](./PRD/Storia_PRD_v3.md), 구조는 [`docs/architecture/README.md`](./docs/architecture/README.md) 참고.

## 현재 상태 (2026-08-24 기준)

PRD v3 마일스톤 **1주차 마무리 갭 코드 작성 완료, 로컬 실행 검증은 아직 안 함** (이번 세션은 docker/서버 기동 없이 코드만 작성).

- **백엔드**: Spring Boot 3.x + JPA 스캐폴딩. `User`/`Character`/`Conversation`/`Message` 엔티티, REST API(`GET /api/characters`, `GET /api/conversations/{characterId}/messages`, **신규** `POST /api/conversations/{characterId}/messages`), 캐릭터 3종 시딩(`CharacterSeeder`), Swagger UI, **신규** `WebConfig`(로컬 개발용 CORS 전체 허용, `/api/**`).
- **클라이언트**: Expo RN 앱의 캐릭터 목록/채팅방 화면이 이제 더미 데이터가 아니라 **실제 백엔드 REST 호출**을 사용하도록 전환됨. `src/api/`(신규): `config.ts`(base URL, Android는 `10.0.2.2` / iOS는 `localhost` 자동 분기), `deviceId.ts`(AsyncStorage 기반 UUID 영속화), `client.ts`(fetch 래퍼, `X-Device-Id` 헤더 자동 부착), `characters.ts`/`conversations.ts`(DTO 매핑). `useCharacterStore`/`useConversationStore`는 `loadCharacters`/`loadMessages`/`sendMessage` 비동기 액션 + `isLoading`/`error` 상태로 재작성. `src/constants/dummyCharacters.ts`는 삭제.
- **DB**: 로컬 MariaDB(`docker-compose.yml`, 포트 3307). PostgreSQL → MariaDB 마이그레이션 완료 (커밋 `395b2ca` 이후).
- **WebSocket/Gemini/WebRTC/TTS**: 전혀 구현 안 됨 (2~6주차 범위).

## 다음 작업 (바로 이어서 할 것)

**이번 세션에서 짠 코드는 실행 검증이 안 됐다** — docker compose up, `gradlew bootRun`, `expo start`를 순서대로 띄워서 캐릭터 목록 조회 → 채팅방 진입 → 메시지 전송 → 히스토리 복원까지 실제로 왕복시켜볼 것. 특히:

- 실기기로 테스트한다면 `apps/client/.env`에 `EXPO_PUBLIC_API_BASE_URL=http://<개발머신 LAN IP>:8080` 설정 필요 (시뮬레이터/에뮬레이터는 자동 분기되지만 실기기는 `localhost` 접근 불가).
- `WebConfig`의 CORS 설정이 실제로 필요했는지(RN 네이티브 fetch는 CORS 영향 없음) 확인 — 불필요하면 제거 검토.
- 백엔드에 전역 예외 처리기가 없어 잘못된 `characterId` 등은 500으로 노출됨 (`docs/api.md` Error Handling Policy 참고) — 이번 갭 작업 범위 밖이라 그대로 둠.

이후 순서는 [`TODO.md`](./TODO.md) 참고 (2주차: Gemini 연동 & WebSocket).

## 중요한 결정 사항 / 함정

- **MariaDB 예약어 회피**: `User` → `app_user` 테이블, `Character` → `story_character` 테이블로 매핑. 새 엔티티 추가 시 MariaDB/MySQL 예약어(`USER`, `CHARACTER`, `GROUP`, `ORDER` 등)와 충돌하는 이름은 `@Table(name = ...)`로 명시적으로 회피할 것.
- **로컬 DB 포트는 3307**: 로컬 머신에 Homebrew mysqld가 이미 `127.0.0.1:3306`을 점유하고 있어서 docker-compose가 `3307:3306`으로 매핑됨. `application.yml`의 JDBC URL도 `localhost:3307` 기준. 다른 환경에서 세팅할 때 3306이 비어있다면 그대로 3307을 써도 되고, 필요하면 포트를 맞춰 바꿔도 무방 (배포 환경에서는 무관).
- **DB 인증 방식**: `jdbc:mariadb://...?allowPublicKeyRetrieval=true&useSSL=false` 옵션은 로컬 개발용. 배포 시 SSL 설정 재검토 필요.
- **디바이스 ID 기반 익명 세션**: 정식 로그인/JWT 없음. `X-Device-Id` 헤더로 유저 식별 (`ConversationController`). 클라이언트에서 디바이스 ID 생성/영속화 로직이 아직 없음 — REST 연동 작업 시 같이 필요.
- **캐릭터당 대화 1개 제약**: `Conversation(user_id, character_id)` UNIQUE — 여러 대화 세션 개념 없음 (PRD상 의도된 범위 밖 항목).
- **음성 통화 기능 범위**: PRD 3.9 참고, B안(RN STT + 서버 LLM/TTS, WebRTC 미사용) + C안(WebRTC 시그널링 최소 데모) 조합이 기본 전략. A안(풀 WebRTC 파이프라인)은 시간 여유 시 확장 목표 — 처음부터 A안으로 설계하지 말 것.

## 로컬 실행

`README.md`의 "로컬 실행" 섹션 참고 (DB → 백엔드 → 클라이언트 순).
