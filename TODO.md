# TODO

PRD v3([`PRD/Storia_PRD_v3.md`](./PRD/Storia_PRD_v3.md)) 마일스톤 기준. 완료된 항목은 커밋 로그 참고, 여기는 남은 작업만 추적.

## 지금 당장 (1주차 마무리 갭)

- [x] 클라이언트에서 실제 백엔드 REST API 호출로 전환 (`GET /api/characters`, `GET /api/conversations/{characterId}/messages`) — `dummyCharacters.ts` 제거, `src/api/` 계층 추가
- [x] 클라이언트 디바이스 ID 생성 및 영속화 (AsyncStorage) — `src/api/deviceId.ts`, `X-Device-Id` 헤더로 전송
- [x] 메시지 전송용 REST 엔드포인트 추가 (`POST /api/conversations/{characterId}/messages`) — `ConversationService#postMessage` + 컨트롤러 매핑
- [x] 백엔드 CORS 설정 — `WebConfig`(`/api/**` 전체 허용, 로컬 개발 전용)
- [x] **(2026-08-24 실행 검증 완료)** Docker Desktop 실행 → `docker compose up` → `gradlew bootRun` → `expo run:ios`(iOS 시뮬레이터, iPhone 15 Pro)까지 이 프로젝트 최초로 실제 기동 확인. 과정에서 실제 버그 하나 발견/수정(아래 참고).

### 검증 필요 (다음 세션, 로컬 실행 후)

- [ ] Android 에뮬레이터는 `10.0.2.2`, iOS 시뮬레이터는 `localhost`로 자동 분기(`src/api/config.ts`) — 실기기 테스트 시 `EXPO_PUBLIC_API_BASE_URL` 환경변수로 개발 머신 LAN IP를 넣어야 함 (자동 감지 불가). iOS 시뮬레이터 기준(`localhost`)은 검증됨, LAN IP 경로는 미검증.
- [x] **캐릭터 목록 → 채팅방 진입 → 메시지 전송 → 히스토리 복원까지 실제 왕복 확인함** (iOS 시뮬레이터). 이 과정에서 `useConversationStore.getMessages()`가 메시지 없을 때 `?? []`로 매번 새 배열을 반환해 Zustand 셀렉터 참조가 불안정해지고, 채팅방 진입 시 "Maximum update depth exceeded"로 **거의 항상 크래시하는 실제 버그**를 발견 — `EMPTY_MESSAGES` 상수로 고침(커밋 `e0ec8cb`). 앱 완전 종료 후 재시작 + 백엔드 완전 종료 후 재시작 두 경우 모두 DB에서 메시지가 정상 복원되는 것도 확인.
- [ ] CORS 설정이 실제로 필요했는지(RN 네이티브는 CORS 영향 없음, Expo 웹/브라우저 디버깅 시나리오만 해당) 확인 후 불필요하면 제거 검토

## 2주차 — Gemini 연동 & WebSocket

- [x] Google Gemini API 연동 (백엔드, 텍스트 스트리밍) — `GeminiService`(WebClient + SSE), `GEMINI_API_KEY` 환경변수 필요
- [x] WebSocket(STOMP) 채널 구축 — `/app/conversation/{characterId}/send` → `/topic/conversation/{characterId}` (`WebSocketConfig`, `ConversationStompController`)
- [x] 클라이언트 WebSocket 클라이언트 연동 + 스트리밍 청크 렌더링(타이핑 효과) — `src/api/websocket.ts`, `useConversationStore`의 `streamingByCharacterId`
- [x] REST 폴백 경로 구현 (WebSocket 실패 시) — `POST /api/conversations/{characterId}/messages`도 Gemini를 동기 호출해 (논스트리밍) 응답을 반환하도록 변경, 클라이언트는 캐릭터별로 화면 진입 시 WS 연결을 1회 시도하고 실패하면 그 세션 동안 REST로 전환
- [x] **(2026-08-24 부분 검증)** `GEMINI_API_KEY` 미설정 상태로 `gradlew bootRun` 실행 확인 — REST(`postMessage`)는 고정 안내 문구로 정상 폴백하는 것을 curl과 실제 앱 둘 다에서 확인. WS `ERROR` 이벤트 자체(`ConversationStompController`가 빈 응답 시 `StreamEvent.error(...)` 발행)는 코드로만 확인, 실제 이벤트 수신은 미검증 — 아래 참고.

### 검증 필요 (다음 세션, 로컬 실행 후 — 2주차)

