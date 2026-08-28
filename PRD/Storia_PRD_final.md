# PRD — AI 캐릭터 스토리 챗 "Storia" (final / as-built)

## 0. 이 문서에 대하여

- **성격**: v2 → v3 → v3(병합본)으로 이어진 계획 문서를, **7주차까지 실제로 구현된 상태(as-built)에 맞춰 최신화한 최종본**이다. 계획과 구현이 갈라진 지점(특히 음성 통화 아키텍처, 배포 전략, Android Native Module)은 이 문서를 기준으로 읽으면 된다.
- **관계**: 이 문서가 **유일한 PRD**다. 이전 버전(v2 → v3 → v3 병합본)은 삭제했으며, "원래 계획이 무엇이었나"는 git 히스토리에서 확인한다(`git log --follow -- PRD/`, 마지막 스냅샷은 커밋 `62429c4` 시점). 현재 상태의 소스 오브 트루스는 이 문서 + [`docs/architecture/README.md`](../docs/architecture/README.md) + [`docs/decisions.md`](../docs/decisions.md)다.
- **검증 상태는 여기 적지 않는다**: "코드가 어디까지 작성됐나"는 이 문서, "어디까지 실제로 실행 검증됐고 무엇이 자격증명 대기 중인가"는 [`HANDOFF.md`](../HANDOFF.md)(세션별) / [`TODO.md`](../TODO.md)(주차별)를 본다.

### 변경 이력 (요약)

- **v2**: 백엔드 레이어 신설(Spring Boot). 클라이언트가 Gemini를 직접 호출하던 구조를 서버 경유로 바꾸고 WebSocket 스트리밍 추가. RN 단독 → 풀스택 포트폴리오. (DB: PostgreSQL)
- **v3**: WebRTC 기반 음성 통화 추가로 범위 확장. DB를 PostgreSQL → MariaDB로 변경.
- **v3 병합본**: v3의 `(v2와 동일)` 참조를 전개해 단일 문서로 완결. 내용 변경 없음. **1주차 이후 갱신되지 않음.**
- **final (이 문서)**: 2~7주차 구현 과정에서 확정된 실제 설계를 반영.
  - 음성 통화: B안(클라이언트 STT) + 별도 C안(WebRTC 데모) → **"축소판 A안"**(클라이언트↔LiveKit 실제 WebRTC + 서버는 Track Egress로 오디오만 수신). 클라이언트 STT 제거, C안은 별도 데모 없이 선행 충족으로 종료. `docs/decisions.md` [[ADR-004]] 갱신 1~3.
  - 완전한 양방향 실시간(원래 정의의 A안)은 여전히 범위 밖이나, **Python 사이드카 스켈레톤**(`apps/python-sidecar/`)을 선택적 확장 경로로 리포에 포함.
  - Android Native Module(Kotlin): "범위 밖" → **구현**.
  - 배포: Fastlane → **EAS Build**, Firebase App Distribution → **Google Play 내부 테스트**, 상시 클라우드 배포 → **로컬/LAN + 필요 시 기동**. 목표선 = TestFlight + Play 내부 테스트. [[ADR-007]].
  - STT/TTS: Google Cloud STT + Google Cloud TTS로 확정(ElevenLabs·클라이언트 STT 미사용).

---

## 1. 개요

**목적**: React Native(Expo) + Spring Boot 기반 풀스택 포트폴리오. AI 캐릭터와 **텍스트 채팅**뿐 아니라 **실시간 음성 통화**로도 상호작용하는 앱을, 클라이언트 – 백엔드 – 실시간 통신(WebSocket + WebRTC) – Native Module까지 전 구간 직접 구현해 증명한다.

**타겟 포지션**: "AI / Software Engineer / Full Stack" 유형을 메인으로 하되, 클라이언트 파트만 발췌해 RN 프론트엔드 포지션에도 재사용 가능하도록 설계.

**핵심 증명 목표**

- RN + TypeScript + Expo(Development Build)로 처음부터 끝까지 동작하는 앱을 만들 수 있다.
- Spring Boot 백엔드를 설계·구현하고 RESTful API와 DB 모델링을 직접 할 수 있다.
- **WebSocket(텍스트 스트리밍) + WebRTC(음성 통화)** 두 실시간 통신 방식을 모두 다룰 수 있다.
- LLM API 스트리밍 응답을 서버에서 받아 클라이언트로 실시간 중계할 수 있다.
- 음성 통화 파이프라인(실제 WebRTC 미디어 수신 → STT → LLM → TTS)을 구축할 수 있다.
- Swift / Kotlin Native Module을 RN에서 동일 인터페이스로 호출할 수 있다.
- FCM 원격 푸시, Sentry 에러 모니터링, 자동화 테스트, CI, 컨테이너화까지 프로덕션급 구성을 다뤄봤다.
- TestFlight / Google Play 내부 테스트 배포 파이프라인을 완주한 경험이 있다.

