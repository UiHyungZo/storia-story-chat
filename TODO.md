# TODO

PRD v3([`PRD/Storia_PRD_v3.md`](./PRD/Storia_PRD_v3.md)) 마일스톤 기준. 완료된 항목은 커밋 로그 참고, 여기는 남은 작업만 추적.

## 지금 당장 (1주차 마무리 갭)

- [x] 클라이언트에서 실제 백엔드 REST API 호출로 전환 (`GET /api/characters`, `GET /api/conversations/{characterId}/messages`) — `dummyCharacters.ts` 제거, `src/api/` 계층 추가
- [x] 클라이언트 디바이스 ID 생성 및 영속화 (AsyncStorage) — `src/api/deviceId.ts`, `X-Device-Id` 헤더로 전송
- [x] 메시지 전송용 REST 엔드포인트 추가 (`POST /api/conversations/{characterId}/messages`) — `ConversationService#postMessage` + 컨트롤러 매핑
- [x] 백엔드 CORS 설정 — `WebConfig`(`/api/**` 전체 허용, 로컬 개발 전용)
- [ ] **아직 코드만 작성, 실기기/에뮬레이터 확인 안 됨** — 다음 세션에서 docker compose up → gradlew bootRun → expo start로 실제 동작 검증 필요 (아래 "검증 필요" 참고)

### 검증 필요 (다음 세션, 로컬 실행 후)

- [ ] Android 에뮬레이터는 `10.0.2.2`, iOS 시뮬레이터는 `localhost`로 자동 분기(`src/api/config.ts`) — 실기기 테스트 시 `EXPO_PUBLIC_API_BASE_URL` 환경변수로 개발 머신 LAN IP를 넣어야 함 (자동 감지 불가)
- [ ] 캐릭터 목록 → 채팅방 진입 → 메시지 전송 → 새로고침 후 히스토리 복원까지 실제 왕복 확인
- [ ] CORS 설정이 실제로 필요했는지(RN 네이티브는 CORS 영향 없음, Expo 웹/브라우저 디버깅 시나리오만 해당) 확인 후 불필요하면 제거 검토

## 2주차 — Gemini 연동 & WebSocket

- [x] Google Gemini API 연동 (백엔드, 텍스트 스트리밍) — `GeminiService`(WebClient + SSE), `GEMINI_API_KEY` 환경변수 필요
- [x] WebSocket(STOMP) 채널 구축 — `/app/conversation/{characterId}/send` → `/topic/conversation/{characterId}` (`WebSocketConfig`, `ConversationStompController`)
- [x] 클라이언트 WebSocket 클라이언트 연동 + 스트리밍 청크 렌더링(타이핑 효과) — `src/api/websocket.ts`, `useConversationStore`의 `streamingByCharacterId`
- [x] REST 폴백 경로 구현 (WebSocket 실패 시) — `POST /api/conversations/{characterId}/messages`도 Gemini를 동기 호출해 (논스트리밍) 응답을 반환하도록 변경, 클라이언트는 캐릭터별로 화면 진입 시 WS 연결을 1회 시도하고 실패하면 그 세션 동안 REST로 전환
- [ ] **아직 코드만 작성, 실행 검증 안 됨** — 아래 "검증 필요" 참고

### 검증 필요 (다음 세션, 로컬 실행 후 — 2주차)

- [ ] `GEMINI_API_KEY` 환경변수 발급/설정 후 `gradlew bootRun` — 키 없으면 WS는 ERROR 이벤트, REST는 고정 안내 문구로 폴백하는지 확인
- [ ] 채팅방 진입 → 메시지 전송 → 청크가 실시간으로 쌓여 타이핑 효과가 보이는지, 완료 시 최종 메시지로 치환되는지 확인
- [ ] 백엔드를 내려서 강제로 WS 연결 실패 상황을 만든 뒤 REST 폴백(전체 응답 한 번에 반환)이 정상 동작하는지 확인
- [ ] `@stomp/stompjs`가 RN(Hermes) 환경에서 별도 폴리필 없이 붙는지 확인 — 문제 있으면 `TextEncoder`/`TextDecoder` 폴리필 필요할 수 있음
- [ ] 현재는 화면 진입 시 WS 연결을 1회만 시도하고 세션 내내 그 결과(ws/rest)를 유지함 — 스트리밍 도중 연결이 끊기는 시나리오의 재시도/재연결은 3주차 범위로 남겨둠

