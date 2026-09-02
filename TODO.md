# TODO

PRD v3([`PRD/Storia_PRD_v3.md`](./PRD/Storia_PRD_v3.md)) 마일스톤 기준. 완료된 항목은 커밋 로그 참고, 여기는 남은 작업만 추적.

## 현재 상태 (2026-09-01 기준)

PRD v3 마일스톤 **1~6주차 완료**(6주차는 재평가로 코드 작업 없이 종료). **7주차(모니터링/배포)만 남음.**

- **1~4주차**: 텍스트 채팅(REST+WS 스트리밍), DB 히스토리, 안정성/캐시, Native Module(iOS Swift + Android Kotlin), FCM 원격 푸시 — 전부 실기기(iPhone) / 에뮬레이터(Android)까지 실행 검증 완료. Gemini(`gemini-3.6-flash`), Firebase 서비스 계정 JSON, APNs Auth Key 발급/연동 완료.
- **5주차 (음성 통화, 축소판 A안)**: LiveKit Cloud + Google STT/TTS 자격증명 발급, 서버측 파이프라인 헤드리스 검증 + **연결된 iPhone 12 Pro로 클라이언트 전체 흐름 검증 완료**(마이크 WebRTC publish → egress → STT → Gemini → TTS → 스피커 재생, 여러 턴). 이 과정에서 실제 버그 5개(서버 3 + 실기기 클라 2) 발견/수정 — 상세는 아래 5주차 섹션. **완전한 A안(python-sidecar)**도 **연결된 iPhone으로 실기기 검증 완료**(agent 자동 감지 → 마이크 → 실시간 STT → Spring 위임 → Chirp3-HD TTS → 스피커로 깨끗한 음성, 풀 듀플렉스). **(2026-09-01)** Gemini 할당량 리셋 후 재확인 완료 — 진짜 렌 페르소나 응답(폴백 아님)이 에이전트 음성으로 재생되고 유저가 그 위로 끼어들며(`interruption detected`) 연속 대화까지 확인. **5주차 완전 종료.**
- 자격증명은 전부 `~/secrets/storia/`(리포 밖, `chmod 600`) — `livekit.env`에 `LIVEKIT_*`/`STT_API_KEY`/`TTS_API_KEY`/`GEMINI_API_KEY`, 별도로 Firebase/APNs/GCP 서비스계정 JSON. 재기동 절차는 `HANDOFF.md` 참고(ngrok는 예약 도메인 `feline-request-backtrack.ngrok-free.dev`이라 재시작해도 URL 유지 — `livekit.env` 갱신 불필요).

### 다음 작업 (우선순위 순)

- [x] **(2026-08-31)** **python-sidecar 에이전트 음성 실기기 검증 완료** (커밋 `3b0e76e`) — iPhone 마이크 → 실시간 STT(한국어 여러 턴) → `StoriaLLM`이 Spring `/api/conversations/{id}/messages`에 위임(DB 저장) → 에이전트 TTS → **스피커로 깨끗한 한국어 음성**, 풀 듀플렉스. 클라에 agent 모드 배선 + `AudioSession`, `agent.py` TTS를 Chirp3-HD로 교체, `storia_client` 타임아웃 90초. 상세는 아래 5주차 "완전한 A안" 항목.
  - [x] **(2026-09-01) Gemini 할당량 리셋 후 재확인 완료 — 5주차 종료.** 로컬 스택 재기동(docker MariaDB / ngrok 예약 도메인 `feline-request-backtrack.ngrok-free.dev` 그대로 / 백엔드 음성 env / `agent.py dev` 워커 8083) 후 연결된 iPhone으로 통화 1회. 로그로 확인: agent 자동 dispatch(`AJ_JkVVFRXYaSAq`, room `call-…-2`) → 실시간 한국어 STT("지금도 계속 그러고 있나?" 등) → 턴 감지(EOT 0.977) → `StoriaLLM` → Spring 위임 → DB에 user/assistant INSERT → **진짜 렌 페르소나 응답**(*"어머, 미안해요 손님! …마치 태엽이 멈춘 기계처럼… 쨍한 주황색 표지의 작은 책 한 권을…"* — 폴백 문구 아님, Gemini 정상) → Chirp3-HD TTS room publish(`aec warmup active` = 재생 시작) → 유저가 에이전트 음성 위로 끼어들며(`interruption detected` 22:52:01) **연속 대화** → egress 완료(`Voice turn … completing: 27,626,808 bytes`, WS 정상 종료 1000) → CLIENT_INITIATED 클린 종료.
- [x] **(2026-09-02)** **릴리스 파이프라인을 EAS → Fastlane + GitHub Actions로 전환** ([[ADR-008]], `docs/deployment.md`). `apps/client/fastlane/`(`Fastfile` ios/android `beta` lane, `Appfile`), `Gemfile`(`fastlane ~> 2.223`), `plugins/withAndroidReleaseSigning.js`(prebuild가 재생성하는 `android/app/build.gradle`에 release signingConfig + `versionCode` override 주입 — 프로퍼티 없으면 debug 폴백, gradle 평가 성공 확인), `.github/workflows/release.yml`(태그 `v*` 또는 수동, iOS=macos-14 manual signing(`.p12`+profile 임시 keychain import)+`gym`+`pilot` / Android=ubuntu `bundleRelease`+`supply` internal draft, 빌드번호=`github.run_number`), `eas.json` 삭제.
  - **iOS 서명은 Aran 프로젝트(`~/Desktop/Aran/Aran/.github/workflows/cd.yml`) 방식 그대로** — `match` 폐기. Distribution 인증서(`~/Desktop/인증서/certificate.p12`)·ASC API 키는 팀 단위라 Aran repo Secrets(`BUILD_CERTIFICATE_BASE64`/`_PASSWORD`, `ASC_API_KEY_*`) 재사용. 새로 만드는 건 `com.storia.client` provisioning profile 하나.
  - **남은 것 (1회성, 사람이):** ASC 앱 레코드 생성, `com.storia.client` App ID + App Store provisioning profile 발급, iOS Secrets(Aran 값 복사 + `BUILD_PROVISION_PROFILE_BASE64` + `KEYCHAIN_PASSWORD`), Android 업로드 keystore 생성, Play Console 앱 + 서비스계정 JSON + 최초 AAB 수동 업로드, GitHub Secrets 입력(표는 `docs/deployment.md`), `app.json` `aps-environment` → `production`.
  - [ ] Secrets 입력 후 `workflow_dispatch`로 android job 리허설 → Play internal draft 도착 확인 → iOS job → TestFlight 빌드 확인 → 태그로 both.
