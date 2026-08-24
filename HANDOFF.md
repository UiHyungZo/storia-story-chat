# HANDOFF

다음 세션(또는 다른 작업자)이 이 프로젝트를 이어받을 때 필요한 현재 상태 요약. 요구사항 전체는 [`PRD/Storia_PRD_v3.md`](./PRD/Storia_PRD_v3.md), 구조는 [`docs/architecture/README.md`](./docs/architecture/README.md) 참고.

## 참고사항 (재개 전 반드시 확인)

- **git push 안 된 상태**: 로컬 `develop` 브랜치가 origin보다 커밋 여러 개 앞서 있음(1~3주차 작업 전부 미푸시). 이 머신에 GitHub 인증정보/`gh` CLI가 없어 push가 실패했었음 — origin에는 `develop` 브랜치 자체가 아직 없으므로 인증 해결 후 `git push -u origin develop`으로 최초 push 필요.
- **`docker compose`/`expo start`(실기기·시뮬레이터 실행)는 여전히 검증 전무**: docker compose, `expo start`/`run:ios` 둘 다 아직 실제로 띄워보지 않음. 실제 API 왕복, 화면 동작, WebSocket 스트리밍, 음성 통화 왕복은 전부 미검증.
- **(7주차 신규) `gradlew test`/`npm test`는 이제 실행·통과까지 확인함**: 백엔드 18개(H2 인메모리 DB로 MariaDB 없이도 동작), 클라이언트 17개(`jest-expo`). 다만 이건 단위/슬라이스 테스트 범위라 위의 "실제 API 왕복/화면 동작" 미검증과는 별개 — `gradlew bootRun` 자체를 실제로 띄워본 적은 여전히 없음(테스트가 스프링 컨텍스트를 로드하긴 하지만 `bootRun`과 동일한 경로는 아님).
- **이 머신엔 Docker가 없음**: `docker compose up`으로 MariaDB를 못 띄움 (Homebrew mysql 9.7.1이 이미 로컬 3306 포트를 점유 중이고 MariaDB는 미설치). 3주차 세션에서 이 문제로 DB 관련 실행 검증(히스토리 저장/복원)을 보류함 — 사용자가 명시적으로 이번엔 건너뛰기로 선택. 다음 세션에서 Docker Desktop을 설치하거나 `brew install mariadb`로 포트 3307에 별도 인스턴스를 띄워서 검증할 것.
- **`GEMINI_API_KEY` 미설정**: 아직 키를 발급받지 않음. 백엔드를 띄우기 전에 `export GEMINI_API_KEY=...` 필요 (없으면 채팅은 되지만 고정 안내 문구만 돌아옴).
- **Firebase 프로젝트 없음**: 4주차 FCM 푸시는 백엔드 스켈레톤만 구현됨(사용자가 명시적으로 선택). 서비스 계정 JSON/`GoogleService-Info.plist`/APNs 키가 있는 실제 Firebase 프로젝트가 생기기 전까지는 실 발송/클라이언트 SDK 연동 둘 다 불가능.
- **이 머신엔 Xcode(전체 설치)와 CocoaPods도 없음**: Command Line Tools만 있어 `pod`가 없고 `xcodebuild`도 활성 개발자 디렉토리가 CLT라 동작 안 함. 4주차 Native Module(`modules/storia-native`)은 `npx expo prebuild --platform ios --no-install`로 `ios/` 골격이 생성되는 것까지만 확인했고, 실제 pod install/컴파일/기기 실행은 미검증. 5주차 LiveKit RN SDK/오디오 재생(`expo-audio`)도 같은 이유로 기기 검증 못 함.
- **`TTS_API_KEY`/`STT_API_KEY`/`LIVEKIT_*`/`SENTRY_DSN`/`EXPO_PUBLIC_SENTRY_DSN` 전부 미설정**: Google Cloud TTS/STT 키, LiveKit Cloud 프로젝트, Sentry 프로젝트 어느 것도 아직 없음. `LIVEKIT_*` 없으면 `/api/calls/**`가 즉시 `503`을 반환(음성 통화 자체를 시작 못 함), `TTS_API_KEY` 없으면 `/api/messages/{id}/audio`가 404를 반환하고 클라이언트가 텍스트만 남기고 다음 턴으로 넘어가는 폴백까지는 코드로 확인함 — 그 외 실제 왕복은 전부 미검증.
- **로컬 개발 시 ngrok 등 터널이 필요함(신규, 5주차)**: LiveKit Cloud(원격 서비스)가 이 백엔드의 `/egress/audio`로 다시 접속해와야 하므로, `LIVEKIT_EGRESS_AUDIO_WS_URL`이 `localhost`면 절대 동작 안 함 — 공인 접근 가능한 주소(ngrok 터널 등)여야 함. 아래 "다음 작업" 참고.
- **`apps/client/node_modules`는 타입체크 검증용으로 로컬에만 설치함**: `.gitignore` 처리되어 커밋엔 영향 없음. 새 환경/재클론 시 `npm install` 다시 필요.
- 상세 검증 체크리스트는 [`TODO.md`](./TODO.md)의 "검증 필요"/"남은 작업" 섹션(1~5주차 각각) 참고.

