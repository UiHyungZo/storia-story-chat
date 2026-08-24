# API

## Goal

Storia 백엔드는 REST(1주차) → WebSocket(STOMP, 2주차 — 구현됨) → WebRTC 시그널링(6주차) 순으로 확장된다. 계층 간 책임을 명확히 나눠, 실시간 채널이 추가되어도 기존 REST 경로가 폴백으로 계속 동작하도록 유지한다.

목표:

* Controller는 HTTP/DTO 처리만, 비즈니스 로직은 Service, 영속성은 Repository로 분리
* 엔티티를 API 응답으로 직접 노출하지 않고 DTO(record)로 변환
* 디바이스 ID 기반 익명 세션 — 정식 인증 없이 유저 식별
* WebSocket 실패 시 REST 폴백 경로 유지 (PRD 3.3)

---

# Base URL

로컬 개발 기준:

```text
http://localhost:8080
```

Swagger UI:

```text
http://localhost:8080/swagger-ui.html
```

---

# REST Endpoints (구현됨)

## GET /api/characters

캐릭터 목록 조회.

```text
GET /api/characters
```

응답 (`CharacterResponse[]`):

```json
[
  {
    "id": 1,
    "name": "아리아",
    "concept": "우주선 항법 AI가 인격을 얻어가는 SF 세계관",
    "avatarUrl": "...",
    "ttsVoiceId": "..."
  }
]
```

* 구현: `CharacterController` → `CharacterService#findAll` → `CharacterRepository`
* 인증/헤더 불필요 (캐릭터 목록은 공개 데이터)

---

## GET /api/conversations/{characterId}/messages

특정 캐릭터와의 대화 히스토리 조회. 대화가 없으면 조회 시점에 생성한다(get-or-create).

```text
GET /api/conversations/{characterId}/messages
Header: X-Device-Id: <uuid>
```

응답 (`MessageResponse[]`, `createdAt` 오름차순):

```json
[
  { "id": 1, "role": "USER", "content": "안녕", "createdAt": "2026-08-24T10:00:00Z" },
  { "id": 2, "role": "ASSISTANT", "content": "안녕하세요", "createdAt": "2026-08-24T10:00:01Z" }
]
```

* 구현: `ConversationController` → `ConversationService#getMessages`
* `X-Device-Id` 헤더로 `app_user`를 조회하고, 없으면 새로 생성
* `role`은 `Message.Role` enum(`USER`/`ASSISTANT`)의 문자열 표현

---

## POST /api/conversations/{characterId}/messages

유저 메시지 저장 + Gemini 호출. **WebSocket 스트리밍이 실패했을 때 클라이언트가 쓰는 REST 폴백 경로**라서, 스트리밍 없이 Gemini 응답 전체를 기다렸다가 유저 메시지와 함께 한 번에 반환한다.

```text
POST /api/conversations/{characterId}/messages
Header: X-Device-Id: <uuid>
Content-Type: application/json

{ "content": "안녕" }
```

응답 (`201 Created`, `MessageExchangeResponse`):

```json
{
  "userMessage": { "id": 3, "role": "USER", "content": "안녕", "createdAt": "2026-08-24T10:00:02Z" },
  "assistantMessage": { "id": 4, "role": "ASSISTANT", "content": "안녕하세요!", "createdAt": "2026-08-24T10:00:03Z" }
}
```

* 구현: `ConversationController` → `ConversationService#postMessage`/`postAssistantMessage` + `GeminiService#streamReply`(`.block()`으로 동기화, ADR-006 참고)
* `content`가 비어있으면 `@Valid`(`@NotBlank`) 검증 실패 — 단, 전역 예외 처리기가 없어 현재는 500으로 노출됨 (아래 Error Handling Policy 참고)
* `GEMINI_API_KEY`가 설정되지 않았거나 Gemini 호출이 실패하면 고정 안내 문구("죄송해요, 지금은 답변을 생성할 수 없어요.")로 대체 — 500을 던지지 않음

---

## PUT /api/devices/token

FCM 디바이스 토큰 등록(4주차). 새 어시스턴트 메시지가 저장될 때마다 `ConversationService#postAssistantMessage`가 이 토큰으로 푸시를 보낸다 (`PushNotificationService`).

```text
PUT /api/devices/token
Header: X-Device-Id: <uuid>
Content-Type: application/json

{ "token": "<fcm-device-token>" }
```

응답: `204 No Content`.

* 구현: `DeviceController` → `UserService#updateFcmToken` (`app_user.fcmToken`에 저장, 없으면 유저 생성)
* `FIREBASE_CREDENTIALS_PATH`(서비스 계정 JSON 경로) 미설정 시 `PushNotificationService`가 조용히 no-op — 토큰은 저장되지만 실제 푸시는 발송되지 않음
* 클라이언트가 실제 FCM 토큰을 발급받으려면 `@react-native-firebase` SDK + `GoogleService-Info.plist`가 필요한데, 이 프로젝트용 Firebase 프로젝트가 아직 없어 클라이언트 쪽 연동은 보류 상태 (`HANDOFF.md` 참고). 백엔드 경로만 먼저 구현됨.

---

# Layer Flow