- [x] **(2026-09-01)** 개인정보처리방침 공개 URL 퍼블리시 완료 — Notion 웹 게시: **https://atlantic-castanet-88b.notion.site/Storia-3ceed8c75c3d8058b5b7d974df7e4d73** (`docs/legal/privacy-policy.md` Import). 백업: `docs/legal/privacy-policy.html`(디자인 버전) → Claude Artifact `https://claude.ai/code/artifact/829101b2-b6fd-4c75-9d02-4882a6539b47`. 이 URL을 App Store Connect(앱 정보 → 개인정보처리방침 URL) + Play Console(앱 콘텐츠 → 개인정보처리방침)에 등록하면 됨.
- [ ] **(7주차)** Play Data Safety / Apple App Privacy + Export Compliance 설문 (수집 항목은 `privacy-policy.md` 2절 기준: 디바이스ID / 대화내용 / 음성 오디오 / FCM 토큰 / 마지막 활동시각 / Sentry 진단정보)
- [ ] **(7주차)** iOS Release Build → TestFlight 내부 테스트 / Android Release AAB → Google Play 내부 테스트 — 파이프라인(`release.yml`)은 완성됨, 위 "1회성 셋업" + Secrets 입력만 하면 실행 가능. Apple Developer / Google Play Console 유료 계정은 **준비 완료(2026-09-02)**.
- [ ] Android 실기기 USB 재시도(다른 케이블) — 소프트웨어 쪽은 2026-08-25에 다 시도, 케이블/포트 마모 추정
- [ ] 자잘: 메시지 전송/로드 실패 배너 "다시 시도" 탭 동작(탭 자동화 필요), 에뮬레이터-백엔드 동시 종료 버그 pfctl 검증

## 1주차 — 마무리 갭

- [x] 클라이언트에서 실제 백엔드 REST API 호출로 전환 (`GET /api/characters`, `GET /api/conversations/{characterId}/messages`) — `dummyCharacters.ts` 제거, `src/api/` 계층 추가
- [x] 클라이언트 디바이스 ID 생성 및 영속화 (AsyncStorage) — `src/api/deviceId.ts`, `X-Device-Id` 헤더로 전송
- [x] 메시지 전송용 REST 엔드포인트 추가 (`POST /api/conversations/{characterId}/messages`) — `ConversationService#postMessage` + 컨트롤러 매핑
- [x] 백엔드 CORS 설정 — `WebConfig`(`/api/**` 전체 허용, 로컬 개발 전용)
- [x] **(2026-08-24 실행 검증 완료)** Docker Desktop 실행 → `docker compose up` → `gradlew bootRun` → `expo run:ios`(iOS 시뮬레이터, iPhone 15 Pro)까지 이 프로젝트 최초로 실제 기동 확인. 과정에서 실제 버그 하나 발견/수정(아래 참고).

### 검증 완료 (1주차)

전부 실행 검증 완료. 요점만:
- 캐릭터 목록 → 채팅방 → 메시지 전송 → 히스토리 복원 왕복(iOS 시뮬레이터). 진입 시 거의 항상 크래시하던 실제 버그(`useConversationStore.getMessages()`의 `?? []`가 매번 새 배열 → Zustand 셀렉터 무한 루프) `EMPTY_MESSAGES` 상수로 수정(커밋 `e0ec8cb`).
- 실기기 iPhone LAN IP 경로(`EXPO_PUBLIC_API_BASE_URL=http://<Mac LAN IP>:8080`)도 왕복 확인. 이때 잡은 빌드 버그: RNFirebase SPM↔static-framework 충돌(`app.json` `ios.disableSPM: true`), `GoogleUtilities` 모듈 미정의(`withModularHeaders.js` config plugin 신설), iOS ATS 예외(`NSAllowsLocalNetworking: true`) 추가. **교훈: env var 바꿨으면 Metro도 재기동**(안 하면 기존 Metro가 `localhost`를 번들에 박음).
- CORS 전체 허용(`WebConfig`)은 로컬 개발용으로 유지, 배포 전 재검토만 남김.

## 2주차 — Gemini 연동 & WebSocket

- [x] Google Gemini API 연동 (백엔드, 텍스트 스트리밍) — `GeminiService`(WebClient + SSE), `GEMINI_API_KEY` 환경변수 필요
- [x] WebSocket(STOMP) 채널 구축 — `/app/conversation/{characterId}/send` → `/topic/conversation/{characterId}` (`WebSocketConfig`, `ConversationStompController`)
- [x] 클라이언트 WebSocket 클라이언트 연동 + 스트리밍 청크 렌더링(타이핑 효과) — `src/api/websocket.ts`, `useConversationStore`의 `streamingByCharacterId`
- [x] REST 폴백 경로 구현 (WebSocket 실패 시) — `POST /api/conversations/{characterId}/messages`도 Gemini를 동기 호출해 (논스트리밍) 응답을 반환하도록 변경, 클라이언트는 캐릭터별로 화면 진입 시 WS 연결을 1회 시도하고 실패하면 그 세션 동안 REST로 전환
- [x] **(2026-08-24 부분 검증)** `GEMINI_API_KEY` 미설정 상태로 `gradlew bootRun` 실행 확인 — REST(`postMessage`)는 고정 안내 문구로 정상 폴백하는 것을 curl과 실제 앱 둘 다에서 확인. WS `ERROR` 이벤트 자체(`ConversationStompController`가 빈 응답 시 `StreamEvent.error(...)` 발행)는 코드로만 확인, 실제 이벤트 수신은 미검증 — 아래 참고.

### 검증 완료 (2주차)

- 실제 `GEMINI_API_KEY`로 스트리밍(`CHUNK`×N → `DONE`) + REST 왕복 확인. `gemini-2.0-flash`가 Google 쪽 서비스 종료(404)돼 `gemini-3.6-flash`로 교체(커밋 `8fbd6aa`). `.onErrorReturn` 앞에 `.doOnError` 로그가 없어 원인 파악이 어려웠음 → REST 경로에도 로그 추가.
- `gemini-3.6-flash`는 추론 모델이라 첫 청크까지 19~40초+ 걸림 → `GeminiService.CHUNK_TIMEOUT` 30→60초(유효 키로도 간헐 폴백 문구 나오던 진짜 원인). `ConversationStompController`에 `.onErrorComplete()` 추가로 `onErrorDropped` 스택트레이스 정리.
- `@stomp/stompjs` RN(Hermes)에서 폴리필 없이 동작, `/ws` Upgrade `101` 확인. WS 4초 연결 타임아웃(`CONNECT_TIMEOUT_MS`)은 로컬 기준 충분해 그대로 둠(원격 배포 시 재검토 여지).

## 3주차 — 안정성 & 동기화