## 현재 상태 (2026-08-24 기준)

PRD v3 마일스톤 **1~5주차 코드 작성 완료, 로컬 실행 검증은 아직 안 함** (이번 세션들은 docker/서버 기동 없이 코드만 작성 — 3주차는 Docker가 없어 DB 검증을, 4주차는 Xcode/CocoaPods/Firebase 프로젝트가 없어 네이티브 빌드·FCM 실 발송 검증을, 5주차는 같은 이유로 LiveKit/오디오 재생 기기 검증과 LiveKit·STT·TTS 자격증명 미발급으로 실 통화 왕복 검증을 각각 사용자가 명시적으로 보류함). **5주차는 세션 중 사용자와 상의해 범위가 원래 계획(B안만)보다 넓어짐** — 아래 참고.

- **백엔드**: Spring Boot 3.x + JPA. `User`/`Character`/`Conversation`/`Message` 엔티티, 캐릭터 3종 시딩(`CharacterSeeder`), Swagger UI, `WebConfig`(로컬 개발용 CORS 전체 허용, `/api/**`).
  - REST: `GET /api/characters`, `GET /api/conversations/{characterId}/messages`, `POST /api/conversations/{characterId}/messages`(유저 메시지 저장 + Gemini 동기 호출로 어시스턴트 응답까지 한 번에 반환 — WS 실패 시 폴백 경로).
  - WebSocket(STOMP) — `WebSocketConfig`(`/ws` 엔드포인트, SockJS 없이 raw STOMP), `ConversationStompController`(`/app/conversation/{characterId}/send` 수신 → Gemini 스트리밍 청크를 `/topic/conversation/{characterId}`로 발행). `GeminiService`가 WebClient(SSE)로 Gemini `streamGenerateContent` 호출 — `GEMINI_API_KEY` 환경변수 필요, 없으면 WS는 ERROR 이벤트, REST는 고정 안내 문구로 우아하게 저하됨.
  - 3주차에 백엔드 변경 없음 (안정성/동기화 작업은 전부 클라이언트 범위).
