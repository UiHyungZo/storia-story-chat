# Error Handling

REST 계층(`@RestController`)의 전역 예외 처리 정책. 구현: `com.storia.backend.exception.GlobalExceptionHandler`(`@RestControllerAdvice`).

## 적용 범위

`@RestControllerAdvice`는 `@RestController`가 던진 예외만 잡는다. 이 앱엔 REST 말고 두 개의 실시간 채널이 더 있는데, 그쪽은 이 핸들러가 관여하지 않고 각자 자체 에러 통지 메커니즘을 쓴다:

| 채널 | 에러 통지 방법 |
|---|---|
| REST (`@RestController`) | 이 문서의 `GlobalExceptionHandler` |
| STOMP (`ConversationStompController`) | `StreamEvent.error(message)`를 `/topic/conversation/{characterId}`로 발행 |
| Raw WS Egress (`VoiceEgressWebSocketHandler`) | `VoiceTurnSession#fail(message)` → 클라이언트가 `GET /api/calls/turns/{turnId}` 폴링 시 `status: "error"`로 확인 |

## 공통 응답 형태

```json
{ "code": "NOT_FOUND", "message": "Character not found: 999" }
```

`code`는 클라이언트가 분기 처리할 안정적인 문자열, `message`는 그대로 사용자에게 보여줘도 되는 문구(현재는 한국어 원문 그대로 노출 — 별도 다국어 처리 없음).

## 매핑 표

| 예외 | HTTP 상태 | `code` | 발생 조건 |
|---|---|---|---|
| `ResourceNotFoundException` | 404 Not Found | `NOT_FOUND` | 존재하지 않는 `characterId` 등 리소스 조회 실패 (`ConversationService#findCharacterOrThrow`) |
| `MethodArgumentNotValidException` | 400 Bad Request | `VALIDATION_ERROR` | `@Valid @RequestBody`의 Bean Validation 실패 (예: `MessageRequest.content` `@NotBlank`) |
| `MissingRequestHeaderException` | 400 Bad Request | `MISSING_HEADER` | 필수 `@RequestHeader`(주로 `X-Device-Id`) 누락 |
| `IllegalArgumentException` | 400 Bad Request | `BAD_REQUEST` | 위 세 가지에 안 걸리는 그 외 잘못된 인자 (현재 코드베이스엔 실사용처 없음 — 안전망) |
| `IllegalStateException` | 502 Bad Gateway | `UPSTREAM_ERROR` | 외부 의존성 호출 실패 — 예: `VoiceCallService#startTurn`의 LiveKit Egress 요청 실패 |
| 그 외 모든 `Exception` | 500 Internal Server Error | `INTERNAL_ERROR` | 예상 못 한 서버 오류 (스택트레이스는 서버 로그에만 남기고, 응답 바디엔 노출 안 함) |

## 엔드포인트별 에러 케이스

### `GET /api/conversations/{characterId}/messages`

| 상황 | 상태 | `code` |
|---|---|---|
| `X-Device-Id` 헤더 누락 | 400 | `MISSING_HEADER` |
| 존재하지 않는 `characterId` | 404 | `NOT_FOUND` |

### `POST /api/conversations/{characterId}/messages`

| 상황 | 상태 | `code` |
|---|---|---|
| `X-Device-Id` 헤더 누락 | 400 | `MISSING_HEADER` |
| `content`가 빈 문자열/공백 | 400 | `VALIDATION_ERROR` |
| 존재하지 않는 `characterId` | 404 | `NOT_FOUND` |
| `GEMINI_API_KEY` 미설정 / Gemini 호출 실패 | **200**(에러 아님) | — `GeminiService#streamReply`가 `IllegalStateException`을 던져도 `ConversationController`가 `.onErrorReturn(...)`으로 흡수하고 고정 안내 문구로 대체. 컨트롤러까지 예외가 도달하지 않음 |

### `PUT /api/devices/token`