- [x] 로딩/오류/재시도 UI — `CharacterListScreen`/`ChatRoomScreen`에 오류 배너 + "다시 시도" 버튼 추가(각각 `loadCharacters`/`loadMessages` 재호출). 메시지 전송 실패는 별도 배너로 표시하고 탭하면 같은 내용으로 재전송(`sendError` 상태), draft 유실 방지.
- [x] WebSocket 재연결 로직 — `src/api/websocket.ts`가 `@stomp/stompjs`의 `reconnectDelay`/`maxReconnectDelay`/`reconnectTimeMode: EXPONENTIAL`로 드롭 후 지수 백오프 재연결을 켜고, 재연결마다 구독을 다시 검. `onConnectionStateChange`로 connected/reconnecting을 store에 반영해 `ChatRoomScreen`에 "재연결 중" 배너 표시. 재연결 대기 중에는 메시지 전송이 그때그때 REST로 개별 폴백하고, 소켓이 살아나면 다음 전송부터 자동으로 다시 WS 사용(캐시된 transport 대신 매 전송마다 실제 연결 상태를 확인하도록 변경). 화면 재진입 시에도 WS를 다시 시도하도록 `disconnect` 시 `transportByCharacterId` 초기화(이전엔 앱 생애주기 동안 한 번 rest로 굳어지면 다시 시도 안 하던 버그).
  - 부수적으로 발견/수정: WS 전송이 연결 끊김으로 실패해 REST로 폴백할 때 낙관적으로 추가해둔 로컬 사용자 메시지를 지우지 않아 REST 응답의 사용자 메시지와 중복 렌더링되던 버그 수정.
- [x] **(2026-08-24 실행 검증 완료)** DB 히스토리 저장/복원 — Docker Desktop 설치 후 `docker compose up`으로 MariaDB 기동, 앱에서 실제로 보낸 메시지가 `createdAt` 오름차순으로 저장/조회되는 것을 DB 직접 쿼리 + `GET /api/conversations/{id}/messages` 양쪽으로 확인. **백엔드 프로세스를 완전히 껐다 켠 뒤에도, 앱을 완전히 종료 후 재시작한 뒤에도** 히스토리가 그대로 살아있는 것까지 확인.
- [x] 로컬 캐시(AsyncStorage) 동기화 — `src/storage/cache.ts` 추가. `useCharacterStore`/`useConversationStore`가 로드 시작 시 캐시를 먼저 하이드레이션(즉시 렌더) 후 백그라운드로 fetch하고, 성공 시 캐시에 write-through(스트리밍 완료/REST 전송 성공 시점 포함). 네트워크 실패 시에도 캐시된 데이터는 화면에 남아 있음.

### 검증 완료 (3주차)

- DB 히스토리 저장/복원 — 백엔드/앱 완전 재시작 후에도 유지 확인.
- WS 재연결 메커니즘 — 헤드리스 Node 스크립트로 연결 → 백엔드 kill → 2/4/8/16s 지수 백오프 → 재연결 성공까지 확인.
- 오프라인 재시작 시 AsyncStorage 캐시로 캐릭터 목록 즉시 렌더 + 오류 배너/"다시 시도" 표시 확인. 목록 배너 "다시 시도" 탭은 Android 에뮬레이터에서 실제 탭 확인. **남은 것**: 메시지 전송 실패 배너(`sendError`) 재전송 탭, "재연결 중" 배너 렌더 — 탭 자동화 없어 코드 리뷰로만 확인(위 "다음 작업" 참고).

## 4주차 — Native Module & 푸시

- [x] Swift Native Module (Haptic + 로컬 알림) — `apps/client/modules/storia-native/`에 로컬 Expo 모듈로 추가(클래식 `RCTBridgeModule` 패턴: `HapticNotifierModule.swift` + `HapticNotifierModule.m`의 `RCT_EXTERN_MODULE`/`RCT_EXTERN_METHOD`). `NativeModules.HapticNotifier.notify(title, body)`로 노출되고, 호출 시 `UINotificationFeedbackGenerator`로 햅틱 + `UNUserNotificationCenter`로 포그라운드에서도 보이는 로컬 알림(배너)을 띄움. `useConversationStore`가 어시스턴트 응답이 도착하는 두 지점(WS `onDone`, REST `postMessage` 성공) 모두에서 호출.
  - **이 프로젝트는 `ios/`를 커밋하지 않고 `expo prebuild`로 매번 재생성하는 구조**라서, 네이티브 코드를 `ios/` 안에 직접 넣으면 다음 prebuild 때 사라짐. 대신 Expo가 기본으로 찾는 `./modules` 경로에 로컬 모듈로 얹어서 커밋되고 prebuild에도 살아남게 함 (`expo-modules-autolinking`이 자동으로 CocoaPod으로 링크).
  - **(2026-08-24 실행 검증 완료)** Xcode 26.4.0(전체)+CocoaPods가 이후 이 머신에 설치됨. `npx expo prebuild --platform ios --clean` → `expo run:ios`로 실제 pod install/컴파일/링크/시뮬레이터(iPhone 15 Pro) 실행까지 확인. 앱에서 실제로 어시스턴트 응답을 받을 때마다 `NativeModules.HapticNotifier.notify()`가 호출됐고(에러 로그 없음, JS단에서 정상 호출됨) 크래시 없이 통과 — 다만 실제 햅틱 진동/알림 배너가 화면에 뜨는 것 자체를 스크린샷으로 정확한 타이밍에 캡처하진 못해서 눈으로 직접 확인하진 않음.
- [x] **(신규, 오늘 추가) Kotlin Native Module (Android)** — PRD 9절에서 원래 범위 제외였던 항목을 뒤집고 구현(PRD 갱신 완료). `android/src/main/java/com/storianative/HapticNotifierModule.kt` + `HapticNotifierPackage.kt` — iOS와 대칭되는 클래식 `ReactContextBaseJavaModule`/`ReactPackage` 패턴, 동일한 `NativeModules.HapticNotifier.notify()` 브리지 이름을 노출해 `index.ts`가 플랫폼 분기 없이 호출. `Vibrator`/`VibratorManager`로 진동, `NotificationManagerCompat`으로 알림. `POST_NOTIFICATIONS`(API 33+) 권한은 iOS와 동일하게 첫 알림 시점에 lazy 요청. `expo-module.config.json`을 `"platforms": ["ios", "android"]`로 갱신.
  - **(2026-08-24 실행 검증 완료)** 사용자가 Android Studio 설치 → `sdkmanager`(cmdline-tools)로 platform-tools/`platforms;android-35`/`build-tools;35.0.0`/`emulator`/`system-images;android-35;google_apis;arm64-v8a` 헤드리스로 설치 → AVD(Pixel 6, API 35) 생성/부팅 → `./gradlew assembleDebug` 실제 빌드 성공(`storia-native`의 Kotlin/CMake 네이티브 빌드 포함, 4분). APK를 에뮬레이터에 설치 후 `adb shell input`(uiautomator dump로 좌표 확인) + 딥링크(`exp+storia://expo-development-client/?url=...`)로 **실제 탭 조작까지 재현**: 캐릭터 목록 로드 → 렌(Ren) 채팅방 진입(크래시 없음, `getMessages` 버그 수정이 Android에서도 유효함 확인) → 메시지 입력 → 전송 → 18초 뒤 실제 Gemini 응답이 캐릭터 페르소나 그대로 채팅 버블에 렌더링되는 것까지 확인. `adb logcat`에 FATAL/AndroidRuntime 크래시 없음, `HapticNotifier.notify()` 호출 관련 에러도 없음(다만 실제 진동/알림 배너를 눈으로 보는 것 자체는 에뮬레이터라 진동은 확인 불가, 알림 배너 노출 여부는 별도 확인 안 함).