- **클라이언트**: 캐릭터 목록/채팅방 화면이 실제 백엔드를 호출. `src/api/`: `config.ts`(base URL 자동 분기), `deviceId.ts`(AsyncStorage UUID), `client.ts`(fetch 래퍼), `characters.ts`/`conversations.ts`(REST DTO 매핑), `websocket.ts`(`@stomp/stompjs` 기반 STOMP 클라이언트).
  - **신규(3주차)**: `websocket.ts`가 stompjs 내장 재연결(`reconnectDelay`/`maxReconnectDelay`/`reconnectTimeMode: EXPONENTIAL`)을 켜서, 연결이 살아있다가 끊기면 지수 백오프로 자동 재연결하고 재연결마다 구독을 다시 검. `onConnectionStateChange` 콜백으로 connected/reconnecting 상태를 노출.
  - `useConversationStore`가 화면 진입 시 WS 연결을 1회 시도(기존과 동일)하지만, 전송할 때마다 캐시된 transport 값이 아니라 `isSocketConnected()`로 실제 연결 상태를 확인해 결정 — 재연결 대기 중엔 그 메시지만 REST로 개별 폴백하고, 소켓이 살아나면 다음 전송부터 자동으로 다시 스트리밍. `disconnect` 시 `transportByCharacterId`도 초기화해 다음 화면 진입 때 WS를 다시 시도함 (이전엔 앱 생애주기 동안 한 번 rest로 굳어지면 다시 시도 안 하던 버그였음).
  - 로딩/오류/재시도 UI: `CharacterListScreen`/`ChatRoomScreen`에 오류 배너 + "다시 시도" 버튼, 메시지 전송 실패 전용 배너(탭하면 같은 내용 재전송, draft 유실 없음), WS 재연결 중 배너.
  - 로컬 캐시: `src/storage/cache.ts`(AsyncStorage 기반 read/write 래퍼) — 캐릭터 목록/대화별 메시지를 fetch 성공 시 write-through, 로드 시작 시 먼저 캐시로 하이드레이션 후 백그라운드 fetch. 네트워크 실패해도 화면엔 마지막으로 본 데이터가 남아있음.
  - 부수 수정: WS 전송이 연결 끊김으로 중간에 REST로 폴백할 때 낙관적으로 추가했던 로컬 사용자 메시지를 제거하지 않아 REST 응답과 중복 렌더링되던 버그를 고침.
- **DB**: 로컬 MariaDB(`docker-compose.yml`, 포트 3307). PostgreSQL → MariaDB 마이그레이션 완료 (커밋 `395b2ca` 이후). **이 머신엔 Docker가 없어 3주차 세션에서 DB 관련 검증(히스토리 저장/복원)을 못 함** — 코드 레벨로는 `ConversationService`/`MessageRepository`가 `createdAt` 오름차순으로 저장/조회하는 것만 확인.
- **신규(4주차) — Native Module**: `apps/client/modules/storia-native/`에 로컬 Expo 모듈로 추가. 클래식 `RCTBridgeModule` 패턴(`HapticNotifierModule.swift` + `HapticNotifierModule.m`의 `RCT_EXTERN_MODULE`/`RCT_EXTERN_METHOD`) — Expo Modules API(`Module` 클래스)가 아니라 PRD가 명시한 그대로. `NativeModules.HapticNotifier.notify(title, body)`로 노출되며, 호출 시 `UINotificationFeedbackGenerator`로 햅틱 + `UNUserNotificationCenter`로 **포그라운드에서도 보이는** 로컬 알림을 띄움(`UNUserNotificationCenterDelegate.willPresent`에서 `.banner` 옵션 반환). `useConversationStore`가 어시스턴트 응답 도착 지점(WS `onDone`, REST 성공) 두 곳 모두에서 `notifyAssistantReply`를 호출.
  - 이 프로젝트는 `ios/`를 커밋하지 않고 `expo prebuild`로 재생성하는 구조(`.gitignore`의 `/ios`, `/android`)라서, `ios/` 안에 직접 Swift 파일을 넣는 방식은 다음 prebuild 때 사라짐 — 그래서 Expo가 기본으로 찾는 `./modules` 경로에 로컬 모듈로 얹음 (`expo-modules-autolinking`이 자동 발견해서 CocoaPod으로 링크, 별도 config plugin 불필요).
  - Android는 PRD 9절에서 명시적으로 범위 밖(`Android Native Module (Kotlin)` 제외) — `expo-module.config.json`도 `"platforms": ["ios"]`만 선언.