- [ ] `GEMINI_API_KEY` 실제 키 발급 후 스트리밍 청크가 실시간으로 쌓여 타이핑 효과가 보이는지, 완료 시 최종 메시지로 치환되는지 확인 (키 없이는 WS 스트리밍 콘텐츠 자체가 발생하지 않아 미검증)
- [ ] 백엔드를 내려서 강제로 WS 연결 실패 상황을 만든 뒤 REST 폴백(전체 응답 한 번에 반환)이 정상 동작하는지 확인
- [x] `@stomp/stompjs`가 RN(Hermes) 환경에서 별도 폴리필 없이 붙음 — 실제 앱에서 폴리필 관련 크래시 없이 STOMP 핸드셰이크 자체는 정상 동작(curl로 `/ws`에 직접 Upgrade 요청 시 `101` 응답도 별도 확인).
- [x] **(2026-08-24 신규 발견)** 현재는 화면 진입 시 WS 연결을 1회만 시도하고 세션 내내 그 결과(ws/rest)를 유지하는데, 이 연결 시도에 4초 타임아웃(`CONNECT_TIMEOUT_MS`, `src/api/websocket.ts`)이 걸려있음 — 실제 앱을 콜드 스타트 직후 채팅방에 진입해 첫 메시지를 보낸 세션에서 이 타임아웃을 넘겨 **조용히 REST로 폴백된 것으로 추정**됨(DB에 저장된 응답 문구가 WS 경로의 에러 문구가 아니라 REST 전용 폴백 문구였음). 앱 시작 직후 첫 채팅에서 WS가 예상보다 자주 REST로 떨어질 수 있다는 뜻 — 타임아웃을 늘리거나 원인을 더 조사할 가치가 있어 보임. 스트리밍 도중 연결이 끊기는 시나리오의 재시도/재연결은 3주차 범위.

## 3주차 — 안정성 & 동기화

- [x] 로딩/오류/재시도 UI — `CharacterListScreen`/`ChatRoomScreen`에 오류 배너 + "다시 시도" 버튼 추가(각각 `loadCharacters`/`loadMessages` 재호출). 메시지 전송 실패는 별도 배너로 표시하고 탭하면 같은 내용으로 재전송(`sendError` 상태), draft 유실 방지.
- [x] WebSocket 재연결 로직 — `src/api/websocket.ts`가 `@stomp/stompjs`의 `reconnectDelay`/`maxReconnectDelay`/`reconnectTimeMode: EXPONENTIAL`로 드롭 후 지수 백오프 재연결을 켜고, 재연결마다 구독을 다시 검. `onConnectionStateChange`로 connected/reconnecting을 store에 반영해 `ChatRoomScreen`에 "재연결 중" 배너 표시. 재연결 대기 중에는 메시지 전송이 그때그때 REST로 개별 폴백하고, 소켓이 살아나면 다음 전송부터 자동으로 다시 WS 사용(캐시된 transport 대신 매 전송마다 실제 연결 상태를 확인하도록 변경). 화면 재진입 시에도 WS를 다시 시도하도록 `disconnect` 시 `transportByCharacterId` 초기화(이전엔 앱 생애주기 동안 한 번 rest로 굳어지면 다시 시도 안 하던 버그).
  - 부수적으로 발견/수정: WS 전송이 연결 끊김으로 실패해 REST로 폴백할 때 낙관적으로 추가해둔 로컬 사용자 메시지를 지우지 않아 REST 응답의 사용자 메시지와 중복 렌더링되던 버그 수정.
- [x] **(2026-08-24 실행 검증 완료)** DB 히스토리 저장/복원 — Docker Desktop 설치 후 `docker compose up`으로 MariaDB 기동, 앱에서 실제로 보낸 메시지가 `createdAt` 오름차순으로 저장/조회되는 것을 DB 직접 쿼리 + `GET /api/conversations/{id}/messages` 양쪽으로 확인. **백엔드 프로세스를 완전히 껐다 켠 뒤에도, 앱을 완전히 종료 후 재시작한 뒤에도** 히스토리가 그대로 살아있는 것까지 확인.
- [x] 로컬 캐시(AsyncStorage) 동기화 — `src/storage/cache.ts` 추가. `useCharacterStore`/`useConversationStore`가 로드 시작 시 캐시를 먼저 하이드레이션(즉시 렌더) 후 백그라운드로 fetch하고, 성공 시 캐시에 write-through(스트리밍 완료/REST 전송 성공 시점 포함). 네트워크 실패 시에도 캐시된 데이터는 화면에 남아 있음.

### 검증 필요 (다음 세션 — 3주차, DB 기동 가능한 환경에서)

- [x] Docker Desktop 설치 후 `docker compose up`으로 DB 히스토리 저장/복원 실제 확인 완료(위 참고).
- [ ] WS 재연결 배너: 이번 세션엔 테스트 대상 세션이 애초에 WS가 아니라 REST를 쓰고 있었던 것으로 추정돼(위 2주차 신규 발견 참고) 배너 자체는 못 띄워봄 — 실제로 WS 연결이 살아있는 상태에서 백엔드를 내렸다 올렸을 때 "재연결 중" 배너가 뜨고, 그동안 전송은 REST로 개별 폴백되며, 재연결 성공 후 다음 전송부터 다시 스트리밍되는지는 여전히 미검증.
- [ ] 메시지 전송 실패 배너를 탭했을 때 동일 내용으로 재전송되는지, 목록/채팅방 오류 배너의 "다시 시도"가 정상 동작하는지 확인
- [x] **(2026-08-24 실행 검증 완료)** 오프라인 상태로 앱을 재시작해 캐릭터 목록이 AsyncStorage 캐시로부터 즉시 보이는지 확인 — 백엔드를 완전히 끈 상태에서 앱을 완전히 재시작해도 캐릭터 3종이 캐시에서 즉시 렌더링됐고, 동시에 "fetch failed: ... Could not connect to the server" 오류 배너 + "다시 시도" 버튼도 정상적으로 함께 표시됨. ("다시 시도" 탭 자체의 동작은 탭 자동화가 없어 미검증.)