## 3주차 — 안정성 & 동기화

- [x] 로딩/오류/재시도 UI — `CharacterListScreen`/`ChatRoomScreen`에 오류 배너 + "다시 시도" 버튼 추가(각각 `loadCharacters`/`loadMessages` 재호출). 메시지 전송 실패는 별도 배너로 표시하고 탭하면 같은 내용으로 재전송(`sendError` 상태), draft 유실 방지.
- [x] WebSocket 재연결 로직 — `src/api/websocket.ts`가 `@stomp/stompjs`의 `reconnectDelay`/`maxReconnectDelay`/`reconnectTimeMode: EXPONENTIAL`로 드롭 후 지수 백오프 재연결을 켜고, 재연결마다 구독을 다시 검. `onConnectionStateChange`로 connected/reconnecting을 store에 반영해 `ChatRoomScreen`에 "재연결 중" 배너 표시. 재연결 대기 중에는 메시지 전송이 그때그때 REST로 개별 폴백하고, 소켓이 살아나면 다음 전송부터 자동으로 다시 WS 사용(캐시된 transport 대신 매 전송마다 실제 연결 상태를 확인하도록 변경). 화면 재진입 시에도 WS를 다시 시도하도록 `disconnect` 시 `transportByCharacterId` 초기화(이전엔 앱 생애주기 동안 한 번 rest로 굳어지면 다시 시도 안 하던 버그).
  - 부수적으로 발견/수정: WS 전송이 연결 끊김으로 실패해 REST로 폴백할 때 낙관적으로 추가해둔 로컬 사용자 메시지를 지우지 않아 REST 응답의 사용자 메시지와 중복 렌더링되던 버그 수정.
- [ ] DB 히스토리 저장/복원 검증 — **여전히 미검증** (이 머신에 Docker 없음, 사용자가 이번 세션에서 실행 검증 보류 선택). 코드 레벨 점검은 함: `ConversationService`/`MessageRepository`가 `createdAt` 오름차순으로 저장/조회해 클라이언트 기대(오름차순 응답을 화면에서 역순 렌더링)와 일치. 실제 왕복은 다음 세션에서 DB 기동 후 확인 필요.
- [x] 로컬 캐시(AsyncStorage) 동기화 — `src/storage/cache.ts` 추가. `useCharacterStore`/`useConversationStore`가 로드 시작 시 캐시를 먼저 하이드레이션(즉시 렌더) 후 백그라운드로 fetch하고, 성공 시 캐시에 write-through(스트리밍 완료/REST 전송 성공 시점 포함). 네트워크 실패 시에도 캐시된 데이터는 화면에 남아 있음.

### 검증 필요 (다음 세션 — 3주차, DB 기동 가능한 환경에서)

- [ ] 이 머신엔 Docker가 없어 MariaDB를 못 띄웠음 — Homebrew로 MariaDB 설치 후 포트 3307로 별도 기동하거나 Docker Desktop 설치 후 `docker compose up`으로 DB 히스토리 저장/복원을 실제로 확인할 것
- [ ] WS 재연결: 백엔드를 잠깐 내렸다 올려서 스트리밍 도중 연결이 끊겼을 때 "재연결 중" 배너가 뜨고, 그동안 전송은 REST로 개별 폴백되며, 재연결 성공 후 다음 전송부터 다시 스트리밍되는지 확인
- [ ] 메시지 전송 실패 배너를 탭했을 때 동일 내용으로 재전송되는지, 목록/채팅방 오류 배너의 "다시 시도"가 정상 동작하는지 확인
- [ ] 오프라인 상태로 앱을 재시작해 캐릭터 목록/채팅 히스토리가 AsyncStorage 캐시로부터 즉시 보이는지 확인