- [x] FCM 원격 푸시 (백엔드 Admin SDK) — 처음엔 **백엔드만** 구현하고 클라이언트 SDK 연동은 보류했었으나, **(2026-08-24) 클라이언트 SDK 연동도 완료**(아래 "남은 작업" 참고, 서비스 계정 JSON만 남음).
  - `firebase-admin` 의존성 추가, `FcmProperties`/`FirebaseConfig`(`FIREBASE_CREDENTIALS_PATH` 서비스 계정 JSON 경로 미설정 시 조용히 비활성화 — `GEMINI_API_KEY` 패턴과 동일), `PushNotificationService#sendNewMessage`.
  - `User.fcmToken` 컬럼 추가, `PUT /api/devices/token`(`DeviceController`/`UserService`)으로 클라이언트가 토큰을 등록.
  - `ConversationService#postAssistantMessage`(WS `DONE`/REST 응답 두 경로가 공유하는 지점)에서 매 어시스턴트 응답마다 자동으로 푸시 발송 시도.
  - `gradlew compileJava`/`compileTestJava` 통과 확인. Firebase 프로젝트가 없어 실제 발송은 미검증.
- [x] **(신규) `lastActiveAt` 트래킹 + 일일 재참여 푸시** — `User.lastActiveAt`이 엔티티엔 있었지만 생성 후 갱신되는 곳이 없던 버그를 발견, REST/WS 채팅 경로가 공유하는 `getOrCreateConversation`에서 매 요청마다 갱신하도록 수정. `ReEngagementScheduler`가 매일 3일 이상 미접속 유저에게 FCM 재참여 푸시를 발송, `User.reengagementPushSent`로 유휴 기간당 최대 1회만 보내고 유저가 돌아오면 다시 `false`로 리셋.

### 검증 완료 (4주차 — FCM 원격 푸시 전부 종료)

- Firebase 프로젝트 생성, `GoogleService-Info.plist`/`google-services.json`(`com.storia.client`) 배치. 클라이언트 `@react-native-firebase/app`+`/messaging`(커밋 `1144f90`), `src/push/registerPushToken.ts` → `PUT /api/devices/token`. iOS는 `app.json`에 `aps-environment` + `UIBackgroundModes: ["remote-notification"]` 수동 추가 필요(플러그인이 iOS는 안 건드림).
- **서비스 계정 JSON**(`FIREBASE_CREDENTIALS_PATH`, `~/secrets/storia/`) → **Android 에뮬레이터 + 실제 iPhone 둘 다 실제 푸시 배너 도착 확인**. Android 최초엔 `POST_NOTIFICATIONS` 미승인으로 OS가 막고 있었음(정상 동작, 권한 팝업 "허용"으로 해결).
- **APNs Auth Key**(.p8, Key ID `82V493X845`, Team ID `9G5T5K3BP2`) → Firebase Cloud Messaging "개발 APNs 인증 키"로 업로드. 업로드 전 `401 THIRD_PARTY_AUTH_ERROR` 재현 → 업로드 후 정상. `eas.json`의 `appleTeamId`도 채움. **프로덕션 키는 TestFlight 전환 시 별도 업로드 필요.**
- `storia-native` 로컬 모듈(iOS Swift / Android Kotlin) 링크·컴파일·실행 확인. 진동/알림 배너를 실기기에서 눈으로 보는 것만 미확인.

**남은 것 (자잘 — 위 "다음 작업"에도 반영):**
- Android 실기기(갤럭시 S10, Android 12, 시리얼 `R3CM90JM64W`) USB 디버깅 실패 — `adb`가 처음엔 `unauthorized`, 이후 아예 사라짐. 충전은 정상이라 데이터 핀/케이블/포트 마모 추정. 소프트웨어 쪽은 2026-08-25에 다 시도. 다른 케이블/컴퓨터로 재시도.
- 에뮬레이터가 백엔드 `kill -9` 시 같이 죽는 현상(3회 재현, 원인 미확정). 가설: `lsof -ti:8080 | xargs kill -9`가 관련 없는 PID까지 죽임 → PID 파일 방식으로 이미 전환함(2026-08-30). 프로세스 안 죽이고 네트워크 실패만 재현하려면 `pfctl`로 8080 차단하는 방식 검토(`10.0.2.2` 직결 경로에 맞는지 먼저 확인).

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

### 검증 완료 (5주차 — 축소판 A안 서버+클라 전부)