## 4주차 — Native Module & 푸시

- [x] Swift Native Module (Haptic + 로컬 알림) — `apps/client/modules/storia-native/`에 로컬 Expo 모듈로 추가(클래식 `RCTBridgeModule` 패턴: `HapticNotifierModule.swift` + `HapticNotifierModule.m`의 `RCT_EXTERN_MODULE`/`RCT_EXTERN_METHOD`). `NativeModules.HapticNotifier.notify(title, body)`로 노출되고, 호출 시 `UINotificationFeedbackGenerator`로 햅틱 + `UNUserNotificationCenter`로 포그라운드에서도 보이는 로컬 알림(배너)을 띄움. `useConversationStore`가 어시스턴트 응답이 도착하는 두 지점(WS `onDone`, REST `postMessage` 성공) 모두에서 호출.
  - **이 프로젝트는 `ios/`를 커밋하지 않고 `expo prebuild`로 매번 재생성하는 구조**라서, 네이티브 코드를 `ios/` 안에 직접 넣으면 다음 prebuild 때 사라짐. 대신 Expo가 기본으로 찾는 `./modules` 경로에 로컬 모듈로 얹어서 커밋되고 prebuild에도 살아남게 함 (`expo-modules-autolinking`이 자동으로 CocoaPod으로 링크).
  - **(2026-08-24 실행 검증 완료)** Xcode 26.4.0(전체)+CocoaPods가 이후 이 머신에 설치됨. `npx expo prebuild --platform ios --clean` → `expo run:ios`로 실제 pod install/컴파일/링크/시뮬레이터(iPhone 15 Pro) 실행까지 확인. 앱에서 실제로 어시스턴트 응답을 받을 때마다 `NativeModules.HapticNotifier.notify()`가 호출됐고(에러 로그 없음, JS단에서 정상 호출됨) 크래시 없이 통과 — 다만 실제 햅틱 진동/알림 배너가 화면에 뜨는 것 자체를 스크린샷으로 정확한 타이밍에 캡처하진 못해서 눈으로 직접 확인하진 않음.
- [x] **(신규, 오늘 추가) Kotlin Native Module (Android)** — PRD 9절에서 원래 범위 제외였던 항목을 뒤집고 구현(PRD 갱신 완료). `android/src/main/java/com/storianative/HapticNotifierModule.kt` + `HapticNotifierPackage.kt` — iOS와 대칭되는 클래식 `ReactContextBaseJavaModule`/`ReactPackage` 패턴, 동일한 `NativeModules.HapticNotifier.notify()` 브리지 이름을 노출해 `index.ts`가 플랫폼 분기 없이 호출. `Vibrator`/`VibratorManager`로 진동, `NotificationManagerCompat`으로 알림. `POST_NOTIFICATIONS`(API 33+) 권한은 iOS와 동일하게 첫 알림 시점에 lazy 요청. `expo-module.config.json`을 `"platforms": ["ios", "android"]`로 갱신.
  - **미검증**: 이 머신엔 Android SDK/에뮬레이터/`kotlinc`가 없어 실제 빌드·링크·동작 확인 못 함 — iOS와 동일한 상황.
- [x] FCM 원격 푸시 (백엔드 Admin SDK) — **백엔드만** 구현, 클라이언트 SDK 연동은 보류(아래 참고).
  - `firebase-admin` 의존성 추가, `FcmProperties`/`FirebaseConfig`(`FIREBASE_CREDENTIALS_PATH` 서비스 계정 JSON 경로 미설정 시 조용히 비활성화 — `GEMINI_API_KEY` 패턴과 동일), `PushNotificationService#sendNewMessage`.
  - `User.fcmToken` 컬럼 추가, `PUT /api/devices/token`(`DeviceController`/`UserService`)으로 클라이언트가 토큰을 등록.
  - `ConversationService#postAssistantMessage`(WS `DONE`/REST 응답 두 경로가 공유하는 지점)에서 매 어시스턴트 응답마다 자동으로 푸시 발송 시도.
  - `gradlew compileJava`/`compileTestJava` 통과 확인. Firebase 프로젝트가 없어 실제 발송은 미검증.