## 4주차 — Native Module & 푸시

- [x] Swift Native Module (Haptic + 로컬 알림) — `apps/client/modules/storia-native/`에 로컬 Expo 모듈로 추가(클래식 `RCTBridgeModule` 패턴: `HapticNotifierModule.swift` + `HapticNotifierModule.m`의 `RCT_EXTERN_MODULE`/`RCT_EXTERN_METHOD`). `NativeModules.HapticNotifier.notify(title, body)`로 노출되고, 호출 시 `UINotificationFeedbackGenerator`로 햅틱 + `UNUserNotificationCenter`로 포그라운드에서도 보이는 로컬 알림(배너)을 띄움. `useConversationStore`가 어시스턴트 응답이 도착하는 두 지점(WS `onDone`, REST `postMessage` 성공) 모두에서 호출.
  - **이 프로젝트는 `ios/`를 커밋하지 않고 `expo prebuild`로 매번 재생성하는 구조**라서, 네이티브 코드를 `ios/` 안에 직접 넣으면 다음 prebuild 때 사라짐. 대신 Expo가 기본으로 찾는 `./modules` 경로에 로컬 모듈로 얹어서 커밋되고 prebuild에도 살아남게 함 (`expo-modules-autolinking`이 자동으로 CocoaPod으로 링크).
  - **미검증**: 이 머신엔 Xcode(Command Line Tools만 있음)와 CocoaPods가 없어 실제 빌드/실행 확인은 못 함. `npx expo prebuild --platform ios --no-install`로 `ios/` 프로젝트 골격이 생성되는 것까지만 확인(생성 후 삭제함 — 실제 pod install/빌드는 다음 세션에서 Xcode+CocoaPods 있는 환경에서 필요).
- [x] FCM 원격 푸시 (백엔드 Admin SDK) — **백엔드만** 구현, 클라이언트 SDK 연동은 보류(아래 참고).
  - `firebase-admin` 의존성 추가, `FcmProperties`/`FirebaseConfig`(`FIREBASE_CREDENTIALS_PATH` 서비스 계정 JSON 경로 미설정 시 조용히 비활성화 — `GEMINI_API_KEY` 패턴과 동일), `PushNotificationService#sendNewMessage`.
  - `User.fcmToken` 컬럼 추가, `PUT /api/devices/token`(`DeviceController`/`UserService`)으로 클라이언트가 토큰을 등록.
  - `ConversationService#postAssistantMessage`(WS `DONE`/REST 응답 두 경로가 공유하는 지점)에서 매 어시스턴트 응답마다 자동으로 푸시 발송 시도.
  - `gradlew compileJava`/`compileTestJava` 통과 확인. Firebase 프로젝트가 없어 실제 발송은 미검증.

### 남은 작업 (다음 세션 — Firebase 프로젝트 준비된 뒤)

- [ ] Firebase 프로젝트 생성 → 서비스 계정 JSON 발급(`FIREBASE_CREDENTIALS_PATH`로 백엔드에 주입) + iOS 앱 등록(`GoogleService-Info.plist`) + APNs Auth Key 업로드
- [ ] 클라이언트에 `@react-native-firebase/app`+`/messaging` 추가, 알림 권한 요청 후 발급받은 토큰을 앱 시작 시 `PUT /api/devices/token`으로 전송하는 API 래퍼 작성(`src/api/devices.ts` 등, `characters.ts`/`conversations.ts` 패턴 참고)
- [ ] 앱을 백그라운드/종료 상태로 두고 메시지를 보내 실제 FCM 푸시가 오는지 확인
- [ ] Xcode+CocoaPods 있는 환경에서 `npx expo prebuild`/`expo run:ios`로 `storia-native` 로컬 모듈이 정상 링크·컴파일되는지, 실제로 Haptic + 포그라운드 로컬 알림이 뜨는지 확인

## 5주차 — 음성 통화 B안