- **신규(4주차) — FCM 원격 푸시**: **백엔드만** 구현(사용자가 "백엔드 스켈레톤만 먼저" 선택). `firebase-admin` 의존성, `FcmProperties`/`FirebaseConfig`(`FIREBASE_CREDENTIALS_PATH` 미설정 시 조용히 비활성화 — `GEMINI_API_KEY`와 동일한 graceful-degradation 패턴), `PushNotificationService#sendNewMessage`. `User.fcmToken` 컬럼 + `PUT /api/devices/token`(`DeviceController`/`UserService`)으로 토큰 등록. `ConversationService#postAssistantMessage`(WS/REST 두 경로가 공유하는 지점)에서 매 어시스턴트 응답마다 자동 발송 시도. 클라이언트 쪽 `@react-native-firebase` 연동은 실제 Firebase 프로젝트가 생긴 뒤로 보류.
- **신규(5주차) — 음성 통화, "축소판 A안"**: 세션 중 사용자가 "A안까지 가면 더 빠르지 않냐"고 제안 → 리서치 스파이크로 실현 가능성/비용 확인(무료로 가능) → 완전한 양방향 실시간 A안 대신 아래처럼 축소한 중간 지점으로 확장 구현. 상세 흐름은 [`docs/architecture/README.md`](./docs/architecture/README.md) 시퀀스 다이어그램, 결정 배경은 `docs/decisions.md` ADR-004 갱신 1/2 참고.
  - 클라이언트 ↔ **LiveKit**(Cloud) 구간은 실제 WebRTC로 마이크 오디오 전송. 서버는 WebRTC 미디어를 직접 다루지 않고 LiveKit **Track Egress → WebSocket**(raw PCM, `pcm_s16le`)으로 오디오를 받아 배치 STT/Gemini/TTS(B안 그대로) 파이프라인에 흘려보냄 — 별도 클라우드 스토리지 불필요(Egress의 WebSocket 직접 출력 옵션 사용).
  - **백엔드 신규**: `io.livekit:livekit-server:0.15.0`(정확한 API가 문서에 잘 없어 실제 jar를 `javap`으로 디컴파일해 시그니처 확인 후 작성 — 1회 컴파일 성공). `LiveKitProperties`(`LIVEKIT_HOST`/`LIVEKIT_API_KEY`/`LIVEKIT_API_SECRET`/`LIVEKIT_EGRESS_AUDIO_WS_URL`), `voice/` 패키지(`VoiceCallService`/`VoiceTurnSession`/`VoiceTurnRegistry` — 턴 상태는 DB 아닌 인메모리), `VoiceEgressWebSocketHandler`(STOMP와 별개인 raw WebSocket, `/egress/audio` — `VoiceEgressWebSocketConfig`로 등록), `SttProperties`/`SttService`(Google Cloud Speech-to-Text 배치 인식, `STT_API_KEY`), `VoiceCallController`(`POST /api/calls/{characterId}/token`, `POST /api/calls/{characterId}/turns`, `GET /api/calls/turns/{turnId}`). `TtsProperties`/`TtsWebClientConfig`/`TtsService`(Google Cloud TTS REST)는 원래 B안 구현 그대로 재사용 — `GET /api/messages/{id}/audio`가 그때그때 합성해 반환(별도 저장소 없음, 반복 요청마다 재합성).
  - **클라이언트 신규**: `@livekit/react-native` + `livekit-client` + `@livekit/react-native-webrtc` + config plugin(`@livekit/react-native-expo-plugin`, `@config-plugins/react-native-webrtc`) — `App.tsx` 최상단에서 `registerGlobals()` 1회 호출. `src/api/calls.ts`(토큰 발급/턴 시작/상태 폴링). `useVoiceCallStore`는 LiveKit `Room`을 직접 다룸(`room.localParticipant.setMicrophoneEnabled()`로 마이크 트랙 publish/unpublish, unpublish가 "턴 종료" 신호) — 예전에 썼던 클라이언트 사이드 STT(`expo-speech-recognition`)는 완전히 제거함(서버가 오디오를 직접 받으므로 불필요해짐). `VoiceCallOverlay`/`ChatRoomScreen`의 "📞 통화" 버튼은 그대로(스토어 인터페이스 유지).
  - 메시지 송수신 자체는 여전히 **새 실시간 채널 없이 기존 텍스트 채팅 REST 경로 재사용**(`useConversationStore#sendMessageViaRest` — WS 스트리밍은 응답 완료를 기다리지 않고 바로 resolve되므로 음성 통화엔 못 씀, 이 결정 자체는 B안 때와 동일하게 유지).
