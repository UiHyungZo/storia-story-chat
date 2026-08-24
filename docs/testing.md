# Testing

## Goal

Storia는 클라이언트(RN)와 백엔드(Spring Boot)를 모두 직접 구현하는 포트폴리오 프로젝트다. 테스트는 "만들어봤다"가 아니라 "동작을 보장할 수 있다"를 증명하는 수단으로 취급한다 (PRD 7주차 범위, [`TODO.md`](../TODO.md)).

목표:

* 백엔드: Service/Repository 레이어의 비즈니스 로직 검증
* 클라이언트: 화면 상태 전이(로딩/에러/재시도)와 스토어 로직 검증
* Empty/Error 케이스를 정상 흐름으로 취급하고 테스트에 포함
* 실시간 채널(WebSocket/WebRTC)이 추가되어도 REST 폴백 경로가 깨지지 않는지 회귀 검증

## 현재 상태

`BackendApplicationTests`(컨텍스트 로드 확인용 스모크 테스트) 1개만 존재. 실질적인 단위/통합 테스트는 아직 없음. 클라이언트 쪽 테스트도 없음.

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

# Client — Jest + React Native Testing Library

## 대상

* Zustand 스토어(`useCharacterStore`, `useConversationStore`) — 상태 전이 로직
* `CharacterListScreen`, `ChatRoomScreen` — 로딩/에러/빈 목록 렌더링
* `CharacterListItem`, `MessageBubble` — 프레젠테이션 컴포넌트 스냅샷/렌더 검증

## 필수 테스트 케이스

```text
캐릭터 목록 로딩 중 스켈레톤/로딩 표시
API 실패 시 에러 메시지 + 재시도 버튼 표시
재시도 버튼 탭 시 재요청 발생
채팅방 진입 시 히스토리 메시지 순서대로 렌더링
메시지 전송 중 입력창 비활성화(중복 전송 방지)
```

## 규칙

* 네트워크 호출은 반드시 Mock (`msw` 또는 fetch mock) — 실제 백엔드에 의존하는 테스트 금지
* 스토어 테스트는 컴포넌트 렌더링 없이 스토어 API만 직접 호출해 상태 검증 (속도 우선)

---

# WebSocket / WebRTC 테스트 (WebSocket은 2주차에 구현됨, 테스트 자체는 7주차 범위 — 향후)

* WebSocket: STOMP 클라이언트를 Mock해 스트리밍 청크 수신 → UI 업데이트 검증, 연결 끊김 시 REST 폴백 전환 검증
* WebRTC: Offer/Answer/ICE 교환 자체는 통합 테스트가 어려우므로, 시그널링 메시지 직렬화/라우팅 로직만 단위 테스트로 분리해 검증 (실제 P2P 연결은 수동 QA로 확인)

---

# CI 연동 (7주차 — 향후)

* GitHub Actions에서 `./gradlew test`(백엔드), `npm test`(클라이언트) 실행
* 실패 시 배포 파이프라인(Fastlane/Docker) 진행 차단