- [x] **(2026-08-31)** 자격증명 발급 — LiveKit Cloud 무료 티어(`LIVEKIT_*`), ngrok 터널(`LIVEKIT_EGRESS_AUDIO_WS_URL`, 무료 URL은 세션마다 갱신), Google Cloud STT/TTS API 키(`STT_API_KEY`/`TTS_API_KEY`). curl로 TTS→STT 역인식(신뢰도 0.92) 왕복 확인. `lk room list`로 자격증명 유효 확인. `livekit-server` SDK `startTrackEgress`/`AccessToken` grant 런타임 동작 확인(`EGRESS_COMPLETE`, JWT grant가 `VoiceCallService.createToken`과 일치).
- [x] **(2026-08-31 헤드리스 검증)** 서버측 통화 왕복 전체 — `livekit-cli`로 room에 OGG publish → `POST /api/calls/{id}/token` → `POST .../turns` → Track Egress가 ngrok 통해 `/egress/audio`로 되접속 → PCM 프레임 수신 → 트랙 unpublish → `completeTurn` → STT → USER 저장 → Gemini → ASSISTANT 저장 → 턴 상태 `recording`→`processing`→`done` → `GET /api/messages/{id}/audio` MP3.
- [x] **(2026-08-31 실기기 검증 완료)** RN 앱 클라이언트 흐름 — 연결된 iPhone 12 Pro(iOS 26.6)에 `expo run:ios --device`로 빌드/설치(`EXPO_PUBLIC_API_BASE_URL`=Mac LAN IP, `SENTRY_DISABLE_AUTO_UPLOAD=true`), "📞 통화" → LiveKit Cloud 연결 → `setMicrophoneEnabled(true)` 마이크 publish(권한 팝업 "허용") → 발화 → ■ → `unpublishTrack` → egress 종료 → STT → Gemini(렌 페르소나) → TTS → `expo-audio`가 스피커로 렌 목소리 재생 → idle 복귀. **여러 턴 연속 반복 정상**(재생 후 다음 턴 마이크 재-publish도 OK). 한 턴 12~25초.
  - **실기기 전용 버그 2개 발견/수정**:
    1. **턴이 영원히 완료 안 됨**: `stopListening`이 `room.localParticipant.setMicrophoneEnabled(false)`로 트랙을 unpublish한다고 가정했는데, `livekit-client` 2.22.0에서 이 호출은 **오디오 트랙은 mute만** 함(unpublish는 screen-share 소스만). 그래서 마이크 트랙이 muted 상태로 계속 published → Track Egress가 안 끝남 → 백엔드 `completeTurn` 미실행 → 클라가 75초 폴링 후 "응답이 너무 오래 걸려요". 헤드리스 검증은 `lk` 프로세스를 죽여서(하드 disconnect) 이 버그가 안 드러났음. `stopListening`에서 `startListening`이 저장해둔 publication을 `unpublishTrack(pub.track, true)`로 명시 unpublish하도록 수정.
    2. **응답 음성이 재생 안 됨(에러도 없이)**: `isMessageAudioAvailable`의 HEAD 존재확인 타임아웃이 3초인데, `/api/messages/{id}/audio`가 요청마다 Google TTS를 새로 합성(측정 3~4초) → HEAD abort → `false` 반환 → `playAssistantAudio`가 조용히 idle 복귀. 타임아웃 15초로 상향(`AVAILABILITY_CHECK_TIMEOUT_MS`). 추가로 재생 직전 `setAudioModeAsync({ playsInSilentMode: true, allowsRecording: false })`(WebRTC 녹음세션 직후라 iOS `AVAudioSession`이 playAndRecord→이어피스/무음스위치 준수 상태) + 재생 무한 대기 방지 `PLAYBACK_MAX_MS` 90초 안전 타임아웃.
- **(2026-08-31 발견/수정한 서버측 실제 버그 3개)**:
  1. **STT가 항상 전사 실패**: LiveKit Track Egress는 트랙을 **interleaved stereo `pcm_s16le`**로 스트리밍하는데 `SttService`가 Google STT에 mono로 넘겨서(`audioChannelCount` 미지정) 인터리브된 샘플을 뭉갠 mono로 읽어 항상 결과 0개였음. `stt.audio-channel-count: 2` 프로퍼티 추가 + 요청에 `audioChannelCount` 전달로 수정. 프로퍼티 파일 밖에선 검증 불가였던 항목.
  2. **긴 응답의 `GET /api/messages/{id}/audio`가 404**: `TtsWebClientConfig`/`SttWebClientConfig`가 WebClient 기본 in-memory 버퍼(256KB)를 그대로 씀 → Google TTS가 MP3 전체를 base64로 한 JSON 바디에 담아 반환하는데, ~400자 한국어 응답이면 이미 256KB 초과 → `DataBufferLimitException` → `TtsService`가 null 반환 → 404. 두 WebClient에 `maxInMemorySize(16MB)` 설정으로 수정(실제 483KB MP3 응답으로 재확인).
  3. **음성 턴 Gemini 503을 조용히 삼킴**: `VoiceCallService`의 Gemini 호출이 `.onErrorReturn(...)`만 있고 `.doOnError` 로그가 없어(REST 경로는 2026-08-24에 이미 고친 안티패턴) Google의 간헐적 `503 Service Unavailable`이 로그 없이 고정 폴백 문구로만 나왔음. `.doOnError` 로그 추가.