- **6주차(C안 WebRTC 최소 데모) → 재평가 완료, 별도 데모 없이 종료**: 5주차에서 이미 클라이언트↔서버 실제 WebRTC 연결(LiveKit)을 구현했으므로, C안이 원래 증명하려던 "WebRTC 연동 경험"은 선행 충족됐다고 판단. `react-native-webrtc` 기반 수동 시그널링 데모는 추가로 만들지 않기로 결정(판단 근거는 `docs/decisions.md` ADR-004 갱신 3 참고) — 6주차는 코드 작업 없이 문서 정리로 종료됨.

## 다음 작업 (바로 이어서 할 것)

**아직 실행 검증이 안 됐다** — docker compose up(또는 이 머신처럼 Docker가 없으면 `brew install mariadb`로 포트 3307에 별도 기동), `GEMINI_API_KEY` 환경변수 설정 후 `gradlew bootRun`, `expo start`를 순서대로 띄워서 다음을 확인할 것:

- 캐릭터 목록 조회 → 채팅방 진입 → 메시지 전송 → 스트리밍 타이핑 효과 → 히스토리 복원까지 왕복.
- 백엔드를 내려서 WS 연결 실패를 강제한 뒤 REST 폴백(전체 응답 한 번에 반환)이 동작하는지.
- **(3주차 신규)** 채팅 도중 백엔드를 잠깐 내렸다 올려서, 재연결 중 배너가 뜨고 그동안 전송이 REST로 개별 폴백되며, 재연결 성공 후 다시 스트리밍되는지.
- **(3주차 신규)** 오프라인으로 앱을 재시작해 AsyncStorage 캐시로부터 캐릭터 목록/채팅 히스토리가 즉시 보이는지.
- **(3주차 신규)** 메시지 전송 실패 배너 재시도, 목록/채팅방 로드 실패 배너 재시도가 정상 동작하는지.
- `@stomp/stompjs`가 RN(Hermes)에서 폴리필 없이 붙는지 (문제 시 `TextEncoder`/`TextDecoder` 폴리필 검토).
- 실기기 테스트라면 `apps/client/.env`에 `EXPO_PUBLIC_API_BASE_URL=http://<개발머신 LAN IP>:8080` 설정 필요 (WebSocket URL도 이 값에서 `http`→`ws`로 자동 치환됨).
- 백엔드에 전역 예외 처리기가 없어 잘못된 `characterId` 등은 500으로 노출됨 (`docs/api.md` Error Handling Policy 참고) — 계속 범위 밖으로 남겨둠.
- **(4주차 신규)** Firebase 프로젝트 생성 → 서비스 계정 JSON(`FIREBASE_CREDENTIALS_PATH`) + iOS 앱 등록(`GoogleService-Info.plist`) + APNs Auth Key 준비 → 클라이언트에 `@react-native-firebase/app`+`/messaging` 추가 → 발급받은 토큰을 `PUT /api/devices/token`으로 전송 → 앱 백그라운드/종료 상태에서 실제 FCM 푸시 오는지 확인.
- **(4주차 신규)** Xcode(전체)+CocoaPods 있는 환경에서 `npx expo prebuild`/`npx expo run:ios`로 `storia-native` 로컬 모듈이 정상 링크·컴파일되는지, 실제로 Haptic + 포그라운드 로컬 알림이 뜨는지 확인.
- **(5주차 신규)** LiveKit Cloud 프로젝트 생성(무료 티어) → `LIVEKIT_HOST`/`LIVEKIT_API_KEY`/`LIVEKIT_API_SECRET` 백엔드에 주입.
- **(5주차 신규)** 로컬 개발용 ngrok 등 터널 준비(LiveKit Cloud가 `localhost`로 못 붙으므로 필수) → 그 공인 주소로 `LIVEKIT_EGRESS_AUDIO_WS_URL` 설정.
- **(5주차 신규)** Google Cloud STT/TTS API 키(`STT_API_KEY`/`TTS_API_KEY`) 발급 → 백엔드에 주입 → `GET /api/messages/{id}/audio`가 실제로 mp3를 반환하는지 확인(`ko-KR-Standard-A` 보이스가 유효한지도 — Cloud TTS 콘솔에서 재확인 필요할 수 있음).
- **(5주차 신규)** Xcode+CocoaPods 있는 환경에서 `expo prebuild`/`expo run:ios`로 LiveKit RN SDK(`@livekit/react-native`, `@livekit/react-native-webrtc`)가 정상 링크·컴파일되는지 확인. `expo-doctor`가 "Unsupported on New Architecture" 경고를 띄울 수 있는데, LiveKit 쪽에서 실제로는 동작하는데 메타데이터만 안 맞는 알려진 이슈라고 함 — 실제 동작 여부로 판단할 것.
- **(5주차 신규)** 실제 통화 왕복: 통화 버튼 → 토큰 발급(`POST /api/calls/{characterId}/token`) → LiveKit room 연결 → 마이크 트랙 publish → `POST /api/calls/{characterId}/turns`로 egress 시작 → 말하고 트랙 unpublish → `GET /api/calls/turns/{turnId}` 폴링 → STT 텍스트 정확도 → TTS 오디오 재생까지 확인.
- **(5주차 신규)** `livekit-server` SDK 호출부(`VoiceCallService`의 `AccessToken` grant 구성, `EgressServiceClient#startTrackEgress`)가 컴파일은 됐지만(jar를 `javap`으로 시그니처만 확인) 실제 LiveKit 서버와의 런타임 동작은 미검증 — 특히 `CanPublish`/`CanSubscribe` 그랜트가 기대한 대로 동작하는지.
- **(5주차 신규)** LiveKit/STT/TTS 전부 미설정 상태(지금 이 머신 그대로)에서 통화 버튼을 눌러 `503`이 뜨고 클라이언트가 적절히 에러 처리하는지, TTS만 미설정인 경우 오디오 없이 텍스트 응답만 오고 자동으로 다음 턴(idle)으로 넘어가는 폴백이 매끄러운지 확인.