- [x] **(신규) `lastActiveAt` 트래킹 + 일일 재참여 푸시** — `User.lastActiveAt`이 엔티티엔 있었지만 생성 후 갱신되는 곳이 없던 버그를 발견, REST/WS 채팅 경로가 공유하는 `getOrCreateConversation`에서 매 요청마다 갱신하도록 수정. `ReEngagementScheduler`가 매일 3일 이상 미접속 유저에게 FCM 재참여 푸시를 발송, `User.reengagementPushSent`로 유휴 기간당 최대 1회만 보내고 유저가 돌아오면 다시 `false`로 리셋.

### 남은 작업 (다음 세션 — Firebase 프로젝트 준비된 뒤)

- [ ] Firebase 프로젝트 생성 → 서비스 계정 JSON 발급(`FIREBASE_CREDENTIALS_PATH`로 백엔드에 주입) + iOS 앱 등록(`GoogleService-Info.plist`) + APNs Auth Key 업로드
- [ ] 클라이언트에 `@react-native-firebase/app`+`/messaging` 추가, 알림 권한 요청 후 발급받은 토큰을 앱 시작 시 `PUT /api/devices/token`으로 전송하는 API 래퍼 작성(`src/api/devices.ts` 등, `characters.ts`/`conversations.ts` 패턴 참고)
- [ ] 앱을 백그라운드/종료 상태로 두고 메시지를 보내 실제 FCM 푸시가 오는지 확인
- [x] Xcode+CocoaPods 있는 환경에서 `npx expo prebuild`/`expo run:ios`로 `storia-native` 로컬 모듈 링크·컴파일 확인 완료(위 참고). 실제 햅틱 진동/알림 배너가 화면에 뜨는지 눈으로 직접 보는 것만 남음.
- [ ] **(신규)** Android SDK/에뮬레이터 있는 환경에서 `npx expo prebuild`/`expo run:android`로 `storia-native`의 Kotlin 모듈이 정상 링크·컴파일되는지, 진동 + 알림(API 33+ 권한 프롬프트 포함)이 뜨는지 확인 — 이 머신엔 여전히 Android SDK 없음

## 5주차 — 음성 통화, "축소판 A안"으로 확장

세션 중 사용자와 상의해 원래 계획(B안만)보다 범위를 넓힘: 진짜 WebRTC 연결까지 포함한
"축소판 A안"을 이번 주에 하고, 완전한 양방향 실시간(TTS를 다시 WebRTC로 되쏘는 것)은
시간이 남으면 확장하기로 함 — 리서치 스파이크로 실현 가능성/비용을 먼저 확인(무료로
가능, 비용 세부는 `docs/decisions.md` ADR-004 갱신 항목 참고).

- [x] **B안 (원래 계획)**: RN STT + 서버 TTS + 오디오 URL 재생 파이프라인. STT는 PRD/ADR-004가 명시한 `@react-native-voice/voice` 대신 `expo-speech-recognition`으로 시작했다가, **축소판 A안으로 대체하면서 서버 사이드 배치 STT로 전환** — 클라이언트 STT 관련 코드/패키지는 모두 제거함(아래 참고).
- [x] **서버 TTS**: Google Cloud TTS REST API(`texttospeech.googleapis.com/v1/text:synthesize`). `TtsProperties`/`TtsWebClientConfig`/`TtsService#synthesize`, `TTS_API_KEY` 미설정 시 `null` 반환(그레이스풀 디그레이드 — `GEMINI_API_KEY`/`FIREBASE_CREDENTIALS_PATH`와 동일 패턴). `GET /api/messages/{messageId}/audio`(`MessageController`/`MessageService`)에서 그때그때 합성해 반환 — 별도 오디오 저장소 없음.
  - **(신규)** 캐릭터별 TTS 보이스가 실제 유효한 Google Cloud TTS 값(`ko-KR-Standard-A/C/D`)으로 연결됨 — 기존 플레이스홀더 문자열("aria-voice" 등)은 애초에 유효한 API 값이 아니었음. 겸사겸사 죽은 `Character` 필드 제거 + 엔티티 문서화(Javadoc)도 정리.