- **(2026-08-31 클라이언트 변경)** `useVoiceCallStore`의 `POLL_TIMEOUT_MS`를 20초→75초로 늘림 — 헤드리스 검증에서 한 턴이 STT+Gemini(추론 모델)+TTS로 30~46초 걸리는 걸 확인, 20초면 happy path가 타임아웃남(`GeminiService.CHUNK_TIMEOUT` 30→60초 수정과 같은 뿌리). **2026-08-31 실기기 검증에서 75초로 충분함 확인**(실제 턴은 12~25초).
- [x] **(2026-08-31 실제 왕복 검증)** **완전한 양방향 실시간(원래 정의의 A안) — `apps/python-sidecar`**: LiveKit Agents SDK 워커가 room에 봇으로 들어가 유저 오디오를 실시간 STT → 응답 생성은 새로 안 짜고 기존 Spring REST(`/api/conversations/{characterId}/messages`)에 위임 → 그 텍스트를 TTS로 합성해 같은 LiveKit 세션에 오디오 트랙으로 되쏨. LiveKit Cloud + Google 서비스 계정으로 실제 검증:
  - ✅ automatic dispatch(`agent_name` 미설정 워커)가 `call-{deviceId}-{characterId}` ad-hoc room에 별도 룰 없이 자동 진입 — 2026-08-26에 "미검증"으로 남겼던 핵심 질문 해소
  - ✅ 실시간 Google STT 한국어 전사 + 턴 감지
  - ✅ `StoriaLLM`이 Spring REST 호출 → DB에 유저/어시스턴트 메시지 저장 + 캐릭터 페르소나 응답 (로직 중복 없음 설계 그대로 동작)
  - ✅ 에이전트가 TTS 합성해 room에 오디오 트랙 publish, 구독자가 오디오 프레임 수신
  - ✅ **(2026-08-31 실기기 검증, 커밋 `3b0e76e`)** 연결된 iPhone으로 실제 왕복 — agent 자동 감지 → 마이크 자동 흐름 → 실시간 STT(한국어 여러 턴) → Spring 위임(DB 저장) → **Chirp3-HD TTS → 스피커로 깨끗한 한국어 음성**(유저가 응답 내용에 반응하며 연속 대화 = 풀 듀플렉스). Gemini 키가 그날 429(할당량 소진)라 응답 내용은 폴백 문구였고 그게 또렷하게 재생됨 = 오디오 경로 검증 충분.
  - ✅ **(2026-09-01 재확인 완료 — 5주차 종료)** 할당량 리셋 후 통화 1회 재검증. 로그상 agent 자동 dispatch → 실시간 한국어 STT → 턴 감지(EOT 0.977) → `StoriaLLM` → Spring 위임 → DB user/assistant INSERT → **진짜 렌 페르소나 응답**(폴백 아님, Gemini 정상 동작) → Chirp3-HD TTS publish(`aec warmup` = 재생 시작) → 유저가 에이전트 음성 위로 끼어들며(`interruption detected`) **연속 대화** → egress 완료(27.6MB, WS 정상 종료) → 클린 종료. **음성 경로 + 실응답 내용 둘 다 검증됨.**
    - **실기기에서 잡은 것**: (a) agent 감지 시 `AudioSession.startAudioSession()`을 마이크 publish **전에** 불러야 함(동시/누락 시 iOS 캡처가 무음 → 워커가 전사 0개). (b) agent 모드에선 마이크를 통화 내내 유지 — unpublish/mute하면 워커의 스트리밍 STT가 굶어 `Audio Timeout`으로 `AgentSession` 크래시. (c) `agent.py` TTS `voice_name="ko-KR-Standard-A"`는 `livekit-plugins-google`(Gemini/Chirp 플러그인)에서 무효 → gemini-2.5-flash-tts로 라우팅(그건 "Agent Platform API" 필요, 미활성) → **지지직**. Chirp 3 HD로 교체하되 `use_streaming=False` + `audio_encoding=LINEAR16` 필수(Chirp는 스트리밍/PCM 둘 다 400). STT 역-전사 신뢰도 0.91. (d) `storia_client` 백엔드 타임아웃 30→90초.
  - **`livekit-agents 1.7.x` 대응 `agent.py` 수정**: (1) `AgentSession(llm=...)` 없으면 응답 생성 자체를 건너뜀 → Spring 위임을 `llm.LLM`/`llm.LLMStream`(`StoriaLLM`) 구현으로. (2) 워커 헬스체크 포트 8081 Metro 충돌 → 8083. (3) TTS = `google.TTS(voice_name="ko-KR-Chirp3-HD-Charon", audio_encoding=LINEAR16, sample_rate=48000, use_streaming=False)`. 상세는 `apps/python-sidecar/README.md`.
  - `requirements.txt` `livekit-agents==1.7.1`로 핀 고정. `VoiceCallService.createToken()`은 2026-08-26에 `CanSubscribe(true)`로 변경 완료(에이전트 오디오 구독용).
  - **왜 사이드카인가**: Spring(JVM)엔 "라이브 LiveKit 세션에 오디오를 다시 publish"하는 표준 경로가 없음 → LiveKit 표준인 Python Agents SDK로 별도 프로세스를 띄우고, 캐릭터 프롬프트/Gemini/영속화/FCM은 새로 안 짜고 기존 Spring REST(`/api/conversations/...`)에 위임. 트레이드오프는 리포에 런타임 하나 추가(배포 파이프라인 7주차에 영향). 상세는 `docs/decisions.md` ADR-004.

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
- [ ] 에러 시나리오 검증 (WebRTC 연결 실패 포함 — LiveKit room 연결 실패, Track Egress WebSocket 끊김, 턴 타임아웃 등 5주차에서 새로 생긴 실패 지점 포함). **(2026-08-25 부분 검증)** 자격증명 불필요한 부분만 curl+코드 리뷰로 확인: `LIVEKIT_*` 미설정 시 `POST /api/calls/{characterId}/token` → `503`(클라이언트는 `useVoiceCallStore#startCall`의 try/catch가 `phase: "error"` + `errorMessage`로 전환하는 것 코드로 확인 — `apiPost`가 non-2xx에서 `ApiError`를 throw함도 확인), `TTS_API_KEY` 미설정 시 `GET/HEAD /api/messages/{id}/audio` → `404`(클라이언트 `isMessageAudioAvailable`이 `HEAD` + `response.ok`로 확인 후 `playAssistantAudio`가 에러 없이 `phase: "idle"`로 조용히 복귀하는 것 코드로 확인). 실제 LiveKit room 연결 후 끊김/턴 타임아웃 등은 여전히 LiveKit 자격증명 필요 — 미검증.
- [x] **테스트 코드 (백엔드 JUnit5+Spring Boot Test, 클라이언트 Jest) — 외부 자원(Docker/Xcode/계정) 없이 이 머신에서 실제로 실행·통과까지 확인함.**
  - **백엔드(18개, `./gradlew test`)**: `MessageRepositoryTest`(`@DataJpaTest` + H2 인메모리 — 3주차부터 미검증으로 남아있던 "메시지가 `createdAt` 오름차순으로 저장/조회되는지"를 처음으로 실제 실행 검증함), `ConversationServiceTest`/`CharacterServiceTest`/`MessageServiceTest`(Mockito 단위 테스트), `TtsServiceTest`/`SttServiceTest`(자격증명 미설정 시 WebClient를 아예 호출하지 않고 graceful degrade하는지 검증), `CharacterControllerTest`(`@WebMvcTest` + `@MockitoBean` — Spring Boot 4에서 `@MockBean`이 제거되고 `org.springframework.test.context.bean.override.mockito.MockitoBean`으로 바뀜, `javap`로 확인 후 사용). `com.h2database:h2`를 `testRuntimeOnly`로 추가하고 `src/test/resources/application.yml`로 테스트 시 datasource를 H2로 자동 오버라이드(테스트 클래스패스가 메인보다 우선순위가 높아 별도 프로파일 지정 없이 적용됨) — 로컬에 MariaDB가 없어도 전체 테스트 스위트가 항상 돌아가게 함(7주차 CI 파이프라인에도 그대로 재사용 가능).
    - **부수적으로 발견/수정한 실제 버그**: 원래 있던 `BackendApplicationTests.contextLoads()`가 DB 문제와 별개로 `NoSuchBeanDefinitionException: ObjectMapper`로 실패하고 있었음 — Spring Boot 4의 모듈화된 스타터(`webmvc`, `websocket`만 쓰고 구버전의 통짜 `web` 스타터를 안 씀) 구성에서는 classic Jackson `ObjectMapper` 빈이 자동 생성되지 않는데, `TtsService`/`SttService`가 이걸 생성자로 직접 주입받고 있어서 **`gradlew bootRun`을 했어도 애플리케이션이 아예 기동조차 못 했을 상황**이었음. `config/JacksonConfig.java`에 명시적 `@Bean ObjectMapper`를 추가해 해결 — 이번 세션 전까지 아무도 전체 컨텍스트를 실제로 띄워본 적이 없어서 드러나지 않았던 버그.
  - **클라이언트 단위(17개, `npm test`)**: `jest-expo` preset 신규 도입(`package.json`에 `"test": "jest"` 스크립트, `"jest": {"preset": "jest-expo"}`), `tsconfig.json`에 `"types": ["jest"]` 추가. `avatarColorFor`(순수 함수), `config.ts`의 `API_BASE_URL` 플랫폼 분기(Android→`10.0.2.2`/iOS→`localhost`/env 우선), `useCharacterStore`(캐시 하이드레이션→fetch 덮어쓰기, 에러 처리), `useConversationStore`— 특히 **3주차에서 고쳤던 두 버그의 회귀 테스트**를 새로 작성: (1) WS 발행이 중간에 실패했을 때 낙관적으로 추가한 로컬 메시지가 REST 응답과 중복 렌더링되지 않는지, (2) `disconnect()` 후 `transportByCharacterId`가 초기화돼 다음 화면 진입 시 WS를 다시 시도하는지. `jest.mock(path)`(automock)이 아니라 명시적 factory로 모킹해야 함 — automock도 실제 모듈을 먼저 로드하려 시도해서, `AsyncStorage` 등 네이티브 모듈을 transitively import하는 파일(`api/conversations.ts`, `storage/cache.ts` 등)을 모킹할 때 "NativeModule: AsyncStorage is null" 에러로 테스트 자체가 안 뜨는 문제가 있었음(`jest.mock(path, () => ({...}))` 형태로 전환해 해결).
  - [x] **(2026-09-02) 클라이언트 UI 테스트 17개 추가** — `@testing-library/react-native@13`(dev), `react-test-renderer`를 `19.2.3`으로 핀(react와 정합, 안 하면 npm이 19.2.8로 올려 peer 충돌). RNTL v13은 matcher 자동 확장이라 setup 파일 불필요. 새 파일 4개: `CharacterListItem`(이름·컨셉 렌더, onPress), `MessageBubble`(내용, user=파란버블/흰글씨 vs assistant=회색버블/검정 — `testID="message-bubble-{user|assistant}"` 소스에 추가), `VoiceCallOverlay`(turn 모드 🎤→`startListening`/■→`stopListening`/thinking=스피너+disabled, agent 모드 "실시간 통화" 뱃지·마이크 숨김·"…말하는 중", 에러 메시지 우선, `endCall`), `CharacterListScreen`(마운트 시 `loadCharacters` 1회, row 렌더, 탭→`navigate("ChatRoom",{characterId})`, 에러 배너 재시도, "재시도 중…"). 스토어는 기존 방식대로 명시적 factory mock(`@livekit/react-native`·`livekit-client`·`expo-audio` 회피), 셀렉터는 `mockImplementation((sel)=>sel(fakeState))`. `npx jest --ci` **34개 전부 통과**, `tsc --noEmit` 통과. `ci.yml`이 경로 필터 없이 `jest --ci` 실행 → 자동 포함. (남음: `ChatRoomScreen` 컴포넌트 테스트, Maestro E2E 1개 — 시뮬레이터 필요해 배포 검증과 묶어서.)