| 상황 | 상태 | `code` |
|---|---|---|
| `X-Device-Id` 헤더 누락 | 400 | `MISSING_HEADER` |
| `token`이 빈 문자열/공백 | 400 | `VALIDATION_ERROR` |

### `GET /api/messages/{messageId}/audio`

| 상황 | 상태 | `code` |
|---|---|---|
| 존재하지 않는 `messageId` / `TTS_API_KEY` 미설정 / 합성 실패 | 404 | — `MessageService#synthesizeAudio`가 `null`을 리턴하면 컨트롤러가 직접 `ResponseEntity.notFound()`를 만듦(예외 아님, `GlobalExceptionHandler` 관여 안 함) |

### `POST /api/calls/{characterId}/token`, `POST /api/calls/{characterId}/turns`

| 상황 | 상태 | `code` |
|---|---|---|
| `X-Device-Id` 헤더 누락 | 400 | `MISSING_HEADER` |
| LiveKit 미설정(`LIVEKIT_*` 환경변수 부재) | 503 | — 컨트롤러가 `voiceCallService.isConfigured()`를 직접 체크해 `ResponseEntity.status(503)`을 리턴(예외 아님) |
| `roomName`/`trackSid` 빈 값 (`turns`만 해당) | 400 | `VALIDATION_ERROR` |
| LiveKit Egress 요청 실패 (`startTurn`) | 502 | `UPSTREAM_ERROR` |

### `GET /api/calls/turns/{turnId}`

예외를 던지지 않는다 — 알 수 없는 `turnId`도 `TurnStatusResponse("error", null, "알 수 없는 통화 턴입니다.")`를 `200 OK`로 반환한다(`VoiceCallService#getStatus`). 폴링 엔드포인트라 HTTP 레벨 에러보다 상태 필드로 표현하는 쪽을 택함.

### `GET /api/characters`

에러 케이스 없음 — 인증/파라미터 없는 단순 조회.

## 설계 메모

* **`IllegalArgumentException` vs `ResourceNotFoundException`**: 기존엔 "리소스 없음"에도 범용 `IllegalArgumentException`을 썼는데, 이러면 전역 핸들러에서 400과 404를 구분할 방법이 없다. 그래서 "존재하지 않는 리소스" 전용으로 `ResourceNotFoundException`을 새로 만들고 `ConversationService`가 이걸 던지도록 바꿨다. `IllegalArgumentException` 매핑은 향후 실제 "잘못된 인자" 케이스가 생길 때를 위한 안전망으로 남겨둠.
* **`IllegalStateException` → 502**: `GeminiService`도 같은 예외 타입을 쓰지만(`GEMINI_API_KEY` 미설정), 그쪽은 `Flux.error(...)`로 만들어져 `ConversationController`/`ConversationStompController`가 리액티브 체인 안에서 `.onErrorReturn`/`.doOnError`로 이미 흡수하므로 이 핸들러까지 도달하지 않는다. 실제로 이 핸들러가 잡는 `IllegalStateException`은 `VoiceCallService`의 LiveKit Egress 실패뿐이다 — "우리 요청이 잘못됨"(4xx)이 아니라 "외부 서비스가 실패함"이므로 502로 구분했다.
* **테스트**: `apps/backend/src/test/java/com/storia/backend/exception/GlobalExceptionHandlerTest.java`에서 `@WebMvcTest(ConversationController.class)`로 404/400(검증)/400(헤더 누락) 세 케이스를 MockMvc로 검증.

## TODO / 남은 범위

* 클라이언트(`apps/client`)가 `code` 필드를 보고 분기 처리(재시도 가능 여부 등)하는 로직은 아직 없음 — 지금은 HTTP 상태코드만 보고 일반적인 에러 토스트를 띄우는 수준(PRD 3.4)
* `code` 값들을 enum이나 상수로 추출해 클라이언트/백엔드가 공유하는 계약으로 문서화할지는 미결정 (현재는 이 문서가 유일한 출처)