- [x] **축소판 A안 — 실제 WebRTC 연결**: 클라이언트↔LiveKit(Cloud) 구간은 진짜 WebRTC로 마이크 오디오 전송. 백엔드는 WebRTC 미디어를 직접 다루지 않고 LiveKit **Track Egress → WebSocket(raw PCM)**로 오디오를 받아, 기존 B안의 배치 STT→Gemini→TTS 파이프라인에 그대로 흘려보냄. 상세 흐름은 [`docs/architecture/README.md`](./docs/architecture/README.md) 시퀀스 다이어그램 참고.
  - **백엔드**: `io.livekit:livekit-server:0.15.0` 의존성(실제 jar를 `javap`으로 디컴파일해 API 시그니처 확인 후 작성 — 문서화가 얇은 SDK라 웹 검색만으로는 부족했음). `LiveKitProperties`(host/apiKey/apiSecret/egressAudioWsUrl, `LIVEKIT_*` 미설정 시 비활성화), `voice/` 패키지(`VoiceCallService`, `VoiceTurnSession`/`VoiceTurnRegistry` — 턴별 오디오 버퍼는 DB 아닌 인메모리), `VoiceEgressWebSocketHandler`(STOMP와 별개인 raw WebSocket, `/egress/audio` — LiveKit이 여기로 접속해 PCM 프레임을 보냄), `SttProperties`/`SttService`(Google Cloud Speech-to-Text 배치 인식, `STT_API_KEY` 미설정 시 `null`), `VoiceCallController`(`POST /api/calls/{characterId}/token`, `POST /api/calls/{characterId}/turns`, `GET /api/calls/turns/{turnId}`).
  - **클라이언트**: `@livekit/react-native` + `livekit-client` + `@livekit/react-native-webrtc` + config plugin들(`@livekit/react-native-expo-plugin`, `@config-plugins/react-native-webrtc`) 설치. `src/api/calls.ts`(토큰 발급/턴 시작/상태 폴링), `useVoiceCallStore`를 LiveKit 기반으로 재작성(`room.localParticipant.setMicrophoneEnabled()`로 마이크 트랙 publish/unpublish, unpublish가 곧 "턴 종료" 신호). `VoiceCallOverlay`/`ChatRoomScreen`은 그대로(스토어 인터페이스 유지).
  - **왜 S3/클라우드 스토리지가 필요 없는가**: LiveKit Track Egress가 파일 저장 없이 **WebSocket으로 raw PCM을 직접 스트리밍**해주는 옵션을 지원해서(`pcm_s16le`, 보통 48kHz), 백엔드가 그 WS를 직접 받아 처리 — 별도 클라우드 스토리지 계정 불필요.
  - **(2026-08-24 부분 검증)** Xcode+CocoaPods가 이후 이 머신에 설치돼 LiveKit RN SDK(`@livekit/react-native`, `@livekit/react-native-webrtc`, `LiveKitExpoPlugin`)가 `expo run:ios` 빌드에서 실제로 정상 링크·컴파일되는 것까지 확인함(앱 자체가 정상 기동). `LIVEKIT_*`/`STT_API_KEY`/`TTS_API_KEY` 전부 여전히 미설정이라 실제 통화 왕복(토큰 발급~room 연결~STT~TTS)은 여전히 미검증. `gradlew compileJava`/`compileTestJava`(livekit-server jar를 `javap`으로 실제 확인하며 작성해 1회 컴파일에 성공), `tsc --noEmit` 통과만 확인.

### 남은 작업 (다음 세션 — 자격증명/환경 준비된 뒤)

- [ ] LiveKit Cloud 프로젝트 생성(무료 티어) → `LIVEKIT_HOST`/`LIVEKIT_API_KEY`/`LIVEKIT_API_SECRET` 백엔드에 주입
- [ ] 로컬 개발용 공인 접근 가능한 터널(예: ngrok) 준비 → `LIVEKIT_EGRESS_AUDIO_WS_URL`을 그 터널 주소로 설정 (LiveKit Cloud가 `localhost`로 못 붙기 때문 — HANDOFF.md 참고)
- [ ] Google Cloud STT/TTS API 키 발급 → `STT_API_KEY`/`TTS_API_KEY` 설정
- [x] Xcode+CocoaPods 있는 환경에서 `expo prebuild`/`expo run:ios`로 LiveKit RN SDK가 정상 링크·컴파일되는 것 확인 완료(위 참고)
- [ ] 실제 통화 왕복: 통화 버튼 → 토큰 발급 → room 연결 → 마이크 publish → egress 시작 → 말하고 unpublish → 폴링 → STT 텍스트 정확도 → TTS 오디오 재생까지 확인
- [ ] `livekit-server` SDK의 `startTrackEgress`/`AccessToken` grant 구성이 실제 LiveKit 서버 응답과 맞는지(`javap`로 시그니처만 확인했고 런타임 동작은 미검증) 확인
- [ ] **시간이 남으면 — 완전한 양방향 실시간(원래 정의의 A안) 확장**: 지금 축소판은 응답을 오디오 URL로 반환하고 클라이언트가 별도로 재생하는데, 이걸 서버가 합성한 TTS를 그 LiveKit 세션에 오디오 트랙으로 다시 publish해서 실시간으로 들려주는 것까지 가는 확장. 리서치 스파이크에서 확인한 핵심 리스크: 이 "서버가 라이브 세션에 오디오 되쏘기" 부분은 LiveKit도 보통 Python/Node **Agent SDK**로 처리하고, 순수 JVM(Spring Boot)에서 하는 표준 경로가 없음. 시도한다면:
  - 먼저 `io.livekit:livekit-server`(관리용 SDK)가 아니라 실시간 미디어 publish가 가능한 JVM 라이브러리가 있는지 재조사(현재는 없다고 판단했지만 확정은 아님)
  - **없다면 Python/Node 사이드카 방식이 유력** — Spring 안에 "붙이는" 게 아니라 별도 프로세스로 띄워서 REST로 통신하는 표준 마이크로서비스 패턴:
    - Python(또는 Node)에 LiveKit **Agents SDK**로 작은 서비스를 하나 만듦. 이 Agent가 room에 봇처럼 들어가서 유저 오디오를 실시간 구독 → STT → TTS 오디오를 그 세션에 다시 publish하는 것까지 SDK가 대부분 처리해줌 — "서버가 라이브 세션에 오디오 되쏘기"라는 가장 어려운 부분이 이미 해결된 상태로 옴
    - 캐릭터 시스템 프롬프트 조회, 메시지 히스토리 저장 같은 비즈니스 로직은 새로 안 짜고 **Agent가 지금 Spring Boot 백엔드의 기존 REST API(`/api/conversations/...`)를 그대로 호출**하면 됨 — 로직 중복 없음
    - 지금 손으로 짠 Track Egress + raw WebSocket(`/egress/audio`) 방식보다 오히려 **더 단순해질 가능성** — Agents SDK 자체가 이 문제(실시간 오디오 구독+되쏘기)를 위해 만들어진 프레임워크라서
    - 트레이드오프: 리포에 언어/런타임이 하나 더 늘어남(Python 또는 Node 프로세스를 별도로 설치·배포·관리) — 배포 파이프라인(7주차)에도 영향
  - 두 방법 다 부담되면 이번 포트폴리오 스코프에서는 축소판으로 마무리하고, README/기술 블로그에 "왜 완전한 A안은 다음 확장 과제로 남겼는지"를 트레이드오프로 명시(PRD 7절 포트폴리오 활용 방안과 동일한 패턴)