---

## 2. 사용자 시나리오

1. 앱을 열면 대화 가능한 캐릭터 목록이 보인다(백엔드 `GET /api/characters`).
2. 캐릭터를 선택하면 채팅방으로 진입한다(캐릭터당 하나의 연속 대화).
3. **텍스트 모드**: 메시지를 입력하면 WebSocket(STOMP)으로 전송 → 백엔드가 Gemini 스트리밍 응답을 청크 단위로 재전송(타이핑 효과). 연결 실패·타임아웃 시 REST로 폴백.
4. **음성 통화 모드**: 채팅방 상단 "통화" 버튼 → LiveKit room에 실제 WebRTC로 연결되고 마이크 트랙을 publish. 말하고 트랙을 unpublish하면(턴 종료) 서버가 그 오디오를 배치로 STT → Gemini → TTS 처리하고, 클라이언트는 합성된 캐릭터 음성을 내려받아 재생한다(턴제, 풀 듀플렉스 아님).
5. 응답 대기 중 로딩 상태가 표시되고, 실패 시 오류 메시지 + 재시도 버튼이 뜬다.
6. 어시스턴트 응답이 도착하면 Haptic 피드백 + 포그라운드 로컬 알림(Native Module). 앱이 백그라운드/종료 상태면 백엔드가 FCM 푸시를 발송한다. 3일 이상 미접속 유저에게는 일일 재참여 푸시.
7. 대화 히스토리(텍스트)는 백엔드 DB(MariaDB)에 저장되고, 클라이언트 AsyncStorage에도 캐싱되어 오프라인 진입 시 즉시 표시된다.
8. 앱/서버 에러는 Sentry로 수집된다(DSN 미설정 시 SDK가 조용히 비활성화).

---

## 3. 기능 요구사항

### 3.0 캐릭터 설계 (오리지널 3종)

저작권 이슈를 피해 완전 오리지널로 설계. 시스템 프롬프트·메타데이터는 **백엔드 DB(`story_character` 테이블)에서 관리**하며, 신규 캐릭터는 코드 배포 없이 데이터 삽입만으로 추가 가능(`CharacterSeeder`가 최초 1회 시딩, idempotent). 캐릭터별 **TTS voice ID**를 메타데이터로 관리.

| 캐릭터 | 컨셉 | 말투/성격 | TTS voice |
| --- | --- | --- | --- |
| 아리아 (Aria) | 우주선 항법 AI가 인격을 얻어가는 SF | 차분·논리적, 가끔 서툰 유머 | `ko-KR-Standard-A` |
| 렌 (Ren) | 폐업 위기의 작은 서점 운영자 | 따뜻·다정, 은근히 책 추천 강요 | `ko-KR-Standard-C` |
| 노아 (Noah) | 탐정 사무소 조수, 옴니버스 미스터리 | 능청·장난기, 사건 앞에선 진지 | `ko-KR-Standard-D` |

> 실존 인물·유명 캐릭터·로맨스/성적 뉘앙스는 배제. `ttsVoiceId`는 실제 Google Cloud TTS에 존재하는 값이어야 함(이전 플레이스홀더 문자열은 무효값이었음 — [[ADR-004]] 갱신).

### 3.1 캐릭터 목록 화면

- `GET /api/characters`로 캐릭터 목록 조회.
- 목록 탭 시 채팅방으로 이동(React Navigation).
- 캐릭터당 하나의 연속 대화만 유지(다중 세션 범위 밖).
- 로드 실패 시 오류 배너 + "다시 시도" 버튼. 캐시된 목록이 있으면 먼저 렌더 후 백그라운드 갱신.

### 3.2 채팅방 화면

- 메시지 리스트(FlatList), 최초 진입 시 `GET /api/conversations/{characterId}/messages`로 히스토리 로드.
- 유저 / 어시스턴트 말풍선 스타일 구분.
- 하단 입력창 + 전송 버튼 → WebSocket으로 발행(실패 시 REST 폴백).
- 스트리밍 중 타이핑 인디케이터, 청크 수신마다 갱신. Gemini 응답이 늦을 수 있어(아래 3.3) "답변을 생각하는 중" 배너 + 전송 버튼 in-flight 표시.
- 키보드 표시 시 레이아웃 조정(`KeyboardAvoidingView` + `useHeaderHeight()`로 오프셋 동적 계산 — 실기기 키보드 가림 버그 대응).
- 채팅방 상단 "📞 통화" 버튼 → 3.9 음성 통화 오버레이.
- WS 재연결 대기 중이면 "재연결 중" 배너, 그동안의 전송은 REST로 개별 폴백.