- [x] **GitHub Actions CI** (`.github/workflows/ci.yml`) — push/PR마다 백엔드(`./gradlew test`, H2라 DB 불필요)와 클라이언트(`npm ci` → `tsc --noEmit` → `jest --ci`)를 각각 별도 job으로 실행. 계정/시크릿이 전혀 필요 없는 순수 CI라 지금 바로 완성 가능했음 — 워크플로 안의 실제 커맨드를 이 머신에서 그대로 실행해 통과 확인(GitHub Actions 러너에서 직접 돌려본 건 아니지만 커맨드 자체는 동일하게 검증됨).
  - **(2026-09-02)** `.github/workflows/release.yml` 추가(CD) — 태그 `v*` 푸시 또는 수동 실행 시 Fastlane으로 iOS→TestFlight / Android→Play internal. `ci.yml`과 독립. 시크릿 미입력 상태로는 실행 시 fail(설계상 정상). 상세 [[ADR-008]] / `docs/deployment.md`.
- [x] **백엔드 Dockerfile** (`apps/backend/Dockerfile`, `.dockerignore`) — multi-stage(`eclipse-temurin:17-jdk`로 `bootJar` 빌드 → `eclipse-temurin:17-jre` 런타임). **이 머신엔 Docker가 없어 `docker build` 자체는 미검증** — 파일 존재/참조 경로(`gradlew`/`settings.gradle`/`gradle/`)만 확인함.
- [ ] **Fastlane, 실제 클라우드 배포(GitHub Actions CD 포함), iOS/Android/백엔드 실배포는 보류** — Apple Developer 계정(인증서/프로비저닝 프로파일), AWS/GCP 계정, Firebase App Distribution 설정 등 실제 계정이 있어야 의미 있는 코드가 나옴(계정 없이 짜면 추측성 스켈레톤이라 검증도 안 되고 가치가 낮음). 클라우드에 실제로 배포하는 작업은 비용이 발생하고 되돌리기 어려운 변경이라 사용자 승인 없이 진행하지 않음 — 계정 준비되면 다음 세션에서 진행.
  - **클라우드 제공자(AWS vs GCP)는 아직 미확정** — PRD가 "AWS/GCP" 둘 다 허용해서 실제 지원할 채용 공고 요구사항 보고 정할 것(`docs/decisions.md` ADR-007 참고).
  - **백엔드는 상시 운영하지 않기로 함** — 이 프로젝트는 실사용자 없는 포트폴리오 데모라, 평소엔 클라우드 리소스를 내려두고 TestFlight 심사/실제 데모 시점에만 기동. 배포 파이프라인을 짤 때 "24시간 떠있다" 전제로 설계하지 말 것(ADR-007).
- [ ] **(5주차 신규)** 배포 환경 시크릿에 `LIVEKIT_HOST`/`LIVEKIT_API_KEY`/`LIVEKIT_API_SECRET`/`LIVEKIT_EGRESS_AUDIO_WS_URL`/`STT_API_KEY`/`TTS_API_KEY` 추가 — 로컬 개발은 LiveKit Cloud가 `localhost`로 못 붙어 ngrok 터널이 필요했지만, 배포 후에는 실제 공인 도메인이 생기므로 `LIVEKIT_EGRESS_AUDIO_WS_URL`을 그 도메인 기준으로 설정하면 터널 불필요
- [ ] **(5주차 신규)** `/egress/audio`(STOMP `/ws`와 별개인 raw WebSocket 경로)가 배포 환경의 리버스 프록시/로드밸런서에서 정상적으로 WSS 업그레이드되는지 확인 — `/ws` 검증됐다고 이 경로도 자동으로 되는 게 아니므로 별도 확인 필요
- [ ] **(5주차 신규, 참고)** 완전한 A안(Python/Node 사이드카)까지 확장하기로 하면 배포 파이프라인에 새 런타임/프로세스가 하나 추가됨 — 상세는 5주차 "남은 작업" 항목 참고

### 배포 목표 재정의 (2026-08-25)

이 프로젝트의 목적은 스토어 정식 출시가 아니라 **"RN으로 iOS/Android 양쪽 실제 배포 파이프라인까지 처리할 수 있음"을 증명하는 것**으로 확정. 목표선은 **TestFlight + Google Play 내부 테스트까지**(정식 스토어 출시는 이번 범위 밖 — 필요해지면 지원 시작 후 진행). 이미 Swift 개인 앱으로 App Store 정식 출시 경험이 있어 재증명 가치가 낮고, RN+Android+Backend+AI 완성도에 시간을 쓰는 게 취업 전환엔 더 효율적이라는 판단.