6주차(WebRTC 최소 데모 C안)는 위 재평가에 따라 코드 작업 없이 종료됨 — 다음은 바로 7주차(모니터링/배포)이며, 5주차 확장 때문에 원래 계획에 없던 LiveKit/STT/TTS 배포 시크릿·`/egress/audio` 라우팅 확인 항목이 새로 추가됨. 상세는 [`TODO.md`](./TODO.md) 7주차 참고.

**7주차 착수 — 테스트 코드부터 시작함(외부 자원 없이 이 머신에서 바로 실행 가능해서 우선순위를 높게 잡음)**: 백엔드 18개(`./gradlew test`)/클라이언트 17개(`npm test`) 전부 실제로 통과 확인함. 그 과정에서 **`ObjectMapper` 빈 부재로 `gradlew bootRun`이 애초에 기동조차 못 했을 실제 버그**를 발견해 고침(`config/JacksonConfig.java` 신규) — Spring Boot 4의 모듈화 스타터 구성 때문에 classic Jackson `ObjectMapper` 자동 빈 생성이 안 되고 있었음. 클라이언트는 `jest-expo`를 신규 도입했고, 3주차에서 고쳤던 두 버그(WS 폴백 시 메시지 중복, `disconnect()` 후 transport 미초기화)에 대한 회귀 테스트도 새로 작성함. 상세는 `TODO.md` 7주차 참고.