## 6주차 — WebRTC 최소 데모 (C안) → 재평가 결과: 별도 데모 없이 문서화로 종료

5주차에서 LiveKit으로 클라이언트↔서버 실제 WebRTC 연결(마이크 오디오 캡처, ICE, DTLS-SRTP, 트랙 publish)을 이미 구현해서, C안이 원래 증명하려던 "WebRTC 연동 경험" 자체는 이미 충족됨(원래 C안이 존재했던 이유는 "B안엔 WebRTC가 전혀 없다"는 공백을 메우려는 것이었는데, 그 공백 자체가 없어짐).

- [x] 5주차 결과로 C안 요구사항이 충분히 충족되는지 재검토 — **충족됨으로 판단, 추가 코드 없이 종료**. LiveKit이 Offer/Answer/ICE 교환을 SDK 내부에서 처리해 "수동 시그널링 프로토콜 구현" 코드 증거는 없지만, 인터뷰/이력서 관점에서는 "LiveKit(WebRTC SFU)으로 실 미디어 스트리밍 구현" 정도로 충분히 증명 가능하다고 판단. 남은 시간은 5주차 미검증 항목(실기기 테스트, LiveKit/STT/TTS 자격증명 연동) 검증에 우선 투입하기로 함(`docs/decisions.md` ADR-004 갱신 3 참고).
- [x] ~~(필요시) `react-native-webrtc` 기반 1:1 P2P 오디오 스트리밍 최소 데모~~ — 위 판단에 따라 진행 안 함. 나중에 "수동 시그널링을 직접 짜본 경험"을 굳이 더 증명하고 싶어지면 이 항목을 다시 열 것.
- [x] B안/축소판 A안과의 통합 상태 문서화 — `docs/decisions.md` ADR-004, `docs/architecture/README.md`, `HANDOFF.md`에 이미 반영됨. 완전한 A안 확장은 5주차 "남은 작업" 항목으로 남아있음.

## 7주차 — 모니터링 & 배포