### 3.3 AI 응답 연동 (텍스트)

- **클라이언트는 Gemini를 직접 호출하지 않는다.** 모든 LLM 요청은 Spring Boot 경유(API 키는 서버에만).
- 백엔드가 Google Gemini `streamGenerateContent`(SSE)를 호출. 모델은 `gemini-3.6-flash`(`application.yml`의 `gemini.model`로 분리 — 모델 교체 시 이 값만 수정).
- **추론(thinking) 모델 특성상 첫 청크까지 지연이 큼**: 실제 캐릭터 프롬프트 요청에서 19~40초+ 관측. `GeminiService`의 청크 타임아웃은 60초(과거 30초에서 상향, `docs/decisions.md` 함정 노트 참고). 대화 히스토리가 길수록 더 걸릴 수 있음.
- **WebSocket(STOMP) 스트리밍**: `/app/conversation/{characterId}/send` 수신 → Gemini 스트리밍 청크를 `/topic/conversation/{deviceId}/{characterId}`로 발행(디바이스별 스코프). `GeminiService`는 `WebClient`(리액티브)로 SSE 소비, `Schedulers.boundedElastic()`에서 구독.
- **REST 폴백**: `POST /api/conversations/{characterId}/messages`가 Gemini를 동기 호출(`.collect(...).block()`)해 논스트리밍 응답 반환. 클라이언트는 화면 진입 시 WS를 1회 시도하고, 전송 시점마다 실제 연결 상태를 확인해 WS/REST를 결정.
- 캐릭터 시스템 프롬프트는 매 요청 시 DB에서 조회해 컨텍스트로 주입.
- 자격증명 미설정(`GEMINI_API_KEY` 없음) 시: WS는 `ERROR` 이벤트, REST는 고정 안내 문구로 우아하게 저하. 폴백 전 `doOnError`로 항상 로그를 남긴다(에러를 조용히 삼키면 모델 서비스 종료 같은 상황을 못 잡음 — 실제로 겪음).

### 3.4 로딩 · 오류 · 재시도

- API/WS 요청 중 로딩 상태 표시(타이핑 인디케이터 / 스켈레톤).
- 실패 시 오류 메시지 + 재시도 버튼. 메시지 전송 실패는 전용 배너로 표시하고 탭 시 같은 내용 재전송(draft 유실 방지).
- WS 연결 끊김 감지 시 지수 백오프 자동 재연결(`@stomp/stompjs`의 `reconnectDelay`/`maxReconnectDelay`/`EXPONENTIAL`), 재연결마다 구독 재설정.
- `disconnect` 시 캐릭터별 transport 캐시 초기화 → 다음 화면 진입 시 WS 재시도.

### 3.5 데이터 저장 및 상태 복원

- 원본 데이터는 백엔드 **MariaDB** — `app_user` / `story_character` / `conversation` / `message`(ERD는 [`docs/erd/README.md`](../docs/erd/README.md)).
- 클라이언트는 AsyncStorage에 캐릭터 목록·대화별 메시지를 write-through 캐싱. 로드 시작 시 캐시로 즉시 하이드레이션 후 백그라운드 fetch, 네트워크 실패해도 마지막 데이터는 화면에 남음.
- 앱 재실행·백엔드 재시작 후에도 히스토리 복원.
- 음성 통화는 **텍스트로 전사된 결과만** 히스토리에 남기고 오디오 자체는 저장하지 않음. 턴 상태는 DB가 아닌 인메모리(`VoiceTurnRegistry`).

### 3.6 푸시 알림

- **로컬 알림 + Haptic**: 어시스턴트 응답 도착 시 Native Module `NativeModules.HapticNotifier.notify(title, body)` 호출(WS `DONE` / REST 성공 두 지점). 포그라운드에서도 배너 노출.
- **원격 푸시(FCM)**: 앱이 백그라운드/종료 상태일 때 백엔드가 Firebase Admin SDK로 발송. `ConversationService`의 어시스턴트 메시지 저장 지점(WS/REST 공유)에서 자동 시도.
- **재참여 푸시**: `ReEngagementScheduler`가 매일 3일 이상 미접속 유저에게 FCM 발송. `User.lastActiveAt`(채팅 경로 진입마다 갱신), `User.reengagementPushSent`(유휴 기간당 1회, 복귀 시 리셋).
- 클라이언트: `@react-native-firebase/app` + `/messaging`으로 권한 요청 → 토큰 발급 → `PUT /api/devices/token` 등록(앱 시작 시 1회, best-effort).
- 자격증명(`FIREBASE_CREDENTIALS_PATH` 서비스 계정 JSON) 미설정 시: 토큰은 저장되지만 발송은 조용히 no-op.