- [x] RN STT 연동 — **ADR-004/PRD가 명시한 `@react-native-voice/voice` 대신 `expo-speech-recognition` 사용**. npm이 `@react-native-voice/voice@3.2.4`를 "deprecated, use expo-speech-recognition instead"로 표시하고 있어(포트폴리오에서 deprecated 패키지 채택은 마이너스) 설치 직후 교체함 (`docs/decisions.md` ADR-004 갱신 참고). `app.json`에 config plugin 등록(마이크/음성 인식 권한 문구 포함).
- [x] 서버 TTS 연동 — Google Cloud TTS REST API(`texttospeech.googleapis.com/v1/text:synthesize`) 사용. `TtsProperties`/`TtsWebClientConfig`/`TtsService#synthesize`, `TTS_API_KEY` 미설정 시 `null` 반환(그래프풀 디그레이드 — `GEMINI_API_KEY`/`FIREBASE_CREDENTIALS_PATH`와 동일 패턴).
- [x] 오디오 파일 URL 응답 → 클라이언트 재생 파이프라인 — 새 엔드포인트 없이 **메시지 송수신은 기존 텍스트 채팅 파이프라인 그대로 재사용**(ADR-004 그대로): STT로 텍스트 채운 뒤 `useConversationStore#sendMessageViaRest`(REST 전용, 응답 대기 후 assistantMessage 반환 — WS 경로는 스트리밍이라 응답 완료를 기다리지 않아 음성 통화엔 부적합해서 신설)로 전송. 응답이 오면 `GET /api/messages/{messageId}/audio`(신규 `MessageController`/`MessageService`, TTS를 그때그때 합성 — 별도 오디오 저장소 없음)에서 오디오를 받아 `expo-audio`의 `createAudioPlayer`로 재생.
  - 클라이언트: `src/api/tts.ts`(오디오 URL + 가용성 HEAD 체크), `src/store/useVoiceCallStore.ts`(턴제 상태머신: idle→listening→thinking→speaking, 풀 듀플렉스 아님 — PRD 3.9 그대로), `src/components/VoiceCallOverlay.tsx`(통화 UI), `ChatRoomScreen` 헤더에 "📞 통화" 버튼.
  - **미검증**: 이 머신엔 Xcode/CocoaPods가 없어 STT/오디오 재생 네이티브 동작을 전혀 확인 못 함. TTS도 `TTS_API_KEY`가 없어 실제 합성 미검증(다음 세션에서 Google Cloud TTS 키 발급 후 확인 필요) — 키가 없어도 `/audio`가 404를 반환하고 클라이언트가 텍스트만 남기고 넘어가는 폴백 경로까지만 코드로 확인함.
  - `gradlew compileJava`/`compileTestJava`, `tsc --noEmit` 통과 확인.

## 6주차 — WebRTC 최소 데모 (C안)

- [ ] WebRTC 시그널링 서버 (Offer/Answer/ICE, WebSocket 채널로 중계)
- [ ] `react-native-webrtc` 1:1 P2P 오디오 스트리밍 최소 데모
- [ ] B안과의 통합 여부 판단 (시간 여유 시 A안 확장 검토)

## 7주차 — 모니터링 & 배포

- [ ] Sentry 연동 (클라이언트 RN SDK + 백엔드 Spring Boot SDK)
- [ ] 에러 시나리오 검증 (WebRTC 연결 실패 포함)
- [ ] 테스트 코드 (Jest+RNTL / JUnit5+Spring Boot Test) — 현재 `BackendApplicationTests`만 존재, 실질 테스트 없음
- [ ] Docker/Fastlane/GitHub Actions 배포 파이프라인
- [ ] iOS(TestFlight)/Android(Firebase App Distribution)/백엔드 배포

## 문서화 (진행 중 계속 갱신)

- [ ] API 명세 문서 (`docs/api/` — Swagger로 충분한지, 별도 정리 필요한지 판단)
- [ ] B안/C안 선택 이유 기술 블로그 초안 (PRD 7절 포트폴리오 활용 방안 참고)