```text
Client (RN)
→ Controller (@RestController)
→ Service (@Service, @Transactional)
→ Repository (Spring Data JPA)
→ Entity
→ MariaDB
```

응답 방향은 역순이며, Service → Controller 경계에서 Entity를 DTO(`XxxResponse.from(entity)`)로 변환한다.

규칙:

* Controller에서 Repository 직접 호출 금지
* Entity를 `@RestController` 메서드 반환 타입으로 사용 금지 — 반드시 DTO 경유
* DTO는 `record` + static factory(`from`) 패턴 유지

---

# Device ID Policy

정식 로그인 없이 `X-Device-Id` 헤더로 유저를 식별한다 (`app_user.deviceId` UNIQUE).

규칙:

* 클라이언트는 최초 실행 시 UUID를 생성해 AsyncStorage에 영속화하고, 이후 모든 요청에 동일 값을 헤더로 전송해야 한다 (`apps/client/src/api/deviceId.ts`, `client.ts`에서 구현)
* 서버는 `deviceId`가 없으면 새 `app_user`를 즉시 생성한다 (`ConversationService#getOrCreateConversation`) — 별도 회원가입 절차 없음
* 캐릭터당 대화는 `(user_id, character_id)` UNIQUE 제약으로 1개만 허용

---

# Error Handling Policy

## 현재 상태

전역 예외 처리기(`@RestControllerAdvice`)가 없다. `ConversationService`에서 존재하지 않는 캐릭터 ID 조회 시 `IllegalArgumentException`을 던지며, 이는 처리되지 않은 500 에러로 그대로 노출된다.

TODO:

* `@RestControllerAdvice` 기반 전역 예외 처리기 추가
* 존재하지 않는 리소스 → 404, 잘못된 요청 → 400으로 매핑
* 클라이언트에는 에러 코드 + 사용자용 메시지를 함께 내려줄 것 (PRD 3.4 로딩/오류/재시도 요구사항과 연결)

---

# WebSocket Policy (2주차 — 구현됨)

PRD 3.3, `docs/architecture/README.md` "목표 아키텍처" 참고.

```text
STOMP endpoint: ws://<host>:8080/ws  (SockJS 없음 — RN 네이티브 클라이언트 대상)
Client --SEND--> /app/conversation/{characterId}/send   { "content": "..." }, Header: X-Device-Id
Server --publish--> /topic/conversation/{characterId}   StreamEvent (CHUNK* → DONE | ERROR)
```

`StreamEvent`:

```json
{ "type": "CHUNK", "content": "안녕" }
{ "type": "DONE", "messageId": 4, "content": "안녕하세요! 무엇을 도와드릴까요?", "createdAt": "2026-08-24T10:00:03Z" }
{ "type": "ERROR", "content": "응답 생성에 실패했습니다: ..." }
```

* 구현: `WebSocketConfig`(STOMP 브로커 등록, `/topic` 심플 브로커 + `/app` prefix) → `ConversationStompController`(`@MessageMapping("/conversation/{characterId}/send")`) → `GeminiService#streamReply`(WebClient + SSE) → `SimpMessagingTemplate`으로 발행
* 클라이언트 헤더(`X-Device-Id`)는 STOMP SEND 프레임의 네이티브 헤더로 전송하고, 서버는 `@Header("X-Device-Id")`로 읽는다 (CONNECT 프레임 헤더가 아니라 매 SEND마다 개별 헤더로 보냄 — 세션 속성 전파에 의존하지 않기 위함)
* 유저 메시지는 스트리밍 시작 전에 먼저 저장(`ConversationService#postMessage`)하고, 스트림이 끝나면 누적된 전체 텍스트를 어시스턴트 메시지로 저장(`postAssistantMessage`)한 뒤 `DONE` 이벤트로 실제 `messageId`/`createdAt`을 전달
* 연결 실패/타임아웃 시 클라이언트는 REST(`POST /api/conversations/{characterId}/messages`)로 폴백 — 채팅방 진입 시 최초 연결은 1회만 시도(실패하면 그 화면 세션 동안 REST만 사용)하지만, **일단 연결된 뒤 끊기는 경우(3주차 구현)** 클라이언트가 stompjs의 지수 백오프로 자동 재연결하고 재연결마다 구독을 다시 검. 재연결 대기 중 전송은 그때그때 REST로 개별 폴백되고, 재연결되면 다음 전송부터 다시 스트리밍 (`apps/client/src/api/websocket.ts`)

---

# WebRTC Signaling Policy (6주차 — 미구현)

PRD 3.9, 3.10 참고. B안(WebRTC 미사용, RN STT + 서버 TTS 오디오 URL) + C안(WebRTC 최소 데모) 조합이 기본 전략이며, 처음부터 풀 WebRTC 파이프라인(A안)으로 설계하지 않는다.

계획:

* Offer/Answer/ICE Candidate 교환은 기존 WebSocket(STOMP) 채널로 중계 (별도 시그널링 프로토콜 신설 안 함)
* STUN은 공개 Google STUN 서버 사용, TURN은 범위 밖 (PRD 9절)

---

# Test Policy

API 계층 테스트 방침은 [`docs/testing.md`](./testing.md) 참고.