### 3.7 Native Module — iOS(Swift) + Android(Kotlin)

- **iOS**: Swift, 클래식 `RCTBridgeModule` 패턴(`RCT_EXTERN_MODULE`/`RCT_EXTERN_METHOD`). `UINotificationFeedbackGenerator` + `UNUserNotificationCenter`(delegate `willPresent`에서 `.banner` 반환해 포그라운드 알림 opt-in).
- **Android**: Kotlin, 대칭되는 `ReactContextBaseJavaModule` / `ReactPackage`. `Vibrator`/`VibratorManager` + `NotificationManagerCompat`(채널 importance `HIGH`). `POST_NOTIFICATIONS`(API 33+)는 첫 알림 시점에 lazy 요청.
- 두 플랫폼이 **동일한 브리지 이름**(`NativeModules.HapticNotifier.notify()`)을 노출 → `index.ts`가 플랫폼 분기 없이 호출.
- **위치**: `apps/client/modules/storia-native/`(로컬 Expo 모듈). 이 프로젝트는 `expo prebuild`로 `ios/`·`android/`를 재생성하므로(`.gitignore`), 네이티브 코드는 반드시 `./modules` 아래에 둬야 prebuild에도 살아남는다(`expo-modules-autolinking`이 자동 링크).

### 3.8 에러/크래시 모니터링 (Sentry)

- Sentry SDK를 RN(`@sentry/react-native`, `Sentry.wrap(App)`) + Spring Boot(`io.sentry:sentry-spring-boot-4` — Spring Boot 4 전용 아티팩트) 양쪽에 연동.
- JS 레이어 에러, Native 크래시, 백엔드 예외(WS 연결 오류, Gemini 타임아웃, WebRTC/LiveKit 연결 실패 등) 수집 대상.
- DSN(`EXPO_PUBLIC_SENTRY_DSN` / `SENTRY_DSN`) 미설정 시 SDK가 스스로 비활성화 — 별도 가드 코드 불필요.
- 로컬 iOS 개발 빌드 시 `SENTRY_DISABLE_AUTO_UPLOAD=true` 필요(소스맵 업로드 시도가 빌드를 깨뜨림).
- 의도적 에러 시나리오(클라 1 / 백엔드 1)를 만들어 대시보드에서 확인·수정하는 과정을 문서화(자격증명 확보 후).

### 3.9 음성 통화 (WebRTC) — "축소판 A안"

**목표**: "AI 음성 스트리밍 기반 통화"를 실제 WebRTC 미디어 경로를 포함해 증명하되, 1인 개발 일정에 맞는 완성도로 축소. 영상은 제외.

**아키텍처 결정**: 완전한 양방향 실시간 A안(서버가 합성 음성을 WebRTC로 실시간 되쏘기)은 **서버 쪽 WebRTC 미디어 처리에 JVM용 표준 라이브러리가 없어** 일정 리스크가 큼(리서치 스파이크로 확인). 아래 "축소판 A안"으로 합의 — [[ADR-004]] 갱신 2.

**흐름 (턴제)**

1. `POST /api/calls/{characterId}/token` → LiveKit room 토큰 발급(`VoiceCallService`, `io.livekit:livekit-server`). 클라이언트 그랜트는 `CanPublish(true)` + `CanSubscribe(true)`.
2. 클라이언트가 `room.connect()` 후 `setMicrophoneEnabled(true)`로 마이크 트랙 publish — **여기가 실제 WebRTC 구간**(ICE, DTLS-SRTP 포함). 클라이언트 SDK는 `@livekit/react-native` + `@livekit/react-native-webrtc`(+ Expo config plugin).
3. `POST /api/calls/{characterId}/turns`(trackSid) → 백엔드가 LiveKit **Track Egress**를 시작하되, S3 등 저장소가 아니라 **WebSocket 직접 스트리밍 옵션**(`pcm_s16le`, 48kHz)으로 `wss://<backend>/egress/audio?turnId=`를 대상으로 지정. 별도 클라우드 스토리지 불필요.
4. LiveKit이 그 WebSocket으로 raw PCM 프레임을 push(`VoiceEgressWebSocketHandler`, STOMP `/ws`와 별개인 raw WebSocket). 마이크 트랙 unpublish → Egress WS 종료 = 턴 완료 신호.
5. 백엔드가 버퍼된 오디오를 **배치 STT**(Google Cloud Speech-to-Text, `SttService`) → Gemini → **TTS**(Google Cloud TTS, `TtsService`) 처리.
6. 클라이언트가 `GET /api/calls/turns/{turnId}` 폴링 → `status=done` + `assistantMessageId` → `GET /api/messages/{messageId}/audio`로 합성 오디오를 그때그때 받아 재생(사전 저장 없음).