- [ ] Apple Developer Program 가입 ($99/년 — 승인 대기 있을 수 있어 최우선 착수 권장)
- [ ] Google Play Console 가입 ($25 1회 — 신규 계정 인증에 며칠 걸릴 수 있음)
- [ ] iOS/Android **실기기**로 실제 동작 확인 — 지금까지 전부 시뮬레이터/에뮬레이터만 검증됨. **(2026-08-25 iOS 대부분 검증)** 실제 iPhone(무료 Apple ID 개인 서명)에 Development Build 설치 → LAN IP로 캐릭터 목록 로드 + 채팅방 진입 + 메시지 전송 + 실제 Gemini 응답 수신까지 확인. 시뮬레이터/코드 리뷰로는 못 잡았던 **실기기 전용 UX 버그 2개 발견/수정**: (1) `keyboardVerticalOffset={80}`이 시뮬레이터 기준 매직 넘버라 실기기에서 키보드가 입력창을 가림 — `useHeaderHeight()`로 동적 계산하도록 수정. (2) 전송 버튼의 `disabled={isSending}`이 스타일 변화 없이 그냥 안 눌리기만 해서, Gemini 응답 대기(20~40초) 중 사용자가 "안 눌리는" 버튼을 보고 실패한 줄 알고 재입력을 시도하는 혼란스러운 UX였음 — 버튼에 스피너/dim 스타일 추가 + "답변을 생각하는 중이에요…" 배너 신규 추가로 해결. Android 실기기는 USB 디버깅 트러블슈팅 실패(위 4주차 항목 참고)로 여전히 미검증. 음성 통화/설정 등 나머지 iOS 화면도 미검증.
- [x] **(2026-08-26)** 개인정보처리방침 초안 작성 완료 — [`docs/legal/privacy-policy.md`](./docs/legal/privacy-policy.md). 수집 항목(디바이스ID/대화 내용/음성통화 오디오/FCM 토큰/Sentry 진단정보), 제3자 위탁(Gemini/Google Cloud STT·TTS/LiveKit/FCM/Sentry), 보관·삭제 절차, 아동 정책, 문의처 이메일(`lukaend@naver.com`)까지 포함.
- [x] **(2026-09-01)** 개인정보처리방침 공개 URL 퍼블리시 완료 — **Notion 웹 게시: https://atlantic-castanet-88b.notion.site/Storia-3ceed8c75c3d8058b5b7d974df7e4d73** (md Import). 디자인 버전 `docs/legal/privacy-policy.html` = Claude Artifact `https://claude.ai/code/artifact/829101b2-b6fd-4c75-9d02-4882a6539b47`(백업).
- [ ] 스토어 필수 관문 설문: Google Play Data Safety, Apple App Privacy, Export Compliance (위 개인정보처리방침 2절 수집 항목 기준으로 응답)
- [x] **(2026-08-26 완료)** `apps/client/assets/icon.png`가 Expo 기본 템플릿 아이콘이던 것을 Storia 브랜딩 아이콘으로 교체 — 인디고/퍼플 그라디언트 배경 + 흰색 말풍선(채팅) + "S" 레터마크. PIL(Pillow)로 직접 생성(`icon.png` 1024x1024, `android-icon-foreground/background/monochrome.png`, `favicon.png` 전부 갱신, 세이프존/모노크롬 컷아웃까지 합성 검증 완료). `splash-icon.png`는 `app.json`에 참조가 없어(splash 미설정) 그대로 둠.
- [x] ~~**(2026-08-26)** Release 빌드 파이프라인 — EAS Build 채택, `apps/client/eas.json` 작성~~ → **(2026-09-02) EAS 폐기, Fastlane + GitHub Actions로 전환** ([[ADR-008]]). `eas.json` 삭제. 상세는 위 "다음 작업" 2026-09-02 항목 + `docs/deployment.md`.
- [ ] iOS Release Build → TestFlight 내부 테스트 배포
- [ ] Android Release AAB → Google Play 내부 테스트 배포
- [x] **(2026-08-25)** README에 아키텍처/배포 방식/구현 범위 정리 (아래 "문서화" 섹션의 API 명세/블로그와는 별개 항목) — 루트 `README.md`에 "구현 범위"/"배포 현황" 섹션 신규 추가, Android 실행법/FCM·Sentry 환경변수/테스트·CI/Docker 빌드 명령어 보강, `docs/architecture/README.md`의 stale한 "Week 5" 헤딩도 정정.
- [ ] DeepLink는 이번 목표에서 필수 아님 — "Push/DeepLink/Streaming 중 핵심 기능" 요건은 Push(FCM)+Streaming(WS)으로 이미 충족됨, 시간 남으면 보너스로만 고려

**(2026-08-25 결정) 백엔드는 localhost/LAN 유지해도 됨 — 클라우드 배포는 이번 목표에 필수 아님**: TestFlight 내부 테스터(App Store Connect Users, 최대 100명)는 Apple Beta App Review 자체가 없고, Google Play 내부 테스트 트랙도 가벼운 정책 체크만 거쳐 정식 리뷰가 없음 — 즉 리뷰어가 백엔드 기능을 실제로 테스트하지 않음. 본인 폰으로 직접 확인할 때만 집 Wi-Fi(LAN IP)에 있으면 됨(단 iOS ATS/Android cleartext traffic 차단 때문에 평문 HTTP 예외 설정은 필요 — `Info.plist NSAllowsArbitraryLoads` 또는 도메인 예외, Android `usesCleartextTraffic`/network security config). 면접 등에서 집 밖에서 직접 시연하려면 그 시점에 ngrok 터널을 예비책으로 켜둘 것 — 지금 단계에서 클라우드 배포를 서두를 필요는 없음.

## 문서화 (진행 중 계속 갱신)

- [x] **(2026-08-25 확인)** API 명세 문서 — `docs/api/`는 빈 디렉토리(Swagger UI로 충분하다고 이미 판단된 흔적)이고, 실제 정리는 이미 [`docs/api.md`](./docs/api.md)에 REST/WS/WebRTC 엔드포인트별로 상세히 되어있었음(이전 세션에서 작성됐으나 이 체크박스에 반영이 안 돼있던 것으로 보임). 오늘 세션에서 stale해진 부분(3주차 per-device WS 토픽 스코프 변경 미반영)만 발견해 수정.
- [x] **(2026-08-25)** B안/C안 선택 이유 기술 블로그 초안 (PRD 7절 포트폴리오 활용 방안 참고) — [`docs/blog-webrtc-tradeoffs.md`](./docs/blog-webrtc-tradeoffs.md)에 초안 작성 완료. `docs/decisions.md` ADR-004(원본 결정 + 갱신 1~3)를 근거 자료로 사용. 실제 블로그 플랫폼에 발행하기 전 다듬을 여지 있음 — 초안 자체가 완료 기준.