이어서 **Sentry 연동**(클라이언트 + 백엔드)도 완료. DSN 미설정 시 SDK가 스스로 비활성화되는 게 표준 동작이라 다른 외부 연동과 동일한 graceful-degradation 패턴을 그대로 따름 — 새 가드 코드 불필요. 백엔드는 Spring Boot 4 전용 아티팩트(`io.sentry:sentry-spring-boot-4`, 구버전 가이드의 `sentry-spring-boot-starter-jakarta`는 Spring Boot 3용이라 안 맞음)를 웹 검색으로 확인 후 사용. 클라이언트는 설치 시점에 `@sentry/react-native`의 Expo SDK 57 호환성 이슈(GitHub #6384)가 열려있는 걸 먼저 확인했지만, 실제 설치·타입체크·테스트가 전부 문제없이 통과해 그대로 채택함. 실제 Sentry 프로젝트/DSN이 없어 이벤트 도착 자체는 미검증.

## 중요한 결정 사항 / 함정

- **MariaDB 예약어 회피**: `User` → `app_user` 테이블, `Character` → `story_character` 테이블로 매핑. 새 엔티티 추가 시 MariaDB/MySQL 예약어(`USER`, `CHARACTER`, `GROUP`, `ORDER` 등)와 충돌하는 이름은 `@Table(name = ...)`로 명시적으로 회피할 것.
- **로컬 DB 포트는 3307**: 로컬 머신에 Homebrew mysqld가 이미 `127.0.0.1:3306`을 점유하고 있어서 docker-compose가 `3307:3306`으로 매핑됨. `application.yml`의 JDBC URL도 `localhost:3307` 기준. 다른 환경에서 세팅할 때 3306이 비어있다면 그대로 3307을 써도 되고, 필요하면 포트를 맞춰 바꿔도 무방 (배포 환경에서는 무관).
- **DB 인증 방식**: `jdbc:mariadb://...?allowPublicKeyRetrieval=true&useSSL=false` 옵션은 로컬 개발용. 배포 시 SSL 설정 재검토 필요.
- **디바이스 ID 기반 익명 세션**: 정식 로그인/JWT 없음. `X-Device-Id` 헤더로 유저 식별 (`ConversationController`). 클라이언트에서 디바이스 ID 생성/영속화 로직이 아직 없음 — REST 연동 작업 시 같이 필요.
- **캐릭터당 대화 1개 제약**: `Conversation(user_id, character_id)` UNIQUE — 여러 대화 세션 개념 없음 (PRD상 의도된 범위 밖 항목).
- **음성 통화 기능 범위 (5주차에 갱신됨)**: PRD 3.9 원래 계획은 B안(WebRTC 미사용)+C안(별도 WebRTC 데모)이었으나, 5주차 세션 중 "축소판 A안"(클라이언트↔LiveKit은 실제 WebRTC, 서버는 Track Egress로 오디오만 받아 배치 STT/TTS 처리)으로 확장 구현됨 — `docs/decisions.md` ADR-004 갱신 2, `docs/architecture/README.md` 참고. 서버가 합성 음성을 WebRTC로 실시간 되쏘는 완전한 양방향(원래 정의의 A안)은 여전히 시간 여유 시 확장 목표 — 처음부터 그렇게 설계하지 말 것.
- **잘 문서화되지 않은 서드파티 SDK는 `javap`로 실제 클래스를 확인하고 코드를 짤 것**: `io.livekit:livekit-server`는 공식 문서/웹 검색만으로는 정확한 메서드 시그니처(`EgressServiceClient#startTrackEgress` 등)를 확인할 수 없었음. Gradle이 받아둔 jar를 `unzip -l`로 클래스 목록 확인 후 `javap <클래스>`로 실제 시그니처를 뽑아서 그걸 기준으로 작성 → 1회 컴파일 성공. 비슷하게 애매한 SDK를 다룰 때 이 방법을 우선 고려할 것 (경로: `find ~/.gradle/caches/modules-2/files-2.1 -iname "*<artifact>*.jar"`).
- **LiveKit Track Egress는 WebSocket으로 raw PCM을 직접 스트리밍하는 옵션이 있음**: 파일로 저장해 S3 등 클라우드 스토리지로 보내는 방식(`DirectFileOutput`) 대신, `startTrackEgress(room, websocketUrl, trackId)`로 시작하면 별도 스토리지 계정 없이 우리 백엔드가 직접 오디오를 받을 수 있음 — 다만 로컬 개발 시 LiveKit(Cloud, 원격)이 그 websocketUrl로 다시 접속해와야 하므로 `localhost`는 안 되고 ngrok 등 터널이 필요함.
- **Gemini API 키**: `GEMINI_API_KEY` 환경변수로 주입(`application.yml`의 `gemini.api-key`). 커밋된 파일에는 키가 없음 — 로컬에서 `export GEMINI_API_KEY=...` 하고 백엔드를 띄울 것. 모델명은 `gemini.model`(기본 `gemini-2.0-flash`)로 분리해뒀으니 모델이 바뀌면 `application.yml`만 수정하면 됨.
- **WebClient는 MVC 앱에 부분 도입**: `spring-boot-starter-webflux`(전체 리액티브 스택) 대신 `spring-webflux` + `reactor-netty-http`만 추가해 WebClient만 사용. 앱은 여전히 Servlet(MVC) 스택 — REST 폴백 컨트롤러에서는 `.block()`으로 동기 변환해서 씀 (포트폴리오 스코프에서 허용 가능한 트레이드오프, `docs/decisions.md` ADR-006 참고).
- **Native Module은 `ios/`가 아니라 `modules/storia-native`에 둘 것**: 이 프로젝트는 `expo prebuild`로 `ios/`를 매번 재생성하는 continuous native generation 방식이라(`.gitignore`의 `/ios`), `ios/` 안에 직접 넣은 네이티브 코드는 다음 prebuild 때 사라진다. 새 네이티브 코드가 더 필요해지면 반드시 `./modules` 아래 로컬 모듈로 추가할 것 (`expo-modules-autolinking`이 기본으로 찾는 경로 — `apps/client/node_modules/expo-modules-autolinking/build/commands/autolinkingOptions.js`의 기본값 `./modules` 참고).
- **외부 자격증명 미설정 시 graceful degradation 패턴 통일**: `GEMINI_API_KEY`(Gemini), `FIREBASE_CREDENTIALS_PATH`(FCM), `TTS_API_KEY`(TTS), `STT_API_KEY`(STT) 모두 `*Properties#isConfigured()`로 설정 여부를 확인하고, 없으면 예외를 던지지 않고 조용히 비활성화(로그만 남김 / `null` 반환 / 404)하는 동일한 패턴을 따름. `SENTRY_DSN`/`EXPO_PUBLIC_SENTRY_DSN`(Sentry, 7주차)은 SDK 자체가 DSN 미설정 시 스스로 비활성화하는 게 표준 동작이라 별도 `isConfigured()` 코드 없이도 같은 결과. `LIVEKIT_*`만 예외 — 음성 통화 자체가 LiveKit 없이는 성립하지 않아서 `VoiceCallController`가 `503`을 명시적으로 반환함(조용히 no-op이 아니라 클라이언트가 즉시 알 수 있게). 새 외부 연동을 추가할 때도 "기능이 부분적으로 대체 가능하면 graceful degrade, 아예 불가능하면 명시적 에러"라는 기준을 유지할 것.
- **음성 통화는 새 실시간 채널을 만들지 않고 기존 REST를 재사용**: WS 스트리밍 경로(`sendMessage`)는 청크 콜백만 있고 "응답이 완전히 끝났다"는 시점을 기다리지 않고 resolve되므로, 오디오를 언제 가져올지 알 수 없어 음성 통화엔 못 씀. 대신 REST 전용 `sendMessageViaRest`를 신설해 재사용 — 새로운 실시간 파이프라인이 필요해 보여도 먼저 REST 재사용이 가능한지 검토할 것 (불필요한 WS 채널 증식 방지).
- **TTS는 미리 합성해 저장하지 않고 요청 시점에 합성**: `MessageService#synthesizeAudio`는 오디오를 DB/디스크에 캐싱하지 않고 `GET /api/messages/{id}/audio` 호출마다 매번 Google Cloud TTS를 다시 호출한다. 포트폴리오 스코프에서 허용한 트레이드오프(반복 재생 시 비용/지연 증가) — 프로덕션이라면 결과를 캐싱해야 함.

## 로컬 실행

`README.md`의 "로컬 실행" 섹션 참고 (DB → 백엔드 → 클라이언트 순).
