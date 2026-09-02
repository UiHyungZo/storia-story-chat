# Testing

## Goal

Storia는 클라이언트(RN)와 백엔드(Spring Boot)를 모두 직접 구현하는 포트폴리오 프로젝트다. 테스트는 "만들어봤다"가 아니라 "동작을 보장할 수 있다"를 증명하는 수단으로 취급한다 (PRD 7주차 범위, [`TODO.md`](../TODO.md)).

목표:

* 백엔드: Service/Repository 레이어의 비즈니스 로직 검증
* 클라이언트: 화면 상태 전이(로딩/에러/재시도)와 스토어 로직 검증
* Empty/Error 케이스를 정상 흐름으로 취급하고 테스트에 포함
* 실시간 채널(WebSocket/WebRTC)이 추가되어도 REST 폴백 경로가 깨지지 않는지 회귀 검증

## 현재 상태 (2026-09-02)

* **백엔드 — 18개** (`./gradlew test`, H2 인메모리): Service 단위(Mockito) `CharacterServiceTest`/`ConversationServiceTest`/`MessageServiceTest`, graceful-degrade `TtsServiceTest`/`SttServiceTest`, `MessageRepositoryTest`(`@DataJpaTest`), `CharacterControllerTest`(`@WebMvcTest`+`@MockitoBean`), `GlobalExceptionHandlerTest`, `BackendApplicationTests`(스모크).
* **클라이언트 단위 — 17개** (`npm test`, `jest-expo`): `avatarColorFor`, `config.ts`(`API_BASE_URL` 플랫폼 분기), `useCharacterStore`/`useConversationStore`(상태 전이 + 3주차 버그 2건 회귀).
* **클라이언트 UI — 17개** (`@testing-library/react-native` v13): `CharacterListItem`(2), `MessageBubble`(3), `VoiceCallOverlay`(7), `CharacterListScreen`(5). 스토어는 명시적 factory로 mock(네이티브 의존성 회피), 셀렉터는 `mockImplementation((sel) => sel(fakeState))`.
* CI(`ci.yml`)가 push/PR마다 `./gradlew test` + `tsc --noEmit` + `jest --ci` 실행.

**남은 것**: `ChatRoomScreen` 컴포넌트 테스트(스토어 3개 + navigation), Maestro E2E 스모크 1개(시뮬레이터 필요 — 배포 실기기 검증과 묶어서), WebSocket 스트리밍/폴백 통합 테스트.

---

# Backend — JUnit5 + Spring Boot Test

## 대상

* `CharacterService`, `ConversationService` — 비즈니스 로직 단위 테스트
* `CharacterController`, `ConversationController` — `@WebMvcTest` 기반 요청/응답 검증
* Repository — `@DataJpaTest`로 쿼리 메서드 검증 (`findByDeviceId`, `findByUserIdAndCharacterId` 등)

## 필수 테스트 케이스

```text
정상 조회 (캐릭터 목록, 대화 히스토리)
존재하지 않는 characterId 조회 → 예외/404
신규 deviceId 최초 요청 시 app_user 자동 생성 (get-or-create)
기존 deviceId 재요청 시 기존 user/conversation 재사용 (중복 생성 안 됨)
캐릭터당 대화 1개 제약 — 같은 (user, character) 쌍으로 두 번 조회해도 conversation row는 1개
```

## Mock 정책

* Service 단위 테스트는 Repository를 Mock으로 대체 (Mockito)
* Controller 테스트는 `@WebMvcTest` + `@MockBean` Service로 웹 계층만 격리
* 실제 DB를 태우는 통합 테스트는 `@DataJpaTest`(임베디드 DB 또는 Testcontainers) 범위로 제한

## 규칙

* `@SpringBootTest`(전체 컨텍스트 로드) 남용 금지 — 느리고 격리가 약함. 가능하면 슬라이스 테스트(`@WebMvcTest`, `@DataJpaTest`) 우선
* 테스트에서 운영 DB(MariaDB 3307)에 직접 의존하지 않는다 — CI에서도 동일하게 재현 가능해야 함

---

# Client — Jest (`jest-expo`) + React Native Testing Library

## 대상

* Zustand 스토어(`useCharacterStore`, `useConversationStore`) — 렌더링 없이 스토어 API 직접 호출 (`src/store/__tests__/`)
* `CharacterListScreen` — 로딩/에러/재시도/네비게이션 (`src/screens/__tests__/`)
* `CharacterListItem`, `MessageBubble`, `VoiceCallOverlay` — 렌더 + 인터랙션 (`src/components/__tests__/`)

## 규칙

* 네트워크·네이티브 모듈은 **명시적 factory로 mock** — `jest.mock(path, () => ({...}))`. automock(`jest.mock(path)`)은 실제 모듈을 먼저 로드하려다 `AsyncStorage`/LiveKit/`expo-audio` 등에서 터진다.
* 셀렉터 훅(`useStore(s => s.x)`)을 mock할 땐 `mockImplementation((selector) => selector(fakeState))`.
* 스타일 검증이 필요하면 소스에 `testID`를 붙인다(예: `MessageBubble`의 `message-bubble-{user|assistant}`). 부모 체인 탐색(`.parent.parent`)은 composite fiber를 만나 깨지기 쉬움.
* RNTL v13은 matcher(`toBeOnTheScreen`/`toHaveStyle`/`toBeDisabled`)를 자동 확장 — 별도 setup 파일 불필요.
* `react-test-renderer`는 `react`와 **정확히 같은 버전**으로 핀(현재 `19.2.3`). 안 하면 npm이 최신으로 올려 peer 충돌.

---

# WebSocket / WebRTC 테스트 (WebSocket은 2주차에 구현됨, 테스트 자체는 7주차 범위 — 향후)

* WebSocket: STOMP 클라이언트를 Mock해 스트리밍 청크 수신 → UI 업데이트 검증, 연결 끊김 시 REST 폴백 전환 검증
* WebRTC: Offer/Answer/ICE 교환 자체는 통합 테스트가 어려우므로, 시그널링 메시지 직렬화/라우팅 로직만 단위 테스트로 분리해 검증 (실제 P2P 연결은 수동 QA로 확인)

---

# CI 연동

* `.github/workflows/ci.yml` — push/PR마다 `./gradlew test`(백엔드, H2) + `tsc --noEmit` + `jest --ci`(클라이언트)를 별도 job으로 실행.
* 릴리스(`.github/workflows/release.yml`, Fastlane)는 태그 트리거로 별도 — `ci.yml`과 독립적이며 서로를 게이트하지 않는다(태그는 이미 머지된 커밋에만 붙음).
