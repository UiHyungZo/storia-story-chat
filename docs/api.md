# API

## Goal

Storia 백엔드는 REST(1주차) → WebSocket(STOMP, 2주차 — 구현됨) → LiveKit 기반 WebRTC 음성 통화("축소판 A안", 5주차 — 구현됨) 순으로 확장된다. 계층 간 책임을 명확히 나눠, 실시간 채널이 추가되어도 기존 REST 경로가 폴백으로 계속 동작하도록 유지한다.

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
* `content`가 비어있으면 `@Valid`(`@NotBlank`) 검증 실패 → `GlobalExceptionHandler`가 400 + `VALIDATION_ERROR`로 응답 (아래 Error Handling Policy 참고)
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

## GET /api/messages/{messageId}/audio

저장된 메시지 내용을 TTS로 합성해 오디오(mp3)로 반환한다(5주차, PRD 3.9 음성 통화 B안). 별도 오디오 저장소 없이 **요청이 올 때마다 그때그때 합성**한다(포트폴리오 스코프 트레이드오프 — 반복 요청 시 매번 재합성 비용 발생, ADR-004 갱신 참고).

```text
GET /api/messages/{messageId}/audio
```

응답: `200 OK` + `Content-Type: audio/mpeg` (성공), 또는 `404 Not Found`(메시지 없음 / `TTS_API_KEY` 미설정 / 합성 실패).

* 구현: `MessageController` → `MessageService#synthesizeAudio` → `TtsService#synthesize`(Google Cloud TTS REST API, WebClient + `.block()`)
* 텍스트 채팅과 달리 `X-Device-Id` 헤더를 요구하지 않음 — `messageId`는 민감하지 않은 순번이고, 이 앱 전체가 정식 인증 없이 동작하는 것과 동일한 신뢰 수준(PRD 9절)
* 클라이언트는 메시지 송수신 자체는 기존 텍스트 채팅 경로(REST `POST /api/conversations/{characterId}/messages`)를 그대로 재사용하고, 응답으로 받은 `assistantMessage.id`로 이 엔드포인트를 호출해 오디오만 별도로 받아온다 — WS 스트리밍 경로는 응답 완료 시점을 기다리지 않으므로 음성 통화에는 쓰지 않음(`useConversationStore#sendMessageViaRest`, `useVoiceCallStore` 참고)

---

## POST /api/calls/{characterId}/token

음성 통화 "축소판 A안"(5주차, PRD 3.9, ADR-004 갱신 2) — LiveKit room 참여용 토큰 발급. `docs/architecture/README.md`의 시퀀스 다이어그램 참고.

```text
POST /api/calls/{characterId}/token
Header: X-Device-Id: <uuid>
```

응답(`200 OK`): `{ "token": "<JWT>", "url": "wss://<livekit-host>", "roomName": "call-<deviceId>-<characterId>" }`. LiveKit 미설정 시(`LIVEKIT_HOST`/`LIVEKIT_API_KEY`/`LIVEKIT_API_SECRET`/`LIVEKIT_EGRESS_AUDIO_WS_URL` 중 하나라도 없으면) `503 Service Unavailable`.

* 구현: `VoiceCallController` → `VoiceCallService#createToken` (`io.livekit:livekit-server`의 `AccessToken` — `RoomJoin`/`RoomName`/`CanPublish(true)`/`CanSubscribe(false)` 그랜트)
* 룸 이름은 `deviceId`+`characterId`로 결정적으로 생성 — 별도 룸 생성 API 호출 없이 클라이언트가 join하는 순간 LiveKit이 자동 생성

## POST /api/calls/{characterId}/turns

클라이언트가 로컬에서 마이크 트랙을 publish한 직후 호출 — 그 트랙의 LiveKit Track Egress를 시작시킨다.

```text
POST /api/calls/{characterId}/turns
Header: X-Device-Id: <uuid>
Content-Type: application/json

{ "roomName": "call-...", "trackSid": "TR_..." }
```

응답(`200 OK`): `{ "turnId": "<uuid>" }`. LiveKit 미설정 시 `503`.

* 구현: `VoiceCallService#startTurn` → `EgressServiceClient#startTrackEgress(roomName, wsUrl, trackSid)` — `wsUrl`은 이 백엔드 자신의 `/egress/audio?turnId=...` (아래 참고)
* 턴 상태는 DB가 아니라 인메모리(`VoiceTurnRegistry`)에서만 관리 — 최종 결과(유저 발화/어시스턴트 응답)만 기존과 동일하게 `Message`로 영속화됨