**메시지 파이프라인 재사용**: 음성 통화는 새 실시간 채널을 만들지 않고 REST 전용 경로(`useConversationStore#sendMessageViaRest` — 응답 완료까지 대기 후 반환)를 재사용. WS 스트리밍 경로는 응답 완료 시점을 안 기다리고 resolve되므로 "오디오를 언제 가져올지" 결정할 수 없어 음성엔 부적합.

**C안(WebRTC 최소 데모)**: 별도 구현하지 않음. 위 2번에서 실제 WebRTC 미디어 연결(마이크 캡처·ICE·DTLS-SRTP·트랙 publish)이 이미 들어가 원래 C안의 목적("WebRTC 연동 경험 증명")이 선행 충족됐다고 판단 — [[ADR-004]] 갱신 3. LiveKit SDK가 Offer/Answer/ICE 교환을 내부 처리하므로 "수동 시그널링 프로토콜 구현 코드"는 없음. 필요해지면 `react-native-webrtc` 기반 수동 데모로 되돌릴 여지는 남김.

**완전한 A안 확장 — 선택적 (`apps/python-sidecar/`)**

- 완전한 양방향 실시간(서버 TTS를 LiveKit room에 오디오 트랙으로 실시간 publish)을 위한 **스켈레톤**을 리포에 포함. LiveKit이 이 문제를 Python/Node **Agents SDK**로 푸는 것을 표준으로 삼기 때문에, Spring에 붙이지 않고 **별도 프로세스 + REST 통신** 사이드카 패턴을 택함.
- Agent 워커가 room에 봇으로 들어가 유저 오디오 구독 → STT/TTS는 플러그인이 처리 → 응답 텍스트는 **기존 Spring REST**(`POST /api/conversations/{characterId}/messages`)를 그대로 호출해 획득(로직 중복 없음).
- **기존 축소판 A안 경로의 대체가 아니라 추가 대안 경로**: Agent 워커가 떠 있으면 실시간 응답, 없으면 축소판 경로만 동작.
- Spring 측 변경은 클라이언트 토큰 `CanSubscribe(false) → true` 한 곳뿐.
- 현재 스켈레톤 상태(설치·API 정합성만 검증, 실제 room 연결 미검증). 상세는 [`apps/python-sidecar/README.md`](../apps/python-sidecar/README.md).

**로컬 개발 제약**: LiveKit Cloud(원격)가 `/egress/audio`로 다시 접속해와야 하므로 `LIVEKIT_EGRESS_AUDIO_WS_URL`이 `localhost`면 동작 불가 — ngrok 등 공인 접근 가능한 터널 필요.

### 3.10 백엔드 아키텍처

- **Spring Boot 4.0.7 + Java 17**(`build.gradle` toolchain 17). 계층: Controller(REST) / STOMP Controller / raw WebSocket Handler / Service / Repository(JPA) / Entity.
  - Spring Boot 4의 **모듈화된 스타터**를 사용 — 통짜 `spring-boot-starter-web` 대신 `spring-boot-starter-webmvc` + `spring-boot-starter-websocket`. 이 구성에서는 classic Jackson `ObjectMapper` 빈이 자동 생성되지 않아, 이를 생성자 주입받는 서비스(`TtsService`/`SttService`) 때문에 컨텍스트 로딩이 실패했음 → `config/JacksonConfig.java`에 명시적 `@Bean ObjectMapper` 추가로 해결.
  - 테스트 스타터도 분리형(`spring-boot-starter-*-test`). `@MockBean`은 제거됐고 `org.springframework.test.context.bean.override.mockito.MockitoBean` 사용.
