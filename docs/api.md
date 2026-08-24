# API

## Goal

Storia 백엔드는 REST(현재 구현) → WebSocket(STOMP, 2주차) → WebRTC 시그널링(6주차) 순으로 확장된다. 계층 간 책임을 명확히 나눠, 실시간 채널이 추가되어도 기존 REST 경로가 폴백으로 계속 동작하도록 유지한다.

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

유저 메시지 저장. LLM 응답 트리거(Gemini 연동)는 2주차 범위 — 현재는 유저 메시지만 저장하고 그대로 응답하며, 어시스턴트 응답은 생성되지 않는다.

```text
POST /api/conversations/{characterId}/messages
Header: X-Device-Id: <uuid>
Content-Type: application/json

{ "content": "안녕" }
```

응답 (`201 Created`, `MessageResponse`):

```json
{ "id": 3, "role": "USER", "content": "안녕", "createdAt": "2026-08-24T10:00:02Z" }
```

* 구현: `ConversationController` → `ConversationService#postMessage`
* `content`가 비어있으면 `@Valid`(`@NotBlank`) 검증 실패 — 단, 전역 예외 처리기가 없어 현재는 500으로 노출됨 (아래 Error Handling Policy 참고)

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

# WebSocket Policy (2주차 — 미구현)

PRD 3.3, `docs/architecture/README.md` "목표 아키텍처" 참고.

계획:

```text
Client --publish--> /app/conversation/{id}/send
Server --subscribe--> /topic/conversation/{id}
```

* STOMP 기반, Gemini 스트리밍 응답을 청크 단위로 `/topic/conversation/{id}`에 발행
* 연결 실패/타임아웃 시 클라이언트는 REST(`POST /api/conversations/{characterId}/messages`)로 폴백
* 재연결 정책은 3주차 범위 ([`TODO.md`](../TODO.md))

---

# WebRTC Signaling Policy (6주차 — 미구현)

PRD 3.9, 3.10 참고. B안(WebRTC 미사용, RN STT + 서버 TTS 오디오 URL) + C안(WebRTC 최소 데모) 조합이 기본 전략이며, 처음부터 풀 WebRTC 파이프라인(A안)으로 설계하지 않는다.

계획:

* Offer/Answer/ICE Candidate 교환은 기존 WebSocket(STOMP) 채널로 중계 (별도 시그널링 프로토콜 신설 안 함)
* STUN은 공개 Google STUN 서버 사용, TURN은 범위 밖 (PRD 9절)

---

# Test Policy

API 계층 테스트 방침은 [`docs/testing.md`](./testing.md) 참고.