- [x] **Sentry 연동 (클라이언트 RN SDK + 백엔드 Spring Boot SDK)** — 둘 다 DSN 미설정 시 SDK 자체가 조용히 비활성화(이벤트 전송 안 함, 예외도 안 던짐)되는 게 Sentry SDK의 표준 동작이라 별도 `isConfigured()` 가드 불필요, 기존 graceful-degradation 패턴과 자연스럽게 맞음.
  - **백엔드**: `io.sentry:sentry-spring-boot-4:8.50.1` — Spring Boot 4 전용 아티팩트로, 구버전 가이드에 나오는 `sentry-spring-boot-starter-jakarta`(Spring Boot 3용)를 그대로 쓰면 안 됨(웹 검색으로 확인). `application.yml`에 `sentry.dsn`(`SENTRY_DSN` 환경변수)/`sentry.environment`(`SENTRY_ENVIRONMENT`, 기본 `local`)/`sentry.traces-sample-rate` 추가.
  - **클라이언트**: `npx expo install @sentry/react-native`로 SDK 57 호환 버전(`~7.11.0`) 설치 — 설치 시점에 Expo SDK 57 호환성 관련 GitHub 이슈(#6384)가 열려있는 걸 먼저 확인했으나, 실제 설치·`tsc --noEmit`·`npm test` 전부 문제없이 통과해 그대로 채택. `app.json` plugins에 `@sentry/react-native` 자동 추가됨(`expo install`이 처리). `App.tsx`에서 `Sentry.init({ dsn, enabled: Boolean(dsn) })` 호출 + `Sentry.wrap(App)`으로 루트 컴포넌트 감싸 자동 계측(터치/네비게이션) 활성화. DSN은 `EXPO_PUBLIC_SENTRY_DSN` 환경변수.
  - **미검증**: 실제 Sentry 프로젝트/DSN이 없어 이벤트가 실제로 대시보드에 도착하는지는 확인 못 함 — 다음 세션에서 Sentry 프로젝트 생성 후 확인 필요.
- [x] **(신규) 전역 REST 예외 처리기** — `GlobalExceptionHandler`(`@RestControllerAdvice`)로 잘못된 `characterId` 등 처리 안 된 예외가 그대로 500으로 노출되던 문제 해결(아래 참고용으로 HANDOFF.md에 "범위 밖"으로 남아있던 항목이었음). `ResourceNotFoundException` 등을 상태 코드 + `{code, message}` 구조화된 응답으로 매핑, `docs/error-handling.md`에 엔드포인트별 에러 케이스 문서화. `GlobalExceptionHandlerTest` 추가. **(2026-08-24 실행 검증 완료)** curl로 실제 확인: 잘못된 characterId → `404 {"code":"NOT_FOUND",...}`, `X-Device-Id` 헤더 누락 → `400 {"code":"MISSING_HEADER",...}`, 유효성 검증 실패 → `400 {"code":"VALIDATION_ERROR",...}` — 전부 구조화된 응답으로 정상 동작.
- [ ] 에러 시나리오 검증 (WebRTC 연결 실패 포함 — LiveKit room 연결 실패, Track Egress WebSocket 끊김, 턴 타임아웃 등 5주차에서 새로 생긴 실패 지점 포함)
- [x] **테스트 코드 (백엔드 JUnit5+Spring Boot Test, 클라이언트 Jest) — 외부 자원(Docker/Xcode/계정) 없이 이 머신에서 실제로 실행·통과까지 확인함.**
  - **백엔드(18개, `./gradlew test`)**: `MessageRepositoryTest`(`@DataJpaTest` + H2 인메모리 — 3주차부터 미검증으로 남아있던 "메시지가 `createdAt` 오름차순으로 저장/조회되는지"를 처음으로 실제 실행 검증함), `ConversationServiceTest`/`CharacterServiceTest`/`MessageServiceTest`(Mockito 단위 테스트), `TtsServiceTest`/`SttServiceTest`(자격증명 미설정 시 WebClient를 아예 호출하지 않고 graceful degrade하는지 검증), `CharacterControllerTest`(`@WebMvcTest` + `@MockitoBean` — Spring Boot 4에서 `@MockBean`이 제거되고 `org.springframework.test.context.bean.override.mockito.MockitoBean`으로 바뀜, `javap`로 확인 후 사용). `com.h2database:h2`를 `testRuntimeOnly`로 추가하고 `src/test/resources/application.yml`로 테스트 시 datasource를 H2로 자동 오버라이드(테스트 클래스패스가 메인보다 우선순위가 높아 별도 프로파일 지정 없이 적용됨) — 로컬에 MariaDB가 없어도 전체 테스트 스위트가 항상 돌아가게 함(7주차 CI 파이프라인에도 그대로 재사용 가능).
    - **부수적으로 발견/수정한 실제 버그**: 원래 있던 `BackendApplicationTests.contextLoads()`가 DB 문제와 별개로 `NoSuchBeanDefinitionException: ObjectMapper`로 실패하고 있었음 — Spring Boot 4의 모듈화된 스타터(`webmvc`, `websocket`만 쓰고 구버전의 통짜 `web` 스타터를 안 씀) 구성에서는 classic Jackson `ObjectMapper` 빈이 자동 생성되지 않는데, `TtsService`/`SttService`가 이걸 생성자로 직접 주입받고 있어서 **`gradlew bootRun`을 했어도 애플리케이션이 아예 기동조차 못 했을 상황**이었음. `config/JacksonConfig.java`에 명시적 `@Bean ObjectMapper`를 추가해 해결 — 이번 세션 전까지 아무도 전체 컨텍스트를 실제로 띄워본 적이 없어서 드러나지 않았던 버그.
  - **클라이언트(17개, `npm test`)**: `jest-expo` preset 신규 도입(`package.json`에 `"test": "jest"` 스크립트, `"jest": {"preset": "jest-expo"}`), `tsconfig.json`에 `"types": ["jest"]` 추가. `avatarColorFor`(순수 함수), `config.ts`의 `API_BASE_URL` 플랫폼 분기(Android→`10.0.2.2`/iOS→`localhost`/env 우선), `useCharacterStore`(캐시 하이드레이션→fetch 덮어쓰기, 에러 처리), `useConversationStore`— 특히 **3주차에서 고쳤던 두 버그의 회귀 테스트**를 새로 작성: (1) WS 발행이 중간에 실패했을 때 낙관적으로 추가한 로컬 메시지가 REST 응답과 중복 렌더링되지 않는지, (2) `disconnect()` 후 `transportByCharacterId`가 초기화돼 다음 화면 진입 시 WS를 다시 시도하는지. `jest.mock(path)`(automock)이 아니라 명시적 factory로 모킹해야 함 — automock도 실제 모듈을 먼저 로드하려 시도해서, `AsyncStorage` 등 네이티브 모듈을 transitively import하는 파일(`api/conversations.ts`, `storage/cache.ts` 등)을 모킹할 때 "NativeModule: AsyncStorage is null" 에러로 테스트 자체가 안 뜨는 문제가 있었음(`jest.mock(path, () => ({...}))` 형태로 전환해 해결).
- [x] **GitHub Actions CI** (`.github/workflows/ci.yml`) — push/PR마다 백엔드(`./gradlew test`, H2라 DB 불필요)와 클라이언트(`npm ci` → `tsc --noEmit` → `jest --ci`)를 각각 별도 job으로 실행. 계정/시크릿이 전혀 필요 없는 순수 CI라 지금 바로 완성 가능했음 — 워크플로 안의 실제 커맨드를 이 머신에서 그대로 실행해 통과 확인(GitHub Actions 러너에서 직접 돌려본 건 아니지만 커맨드 자체는 동일하게 검증됨).
- [x] **백엔드 Dockerfile** (`apps/backend/Dockerfile`, `.dockerignore`) — multi-stage(`eclipse-temurin:17-jdk`로 `bootJar` 빌드 → `eclipse-temurin:17-jre` 런타임). **이 머신엔 Docker가 없어 `docker build` 자체는 미검증** — 파일 존재/참조 경로(`gradlew`/`settings.gradle`/`gradle/`)만 확인함.
- [ ] **Fastlane, 실제 클라우드 배포(GitHub Actions CD 포함), iOS/Android/백엔드 실배포는 보류** — Apple Developer 계정(인증서/프로비저닝 프로파일), AWS/GCP 계정, Firebase App Distribution 설정 등 실제 계정이 있어야 의미 있는 코드가 나옴(계정 없이 짜면 추측성 스켈레톤이라 검증도 안 되고 가치가 낮음). 클라우드에 실제로 배포하는 작업은 비용이 발생하고 되돌리기 어려운 변경이라 사용자 승인 없이 진행하지 않음 — 계정 준비되면 다음 세션에서 진행.
  - **클라우드 제공자(AWS vs GCP)는 아직 미확정** — PRD가 "AWS/GCP" 둘 다 허용해서 실제 지원할 채용 공고 요구사항 보고 정할 것(`docs/decisions.md` ADR-007 참고).
  - **백엔드는 상시 운영하지 않기로 함** — 이 프로젝트는 실사용자 없는 포트폴리오 데모라, 평소엔 클라우드 리소스를 내려두고 TestFlight 심사/실제 데모 시점에만 기동. 배포 파이프라인을 짤 때 "24시간 떠있다" 전제로 설계하지 말 것(ADR-007).
- [ ] **(5주차 신규)** 배포 환경 시크릿에 `LIVEKIT_HOST`/`LIVEKIT_API_KEY`/`LIVEKIT_API_SECRET`/`LIVEKIT_EGRESS_AUDIO_WS_URL`/`STT_API_KEY`/`TTS_API_KEY` 추가 — 로컬 개발은 LiveKit Cloud가 `localhost`로 못 붙어 ngrok 터널이 필요했지만, 배포 후에는 실제 공인 도메인이 생기므로 `LIVEKIT_EGRESS_AUDIO_WS_URL`을 그 도메인 기준으로 설정하면 터널 불필요
- [ ] **(5주차 신규)** `/egress/audio`(STOMP `/ws`와 별개인 raw WebSocket 경로)가 배포 환경의 리버스 프록시/로드밸런서에서 정상적으로 WSS 업그레이드되는지 확인 — `/ws` 검증됐다고 이 경로도 자동으로 되는 게 아니므로 별도 확인 필요
- [ ] **(5주차 신규, 참고)** 완전한 A안(Python/Node 사이드카)까지 확장하기로 하면 배포 파이프라인에 새 런타임/프로세스가 하나 추가됨 — 상세는 5주차 "남은 작업" 항목 참고

## 문서화 (진행 중 계속 갱신)

- [ ] API 명세 문서 (`docs/api/` — Swagger로 충분한지, 별도 정리 필요한지 판단)
- [ ] B안/C안 선택 이유 기술 블로그 초안 (PRD 7절 포트폴리오 활용 방안 참고)