- **DB: MariaDB** + JPA/Hibernate. 로컬은 `docker-compose`(호스트 포트 **3307**:3306 — Homebrew mysqld가 3306 점유, [[ADR-003]]). 테이블: `app_user`, `story_character`(예약어 회피 매핑, [[ADR-002]]), `conversation`, `message`.
- **WebSocket(STOMP)**: `WebSocketConfig`(`/ws`, SockJS 없이 raw STOMP), `ConversationStompController`. 텍스트 스트리밍 전용.
- **raw WebSocket**: `/egress/audio`(`VoiceEgressWebSocketConfig`) — LiveKit Track Egress PCM 수신 전용, STOMP와 분리.
- **Gemini 연동**: `spring-boot-starter-webflux`(전체 리액티브 전환) 대신 `spring-webflux` + `reactor-netty-http`만 추가해 `WebClient` 빈만 사용. 앱은 계속 Servlet(MVC). REST 폴백은 `.block()`으로 동기화 — [[ADR-006]].
- **음성 통화**: `io.livekit:livekit-server`(문서가 얇아 jar를 `javap`으로 시그니처 확인 후 작성). `voice/` 패키지에 세션/레지스트리/Egress 핸들러.
- **전역 예외 처리**: `GlobalExceptionHandler`(`@RestControllerAdvice`) — `ResourceNotFoundException` 등을 `{code, message}` + 적절한 상태코드로 매핑. 엔드포인트별 케이스는 [`docs/error-handling.md`](../docs/error-handling.md).
- **API 문서화**: Swagger/OpenAPI(`/swagger-ui.html`).
- **CORS**: `WebConfig`가 `/api/**` 전체 허용(로컬 개발 전용, 배포 전 재검토).
- **인증**: 정식 로그인 없음. `X-Device-Id` 헤더 기반 익명 세션, 처음 보는 deviceId는 즉시 `app_user` 생성 — [[ADR-005]].
- **외부 연동 자격증명 미설정 시 패턴**: `*Properties#isConfigured()`로 확인 후 예외 없이 저하(로그 / `null` / `404`). 예외는 `LIVEKIT_*` — 음성 통화가 성립 불가하므로 `VoiceCallController`가 명시적 `503`.

### 3.11 상태 관리 (클라이언트)

- Zustand: `useCharacterStore`(목록 + 캐시), `useConversationStore`(대화별 메시지, 스트리밍 상태, WS/REST transport, 전송 오류), `useVoiceCallStore`(LiveKit `Room` 직접 관리, 통화 phase).
- 빈 상태 상수(`EMPTY_MESSAGES` 등)로 셀렉터 참조 안정화 — 매번 새 배열/객체를 반환하면 "Maximum update depth exceeded" 무한 루프가 남(실제로 겪어 수정).

### 3.12 배포 전략 (iOS / Android / Backend)

> **목표선 재정의**: 스토어 정식 출시가 아니라 **"RN으로 iOS/Android 양쪽 실제 배포 파이프라인까지 처리할 수 있음"을 증명**하는 것. **TestFlight + Google Play 내부 테스트까지**가 목표. 이미 Swift 개인 앱을 App Store에 정식 출시한 경험이 있어 재증명 가치가 낮음 — [[ADR-007]].

- **iOS**: **EAS Build**(Fastlane 아님, `apps/client/eas.json`의 `development`/`preview`/`production` 프로파일) → TestFlight 내부 테스트. `submit.production.ios`(appleId/ascAppId/appleTeamId)는 계정 확보 후 채움.
- **Android**: **Google Play Console 가입 + 내부 테스트 트랙**(Firebase App Distribution 아님). EAS Build로 AAB. 내부 테스트는 정식 리뷰·"20인 14일" 요건이 없음.
- **Backend**: **상시 클라우드 배포 안 함.** 평소엔 로컬/LAN으로 기동하고, TestFlight/Play 내부 테스트 심사나 실제 데모 시점에만 띄운다(둘 다 리뷰어가 백엔드를 호출하지 않음). 클라우드 제공자(AWS vs GCP)는 지원할 공고 요구사항 보고 결정 — [[ADR-007]]. OS 레벨 평문 HTTP 예외(iOS ATS `NSAllowsLocalNetworking`, Android cleartext)는 필요(이미 반영). 집 밖 시연 시 ngrok 예비.
- **CI**: `.github/workflows/ci.yml` — push/PR마다 백엔드(`./gradlew test`, H2라 DB 불필요) + 클라이언트(`npm ci` → `tsc --noEmit` → `jest --ci`). 계정/시크릿 불필요.
- **컨테이너**: `apps/backend/Dockerfile`(multi-stage: JDK 빌드 → JRE 런타임).
- **보류**: Fastlane, 실제 클라우드 배포/CD, Terraform 등 IaC — 실제 계정 확보 후 진행(추측성 스켈레톤 방지, 비용·비가역성 때문에 사용자 승인 전제).

---

## 4. 비기능 요구사항

- iOS 시뮬레이터/실기기, Android 에뮬레이터/실기기에서 정상 동작.
- 앱 크래시 없이 네트워크 끊김/재연결, WS 재연결, 통화 중 네트워크 끊김 시 안전한 세션 종료 처리.
- 백엔드는 동시 다중 WS 세션을 안정적으로 처리(최소 부하 수준 검증).
- 자동화 테스트: 백엔드 JUnit5 + Spring Boot Test(서비스 단위, `@DataJpaTest`, `@WebMvcTest`, graceful-degrade 경로), 클라이언트 Jest(`jest-expo` — 순수 함수, config 분기, store 로직 + 3주차 버그 회귀). H2 인메모리로 외부 자원 없이 전체 스위트 실행 가능.
- Git 커밋 히스토리를 의미 단위로 관리(실제 개발 흐름이 드러나도록).