## GET /api/calls/turns/{turnId}

클라이언트가 마이크 트랙을 unpublish한 뒤 폴링해서 처리 상태를 확인한다.

```text
GET /api/calls/turns/{turnId}
```

응답(`200 OK`): `{ "status": "recording" | "processing" | "done" | "error", "assistantMessageId": number | null, "errorMessage": string | null }`. `done`이면 `assistantMessageId`로 `GET /api/messages/{id}/audio`를 호출해 재생.

* 구현: `VoiceCallController` → `VoiceCallService#getStatus`

---

## WS /egress/audio (STOMP 아님 — raw WebSocket)

LiveKit Track Egress가 접속해오는 전용 엔드포인트. `/ws`(STOMP)와는 별개 핸들러(`VoiceEgressWebSocketConfig`)로 등록됨.

```text
ws://<host>:8080/egress/audio?turnId=<uuid>
```

* LiveKit이 binary 프레임으로 raw PCM(`pcm_s16le`, 보통 48kHz)을 스트리밍 — `VoiceEgressWebSocketHandler`가 `turnId`로 어느 `VoiceTurnSession`에 쌓을지 결정
* 트랙이 unpublish되면 LiveKit이 이 연결을 닫음 → `afterConnectionClosed`에서 `VoiceCallService#completeTurn` 트리거(배치 STT → Gemini → `postAssistantMessage`, `@Async`로 실행)
* 이 WS 연결 자체엔 인증이 없음(LiveKit 자체 제약) — `turnId`(UUID)가 추측 불가능한 값이라는 것으로 최소한의 방어만 함
* **로컬 개발 시**: LiveKit Cloud(원격)가 이 엔드포인트로 다시 접속해야 하므로, `LIVEKIT_EGRESS_AUDIO_WS_URL`은 `localhost`가 아니라 ngrok 등으로 터널링한 공인 주소여야 함 — `HANDOFF.md` 참고

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

`GlobalExceptionHandler`(`@RestControllerAdvice`)가 REST 계층 예외를 일괄 처리한다. 상세 매핑 표와 엔드포인트별 에러 케이스는 [`docs/error-handling.md`](./error-handling.md) 참고.

요약:

* `ResourceNotFoundException` → 404, `IllegalArgumentException`/검증 실패/헤더 누락 → 400, `IllegalStateException`(외부 의존성 실패) → 502, 그 외 미처리 예외 → 500
* 모든 에러 응답은 `ErrorResponse { code, message }` 형태로 통일 (PRD 3.4 로딩/오류/재시도 요구사항과 연결)
* STOMP(`ConversationStompController`)/raw WS(`VoiceEgressWebSocketHandler`) 경로에는 적용되지 않음 — 그쪽은 이미 자체 채널(`StreamEvent.error`, `VoiceTurnSession#fail`)로 에러를 알림

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

# WebRTC / LiveKit Policy (5주차 — 구현됨, "축소판 A안")

PRD 3.9, 3.10, `docs/decisions.md` ADR-004 갱신 2 참고. 원래 계획(B안: WebRTC 미사용 + C안: 별도 WebRTC 최소 데모)에서, 세션 중 리서치 스파이크로 실현 가능성/비용을 확인한 뒤 범위를 넓혀 "축소판 A안"으로 구현함 — 클라이언트↔LiveKit 구간은 실제 WebRTC, 서버는 WebRTC 미디어를 직접 다루지 않고 Track Egress로 오디오만 받아 기존 배치 STT/TTS 파이프라인(B안)에 흘려보냄. 상세 흐름은 `docs/architecture/README.md` 시퀀스 다이어그램, 엔드포인트는 위 `POST /api/calls/...`/`WS /egress/audio` 참고.

* Offer/Answer/ICE 협상은 LiveKit이 대신 처리 — 이 백엔드가 시그널링 프로토콜을 직접 구현하지 않음(자체 WebSocket(STOMP) 채널로 중계하는 방식은 채택 안 함)
* STUN/TURN도 LiveKit(Cloud)이 관리 — 직접 STUN 서버 설정 불필요
* 서버가 합성한 TTS 응답을 다시 WebRTC로 실시간 되쏘는 완전한 양방향(원래 정의의 A안)은 여전히 범위 밖 — 시간 여유 시 확장 목표(`TODO.md` 5주차 "남은 작업" 참고)
* 6주차(C안: WebRTC 시그널링 최소 데모)는 이미 상당 부분 선행 충족됨 — 재평가 예정

---

# Test Policy

API 계층 테스트 방침은 [`docs/testing.md`](./testing.md) 참고.