---

## 5. 기술 스택

| 영역 | 채택 |
| --- | --- |
| 클라이언트 프레임워크 | React Native + Expo (Development Build) |
| 클라이언트 언어 | TypeScript |
| 상태 관리 | Zustand |
| Navigation | React Navigation |
| 로컬 캐시 | AsyncStorage |
| Native Module | Swift(`RCTBridgeModule`) + Kotlin(`ReactContextBaseJavaModule`) |
| 음성 통화 (클라) | `@livekit/react-native`, `livekit-client`, `@livekit/react-native-webrtc` (+ `@livekit/react-native-expo-plugin`, `@config-plugins/react-native-webrtc`) |
| 백엔드 프레임워크 | Spring Boot 4.0.7 (모듈화 스타터: `webmvc` + `websocket`) |
| 백엔드 언어 | Java 17 |
| 실시간 통신 | WebSocket(STOMP) 텍스트 스트리밍 + raw WebSocket(Egress PCM) + WebRTC(LiveKit, 클라↔Cloud) |
| 음성 통화 (서버) | `io.livekit:livekit-server`(room 토큰 + Track Egress 관리) |
| STT / TTS | Google Cloud Speech-to-Text(배치) / Google Cloud Text-to-Speech(REST) |
| DB | MariaDB + JPA/Hibernate |
| HTTP 클라이언트(SSE) | `spring-webflux` + `reactor-netty-http`의 `WebClient`만 부분 도입 |
| API 문서화 | Swagger/OpenAPI (springdoc) |
| AI API | Google Gemini (`gemini-3.6-flash`, `streamGenerateContent` SSE) |
| 푸시 | FCM(백엔드 `firebase-admin`), `@react-native-firebase/app`+`/messaging`(클라 토큰), Native Module 로컬 알림 |
| 모니터링 | Sentry (`@sentry/react-native` + `io.sentry:sentry-spring-boot-4`) |
| 테스트 | Jest(`jest-expo`) / JUnit5 + Spring Boot Test (+ H2) |
| CI | GitHub Actions (테스트만) |
| 컨테이너 | Docker (백엔드 multi-stage Dockerfile) |
| 클라이언트 빌드/배포 | EAS Build → TestFlight(iOS) / Google Play 내부 테스트(Android) |
| 완전한 A안 확장 (선택) | `apps/python-sidecar/` — Python 3.10~3.14 + LiveKit Agents SDK(`livekit-agents`, `livekit-plugins-google`, `livekit-plugins-silero`) |

> Native Module 때문에 Expo Go는 불가 — Development Build 필수.

---

## 6. 마일스톤 (7주, as-built)

| 주차 | 내용 |
| --- | --- |
| 1주차 | RN + Spring Boot 스캐폴딩, Navigation, 캐릭터 목록/채팅방 UI, 백엔드 REST + DB 스키마. (진행 중 PostgreSQL → MariaDB 전환) |
| 2주차 | 백엔드–Gemini 스트리밍 연동, WebSocket(STOMP) 채널, REST 폴백. (모델 `gemini-2.0-flash` 서비스 종료 → `gemini-3.6-flash` 교체, 청크 타임아웃 60초) |
| 3주차 | 로딩/오류/재시도 UI, WS 지수 백오프 재연결, DB 히스토리 저장/복원, AsyncStorage 캐시 동기화. WS 토픽을 디바이스별로 스코프. |
| 4주차 | Native Module — **iOS(Swift) + Android(Kotlin) 둘 다**(원래 Android는 범위 밖). FCM 백엔드(Admin SDK) + 클라이언트 토큰 등록. `lastActiveAt` 트래킹 + 재참여 스케줄러. 전역 예외 처리기. |
| 5주차 | 음성 통화 — B안(클라 STT + 서버 TTS)로 시작 → **"축소판 A안"으로 확장**(클라↔LiveKit 실제 WebRTC, 서버는 Track Egress로 PCM 수신 → 배치 STT/Gemini/TTS → 오디오 URL). 클라이언트 STT 제거. |
| 6주차 | C안(WebRTC 시그널링 최소 데모) **재평가 → 별도 코드 없이 종료**(5주차가 선행 충족). 문서 정리만. |
| 7주차 | Sentry(클라+백엔드), 자동화 테스트(백엔드 18 / 클라 17), GitHub Actions CI, 백엔드 Dockerfile. **배포 목표 재정의**(TestFlight + Play 내부 테스트). EAS Build 설정, 개인정보처리방침 초안, 앱 아이콘. Python 사이드카 스켈레톤 merge. Fastlane/CD/실클라우드 배포는 계정 확보까지 보류. |

---

## 7. 포트폴리오 활용 방안

- README에 아키텍처 다이어그램: RN ↔ WebSocket ↔ Spring Boot ↔ Gemini 흐름, RN ↔ Native Module 브리지, **텍스트 스트리밍(WebSocket) vs 음성 통화(WebRTC/LiveKit)** 구분, 푸시/모니터링 통합.
- 백엔드 설계 문서(ERD, API 명세, ADR)를 별도 정리해 풀스택 역량을 명시.
- **B안/축소판 A안/완전한 A안의 트레이드오프를 기술 블로그로 문서화** — 1인 개발 일정 내 현실적 완성도를 스스로 설계했음을 어필([`docs/blog-webrtc-tradeoffs.md`](../docs/blog-webrtc-tradeoffs.md)).
- Sentry 대시보드 스크린샷 + 에러 추적/해결 과정으로 트러블슈팅 역량의 실물 증거.
- GitHub 공개 리포 + TestFlight 링크 + Play 내부 테스트 링크 + (데모 시점) 백엔드 엔드포인트.
- 이력서 표기: "**Android 테스트 배포(Google Play 내부 테스트)**"로 정확히 구분. "상시 운영 서비스"가 아니라 "**배포 파이프라인·배포 자체를 완주한 경험**"으로 설명.
- Full Stack 지원 시: 백엔드 + WebSocket + WebRTC + LLM + TTS 파이프라인 전체를 동일 비중으로.
- RN 포지션 지원 시: 클라이언트(앱 구조, Native Module, 실시간 UX, 통화 화면 상태관리)를 메인, 백엔드는 "협업 이해를 위해 직접 구현" 수준으로 보조.

---

## 8. 리스크 및 제약

- Gemini 무료 등급 속도 제한 + 추론 모델 지연(첫 청크 19~40초+) → 데모용으로 충분, 실서비스 수준 아님을 명시. 타임아웃은 넉넉히.
- 외부 LLM 모델명은 예고 없이 서비스 종료될 수 있음(실제로 `gemini-2.0-flash`가 404 시작) — graceful degrade가 에러를 삼키므로 주기적으로 실키 점검 / 에러 로그 모니터링 필요.
- 음성 통화 전체 왕복은 LiveKit Cloud + Google Cloud STT/TTS 자격증명이 있어야 검증 가능. `livekit-server` SDK 호출부는 `javap` 시그니처 확인만 됐고 런타임 동작은 미검증.
- 로컬 음성 통화 개발엔 ngrok 등 터널 필수(LiveKit Cloud가 `localhost`로 못 붙음).
- 완전한 A안(Python 사이드카)까지 가면 리포에 런타임(Python)이 하나 추가 — 배포 파이프라인에 영향.
- 실무 대비 격차는 존재 — "직접 만들어봄"의 증거이지 실서비스 운영 경험을 대체하진 못함.

---

## 9. 범위에서 제외한 것 (Out of Scope)

- **영상 통화** — 음성만으로 WebRTC 연동 증명에 충분.
- **완전한 양방향 실시간 음성**(서버 합성 음성을 WebRTC로 실시간 되쏘기) — "축소판 A안"으로 대체. Python 사이드카 스켈레톤은 리포에 있으나 미완·미검증(선택적 확장).
- **다중 대화 세션 관리** — 캐릭터당 1개 대화.
- **정식 유저 인증(JWT/OAuth)** — 디바이스 ID 익명 세션으로 최소화([[ADR-005]]).
- **TURN 서버** — LiveKit Cloud가 자체 처리하므로 직접 구축 불필요. (직접 WebRTC였다면 STUN까지만 하고 TURN은 제외했을 것.)
- **스토어 정식 출시** — TestFlight + Google Play 내부 테스트까지가 목표선.
- **Fastlane / 상시 클라우드 배포 / CD 파이프라인** — 계정 확보 후로 보류([[ADR-007]]).
- **풀 듀플렉스 음성 대화**(동시 말하기 / 끼어들기 감지) — 턴제로 단순화.
- **DeepLink** — Push(FCM) + Streaming(WebSocket)으로 "핵심 기능" 요건 충족, 보너스로만.

> v2 시점에는 음성 기능(STT/TTS)과 WebRTC 통화가 Out of Scope였으나 v3에서 핵심(3.9)으로 편입됐고, final에서 "축소판 A안"으로 실제 WebRTC 미디어 경로까지 구현됐다.
