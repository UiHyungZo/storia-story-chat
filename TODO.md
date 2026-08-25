# TODO

PRD v3([`PRD/Storia_PRD_v3.md`](./PRD/Storia_PRD_v3.md)) 마일스톤 기준. 완료된 항목은 커밋 로그 참고, 여기는 남은 작업만 추적.

**(2026-08-24 세션 종료)** 다음 세션 시작 전: Docker(`docker compose up`)/백엔드(`export GEMINI_API_KEY=...` 후 `gradlew bootRun`)/Android 에뮬레이터 전부 재기동 필요(로컬 프로세스라 컴퓨터/세션 종료 시 꺼짐). 최우선 남은 작업은 4주차의 **FCM 서비스 계정 JSON** — 받으면 바로 실 발송 검증 가능. 상세는 [`HANDOFF.md`](./HANDOFF.md) 참고.

**(2026-08-25 세션)** 로컬 스택 재기동 후 실행 검증 중 실제 버그 하나 발견/수정: `GeminiService`의 `CHUNK_TIMEOUT`이 30초였는데, `gemini-3.6-flash`가 추론(thinking) 모델이라 실제 캐릭터 시스템 프롬프트급 요청에서 첫 응답까지 19~29초, 프롬프트에 따라 40초 넘게 걸리는 경우가 확인됨 — 30초 마진이 너무 빡빡해서 유효한 키로도 간헐적으로 고정 폴백 문구가 나왔음. 60초로 늘려서 REST/WS 스트리밍 둘 다 재검증 완료(아래 2주차 항목 참고). 서비스 계정 JSON은 이번 세션엔 아직 안 받음 — 여전히 최우선 남은 작업. 상세는 `HANDOFF.md` 참고.

## 지금 당장 (1주차 마무리 갭)

- [x] 클라이언트에서 실제 백엔드 REST API 호출로 전환 (`GET /api/characters`, `GET /api/conversations/{characterId}/messages`) — `dummyCharacters.ts` 제거, `src/api/` 계층 추가
- [x] 클라이언트 디바이스 ID 생성 및 영속화 (AsyncStorage) — `src/api/deviceId.ts`, `X-Device-Id` 헤더로 전송
- [x] 메시지 전송용 REST 엔드포인트 추가 (`POST /api/conversations/{characterId}/messages`) — `ConversationService#postMessage` + 컨트롤러 매핑
- [x] 백엔드 CORS 설정 — `WebConfig`(`/api/**` 전체 허용, 로컬 개발 전용)
- [x] **(2026-08-24 실행 검증 완료)** Docker Desktop 실행 → `docker compose up` → `gradlew bootRun` → `expo run:ios`(iOS 시뮬레이터, iPhone 15 Pro)까지 이 프로젝트 최초로 실제 기동 확인. 과정에서 실제 버그 하나 발견/수정(아래 참고).

### 검증 필요 (다음 세션, 로컬 실행 후)

- [x] **(2026-08-25 실행 검증 완료)** Android 에뮬레이터는 `10.0.2.2`, iOS 시뮬레이터는 `localhost`로 자동 분기(`src/api/config.ts`) — 실기기 테스트 시 `EXPO_PUBLIC_API_BASE_URL` 환경변수로 개발 머신 LAN IP를 넣어야 함 (자동 감지 불가). iOS 시뮬레이터 기준(`localhost`)은 검증됨. **LAN IP 경로도 실제 iPhone(Xcode에 페어링된 실기기, 무료 Apple ID 개인 서명)으로 왕복 확인 완료**: `EXPO_PUBLIC_API_BASE_URL=http://<Mac LAN IP>:8080`로 Metro/네이티브 빌드 → WiFi로 캐릭터 목록 정상 로드까지 확인.
  - **과정에서 발견/수정한 실제 빌드 버그 2개**: (1) `@react-native-firebase/app`가 기본으로 SPM(Swift Package Manager)을 쓰는데, `@config-plugins/react-native-webrtc`가 요구하는 static 프레임워크 링크와 충돌(`pod install` 실패) — RNFirebase 플러그인이 제공하는 `ios.disableSPM: true` 옵션을 `app.json`에 추가해 해결. (2) 그 다음 `GoogleUtilities`가 모듈을 정의하지 않아 static 프레임워크에서 `pod install`이 또 실패 — CocoaPods 공식 해법인 `use_modular_headers!`를 Podfile에 주입하는 로컬 config plugin(`apps/client/plugins/withModularHeaders.js`, `withPodfile`+`mergeContents` 사용, RNFirebase 자체 플러그인 구현 패턴을 그대로 따름)을 새로 만들어 해결.
  - **iOS ATS 예외도 이번에 처음 추가**: `app.json`의 `ios.infoPlist.NSAppTransportSecurity.NSAllowsLocalNetworking: true` — 이게 없으면 실기기에서 평문 HTTP(사설 IP 대역)가 ATS에 막혀 아예 연결 시도조차 안 됨(배포 목표 재정의 섹션에서 이미 "필요할 것"으로 예견했던 항목, 이번에 실제로 필요함을 확인하고 추가).
  - **처음엔 "Could not connect to the server"로 실패했는데 원인은 코드가 아니라 운영 실수였음**: 이미 몇 시간 전에 Android 테스트용으로 띄워둔 Metro가 `EXPO_PUBLIC_API_BASE_URL` 없이 떠있던 상태라, iOS 실기기 빌드가 그 기존 Metro에 붙으면서 (iOS 기본값인) `localhost`가 그대로 번들에 박힘 — 실기기에서 `localhost`는 자기 자신이라 당연히 연결 실패. Metro를 올바른 env var로 재기동하니 해결. **교훈: `expo run:ios/--device`를 새 env var로 다시 실행해도, 이미 떠있는 Metro가 있으면 그걸 그대로 재사용해서 새 env var가 반영 안 될 수 있음 — env var를 바꿨으면 Metro도 같이 재기동할 것.**
- [x] **캐릭터 목록 → 채팅방 진입 → 메시지 전송 → 히스토리 복원까지 실제 왕복 확인함** (iOS 시뮬레이터). 이 과정에서 `useConversationStore.getMessages()`가 메시지 없을 때 `?? []`로 매번 새 배열을 반환해 Zustand 셀렉터 참조가 불안정해지고, 채팅방 진입 시 "Maximum update depth exceeded"로 **거의 항상 크래시하는 실제 버그**를 발견 — `EMPTY_MESSAGES` 상수로 고침(커밋 `e0ec8cb`). 앱 완전 종료 후 재시작 + 백엔드 완전 종료 후 재시작 두 경우 모두 DB에서 메시지가 정상 복원되는 것도 확인.
- [x] **(2026-08-24 결정)** CORS 설정 유지하기로 결정 — `WebConfig`의 기존 주석이 이미 정확한 근거를 담고 있음(RN 네이티브 fetch는 CORS 영향 없지만 Swagger UI 등 브라우저 기반 API 확인 시나리오엔 필요). 로컬 개발 전용 전체 허용 설정이라 제거할 이유 없음, 배포 전 재검토(코드 주석에 이미 명시)만 남기고 종료.

## 2주차 — Gemini 연동 & WebSocket

- [x] Google Gemini API 연동 (백엔드, 텍스트 스트리밍) — `GeminiService`(WebClient + SSE), `GEMINI_API_KEY` 환경변수 필요
- [x] WebSocket(STOMP) 채널 구축 — `/app/conversation/{characterId}/send` → `/topic/conversation/{characterId}` (`WebSocketConfig`, `ConversationStompController`)
- [x] 클라이언트 WebSocket 클라이언트 연동 + 스트리밍 청크 렌더링(타이핑 효과) — `src/api/websocket.ts`, `useConversationStore`의 `streamingByCharacterId`
- [x] REST 폴백 경로 구현 (WebSocket 실패 시) — `POST /api/conversations/{characterId}/messages`도 Gemini를 동기 호출해 (논스트리밍) 응답을 반환하도록 변경, 클라이언트는 캐릭터별로 화면 진입 시 WS 연결을 1회 시도하고 실패하면 그 세션 동안 REST로 전환
- [x] **(2026-08-24 부분 검증)** `GEMINI_API_KEY` 미설정 상태로 `gradlew bootRun` 실행 확인 — REST(`postMessage`)는 고정 안내 문구로 정상 폴백하는 것을 curl과 실제 앱 둘 다에서 확인. WS `ERROR` 이벤트 자체(`ConversationStompController`가 빈 응답 시 `StreamEvent.error(...)` 발행)는 코드로만 확인, 실제 이벤트 수신은 미검증 — 아래 참고.

### 검증 필요 (다음 세션, 로컬 실행 후 — 2주차)

- [x] **(2026-08-24 실행 검증 완료)** 사용자가 실제 `GEMINI_API_KEY` 제공 → 실 스트리밍/REST 왕복 전부 확인. 처음엔 계속 고정 폴백 문구만 왔는데, 원인은 키가 아니라 **`gemini-2.0-flash` 모델이 Google 쪽에서 서비스 종료**(`404 This model ... is no longer available`)된 것이었음 — `gemini-3.6-flash`로 교체(`application.yml`, 커밋 `8fbd6aa`)해서 해결. 헤드리스 STOMP 스크립트로 실제 WS 스트리밍도 확인: `CHUNK` 이벤트 여러 개 → `DONE`, 캐릭터 페르소나에 맞는 진짜 창의적인 응답(예: 렌이 손님에게 책을 파는 짧은 이야기를 즉석에서 지어냄) 도착. REST 경로도 curl/실제 앱 양쪽에서 진짜 AI 응답 수신 확인.
  - **부수 발견/수정**: `ConversationController.postMessage`의 `.onErrorReturn(...)`이 에러를 로그 한 줄 없이 완전히 삼키고 있어서, 모델 404 에러가 전혀 안 보이고 그냥 폴백 문구만 계속 나와 원인 파악이 어려웠음 — `.doOnError(error -> log.warn(...))` 추가(STOMP 컨트롤러는 이미 하고 있던 패턴, REST도 맞춤). 커밋 `8fbd6aa`.
  - **(2026-08-25 추가 발견/수정)** 모델 교체 후에도 여전히 간헐적으로 고정 폴백 문구가 나오는 걸 재확인 — 로그에 `TimeoutException: Did not observe any item or terminal signal within 30000ms`. curl로 직접 Gemini `streamGenerateContent`를 찔러보니 `gemini-3.6-flash`가 추론(thinking) 모델이라 첫 청크까지 프롬프트 복잡도에 따라 11~29초, 길면 40초 넘게 걸리는 것을 확인(빈 프롬프트: 11초, 실제 캐릭터 시스템 프롬프트급: 19~29초). `GeminiService.CHUNK_TIMEOUT`을 30초→60초로 늘려 해결 — REST/WS 양쪽 헤드리스로 재검증 완료(REST 29초 소요, WS 헤드리스 STOMP 스크립트로 `CHUNK`×N → `DONE` 확인). `./gradlew test`로 회귀 없음 확인.
  - **(2026-08-25 부수 수정)** `ConversationStompController`의 `doOnError`가 STOMP `ERROR` 이벤트는 정상 발행하지만 그 뒤로 예외가 `subscribe()`의 미구현 에러 핸들러까지 전파돼 `Operators : Operator called default onErrorDropped`라는 불필요한 스택트레이스가 로그에 매번 남고 있었음 — `.onErrorComplete()`를 `doOnError` 뒤에 추가해 정리.
- [x] **(2026-08-24 실행 검증 완료)** 백엔드를 내려서 WS 연결 실패 상황을 만든 뒤 재연결이 되는지는 3주차 헤드리스 테스트로 확인(아래 3주차 참고). REST 폴백 자체는 이미 위에서 실제 응답까지 확인됨.
- [x] `@stomp/stompjs`가 RN(Hermes) 환경에서 별도 폴리필 없이 붙음 — 실제 앱에서 폴리필 관련 크래시 없이 STOMP 핸드셰이크 자체는 정상 동작(curl로 `/ws`에 직접 Upgrade 요청 시 `101` 응답도 별도 확인).
- [x] **(2026-08-24 신규 발견, 2026-08-25 원인 확정)** 현재는 화면 진입 시 WS 연결을 1회만 시도하고 세션 내내 그 결과(ws/rest)를 유지하는데, 이 연결 시도에 4초 타임아웃(`CONNECT_TIMEOUT_MS`, `src/api/websocket.ts`)이 걸려있음 — 당시엔 이게 원인으로 "추정"됐었으나, **2026-08-25에 진짜 원인이 확인됨**: WS 연결 자체는 정상이었고, `GeminiService`의 `CHUNK_TIMEOUT`(당시 30초)이 `gemini-3.6-flash`의 실제 응답 시간(19~29초, 프롬프트에 따라 40초+)에 비해 마진이 너무 빡빡해서 REST/WS 양쪽 다 간헐적으로 타임아웃 → 고정 폴백 문구가 나왔던 것. 60초로 늘려 해결(아래 2주차 항목 참고) — WS 4초 연결 타임아웃 자체는 별도 손 안 댐(로컬 백엔드 기준 4초면 충분해 보임, 실기기/원격 배포 시엔 재검토 여지 있음).

## 3주차 — 안정성 & 동기화

- [x] 로딩/오류/재시도 UI — `CharacterListScreen`/`ChatRoomScreen`에 오류 배너 + "다시 시도" 버튼 추가(각각 `loadCharacters`/`loadMessages` 재호출). 메시지 전송 실패는 별도 배너로 표시하고 탭하면 같은 내용으로 재전송(`sendError` 상태), draft 유실 방지.
- [x] WebSocket 재연결 로직 — `src/api/websocket.ts`가 `@stomp/stompjs`의 `reconnectDelay`/`maxReconnectDelay`/`reconnectTimeMode: EXPONENTIAL`로 드롭 후 지수 백오프 재연결을 켜고, 재연결마다 구독을 다시 검. `onConnectionStateChange`로 connected/reconnecting을 store에 반영해 `ChatRoomScreen`에 "재연결 중" 배너 표시. 재연결 대기 중에는 메시지 전송이 그때그때 REST로 개별 폴백하고, 소켓이 살아나면 다음 전송부터 자동으로 다시 WS 사용(캐시된 transport 대신 매 전송마다 실제 연결 상태를 확인하도록 변경). 화면 재진입 시에도 WS를 다시 시도하도록 `disconnect` 시 `transportByCharacterId` 초기화(이전엔 앱 생애주기 동안 한 번 rest로 굳어지면 다시 시도 안 하던 버그).
  - 부수적으로 발견/수정: WS 전송이 연결 끊김으로 실패해 REST로 폴백할 때 낙관적으로 추가해둔 로컬 사용자 메시지를 지우지 않아 REST 응답의 사용자 메시지와 중복 렌더링되던 버그 수정.
- [x] **(2026-08-24 실행 검증 완료)** DB 히스토리 저장/복원 — Docker Desktop 설치 후 `docker compose up`으로 MariaDB 기동, 앱에서 실제로 보낸 메시지가 `createdAt` 오름차순으로 저장/조회되는 것을 DB 직접 쿼리 + `GET /api/conversations/{id}/messages` 양쪽으로 확인. **백엔드 프로세스를 완전히 껐다 켠 뒤에도, 앱을 완전히 종료 후 재시작한 뒤에도** 히스토리가 그대로 살아있는 것까지 확인.
- [x] 로컬 캐시(AsyncStorage) 동기화 — `src/storage/cache.ts` 추가. `useCharacterStore`/`useConversationStore`가 로드 시작 시 캐시를 먼저 하이드레이션(즉시 렌더) 후 백그라운드로 fetch하고, 성공 시 캐시에 write-through(스트리밍 완료/REST 전송 성공 시점 포함). 네트워크 실패 시에도 캐시된 데이터는 화면에 남아 있음.

### 검증 필요 (다음 세션 — 3주차, DB 기동 가능한 환경에서)

- [x] Docker Desktop 설치 후 `docker compose up`으로 DB 히스토리 저장/복원 실제 확인 완료(위 참고).
- [x] **(2026-08-24 실행 검증)** WS 재연결 메커니즘 자체 — 앱 UI로는 테스트 세션이 REST로 떨어져서(위 2주차 신규 발견 참고) 배너를 못 띄워봤지만, `websocket.ts`와 동일한 `@stomp/stompjs` 설정(`reconnectDelay`/`maxReconnectDelay`/`EXPONENTIAL`)으로 헤드리스 Node 스크립트를 만들어 실제 백엔드에 직접 붙여 검증: 연결 → 백엔드 kill → `WS CLOSED`/`WS ERROR` 감지 → 2s/4s/8s/16s 지수 백오프로 재시도 → 백엔드 재기동 후 자동 재연결 성공(`CONNECTED count=2`)까지 실제로 확인함. 즉 재연결의 핵심 메커니즘은 정상 동작 — **남은 건 `ChatRoomScreen`이 이 상태를 배너로 정확히 렌더링하는지를 실제 화면에서 탭으로 확인하는 것뿐**(탭 자동화 없어 미검증).
- [x] **(2026-08-25 부분 검증)** 목록 화면 오류 배너의 "다시 시도" 탭 동작 — Android 에뮬레이터에서 실제 탭으로 확인: 백엔드를 내린 채 앱 재시작 → 캐시된 캐릭터 3종 + 오류 배너("fetch failed: ... Failed to connect to /10.0.2.2:8080") + "다시 시도" 표시 → 백엔드를 다시 올리고 "다시 시도" 실제 탭 → 오류 배너가 사라지고 정상 로드됨까지 확인. 메시지 전송 실패 배너(`sendError`) 탭 시 재전송은 코드 레벨로만 확인(`ChatRoomScreen`의 `onPress={() => handleSend(sendError.content)}` — 목록 재시도와 동일 패턴, 동일 content로 재호출) — 실제 탭 재현은 아래 "신규 발견" 항목 때문에 이번 세션엔 실패, 다음 세션 과제로 남김.
- [x] **(2026-08-24 실행 검증 완료)** 오프라인 상태로 앱을 재시작해 캐릭터 목록이 AsyncStorage 캐시로부터 즉시 보이는지 확인 — 백엔드를 완전히 끈 상태에서 앱을 완전히 재시작해도 캐릭터 3종이 캐시에서 즉시 렌더링됐고, 동시에 "fetch failed: ... Could not connect to the server" 오류 배너 + "다시 시도" 버튼도 정상적으로 함께 표시됨. ("다시 시도" 탭 자체의 동작은 탭 자동화가 없어 미검증.)

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

### 남은 작업

- [x] **(2026-08-24 진행)** Firebase 프로젝트 생성 → iOS/Android 앱 등록해서 `GoogleService-Info.plist`/`google-services.json` 발급받음(`com.storia.client`로 확인) → `apps/client/`에 배치, `.gitignore` 처리, `app.json`의 `googleServicesFile`로 연결. **서비스 계정 JSON(`FIREBASE_CREDENTIALS_PATH`용, 백엔드가 실제 발송하는 데 필요)과 APNs Auth Key는 아직 미발급** — 남은 항목으로 아래 유지.
- [x] **(2026-08-24 실행 검증 완료)** 클라이언트에 `@react-native-firebase/app`+`/messaging` 추가(커밋 `1144f90`). `src/push/registerPushToken.ts`가 권한 요청 → 토큰 발급 → `PUT /api/devices/token`(`src/api/devices.ts`, `client.ts`에 `apiPut` 신규) 전송, 앱 시작 시 1회 호출(`App.tsx`). iOS는 `app.json`에 `aps-environment` entitlement + `UIBackgroundModes: ["remote-notification"]` 수동 추가 필요했음(`@react-native-firebase/messaging` 플러그인은 Android 알림 아이콘 설정만 하고 iOS는 안 건드림). **Android 에뮬레이터에서 실제 토큰 발급 → DB `app_user.fcm_token`에 진짜 FCM 토큰(`fDDC1I...`) 저장되는 것까지 확인** — 에뮬레이터 Play Services가 오래돼서 `SERVICE_VERSION_UPDATE_REQUIRED` 경고가 logcat에 떴지만 토큰 발급 자체는 성공.
- [ ] **서비스 계정 JSON 발급** → `FIREBASE_CREDENTIALS_PATH`로 백엔드에 주입 → 실제 발송(`PushNotificationService`) 확인
- [ ] iOS에서도 위와 동일하게 토큰 발급/등록 확인 — iOS 시뮬레이터는 실제 APNs 토큰을 못 받는 경우가 많아 실기기가 필요할 수 있음
- [ ] APNs Auth Key(.p8, Apple Developer 계정) 발급 → Firebase 콘솔 Cloud Messaging 탭에 업로드 (iOS 실제 원격 푸시 수신에 필요)
- [ ] 앱을 백그라운드/종료 상태로 두고 메시지를 보내 실제 FCM 푸시가 오는지 확인 (서비스 계정 JSON 필요)
- [x] Xcode+CocoaPods 있는 환경에서 `npx expo prebuild`/`expo run:ios`로 `storia-native` 로컬 모듈 링크·컴파일 확인 완료(위 참고). 실제 햅틱 진동/알림 배너가 화면에 뜨는지 눈으로 직접 보는 것만 남음.
- [x] **(2026-08-24 실행 검증 완료)** Android SDK/에뮬레이터 설치 후 `storia-native`의 Kotlin 모듈 링크·컴파일·실행 확인 완료(위 참고). 남은 건 진동/알림 배너를 실제 기기(에뮬레이터는 진동 체감 불가)에서 눈으로 확인하는 것과 API 33+ 권한 프롬프트 확인뿐.
- [ ] **(2026-08-25 신규 발견, 트러블슈팅 실패)** 이 머신에 실제 Android 기기(갤럭시 S10, Android 12, 시리얼 `R3CM90JM64W`)가 USB로 연결돼 있어 실기기 테스트를 시도했으나 실패. 처음엔 `adb devices`에 `unauthorized`로 잡혔으나(USB 디버깅 인증 팝업 문제로 추정), 권한 취소 후 재연결/adb 서버 재시작/USB 모드를 "파일 전송"으로 확인/포트 교체/macOS 쪽 "기기 신뢰" 팝업 수락까지 다 시도한 뒤로는 `adb devices`에서 아예 사라짐 — Android Studio Device Manager에도 Physical 기기가 전혀 안 뜸(가상 기기 `storia_test`만 보임). 폰은 **충전은 정상**되는 것으로 봐서 전원 핀은 살아있고 데이터 핀 쪽 핸드셰이크만 실패하는 것으로 추정(케이블/포트 마모 가능성). Android 12라 OS 버전 문제는 아님. 다음 세션에서 다른 케이블/다른 컴퓨터로 재시도해볼 것 — 소프트웨어 쪽으로 시도할 수 있는 건 이번 세션에서 다 시도함.
- [ ] **(2026-08-25 신규 발견, 도구 관련)** Android 에뮬레이터(`storia_test`)가 백엔드 JVM 프로세스를 `kill -9`로 죽일 때 같이 죽는 현상을 이번 세션에서 3회 재현함(원인 미확정 — 리소스 경합으로 추정, 메모리 여유가 있을 때도 발생해 확정은 아님). `adb reverse tcp:8080` 제거로 우회 시도했으나 Android는 `10.0.2.2`(에뮬레이터 내장 게이트웨이 별칭)로 백엔드에 직접 붙기 때문에 `adb reverse` 설정과 무관하게 계속 연결됨 — 이 방법으로는 백엔드 다운 상황을 재현할 수 없음. 다음에 백엔드 다운/네트워크 실패 시나리오를 에뮬레이터에서 테스트하려면 프로세스를 죽이지 말고 다른 방법(예: 에뮬레이터 게스트 OS 방화벽 규칙, `adb shell` 네트워크 제어)을 찾을 것.

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
- [ ] 에러 시나리오 검증 (WebRTC 연결 실패 포함 — LiveKit room 연결 실패, Track Egress WebSocket 끊김, 턴 타임아웃 등 5주차에서 새로 생긴 실패 지점 포함). **(2026-08-25 부분 검증)** 자격증명 불필요한 부분만 curl+코드 리뷰로 확인: `LIVEKIT_*` 미설정 시 `POST /api/calls/{characterId}/token` → `503`(클라이언트는 `useVoiceCallStore#startCall`의 try/catch가 `phase: "error"` + `errorMessage`로 전환하는 것 코드로 확인 — `apiPost`가 non-2xx에서 `ApiError`를 throw함도 확인), `TTS_API_KEY` 미설정 시 `GET/HEAD /api/messages/{id}/audio` → `404`(클라이언트 `isMessageAudioAvailable`이 `HEAD` + `response.ok`로 확인 후 `playAssistantAudio`가 에러 없이 `phase: "idle"`로 조용히 복귀하는 것 코드로 확인). 실제 LiveKit room 연결 후 끊김/턴 타임아웃 등은 여전히 LiveKit 자격증명 필요 — 미검증.
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

### 배포 목표 재정의 (2026-08-25)

이 프로젝트의 목적은 스토어 정식 출시가 아니라 **"RN으로 iOS/Android 양쪽 실제 배포 파이프라인까지 처리할 수 있음"을 증명하는 것**으로 확정. 목표선은 **TestFlight + Google Play 내부 테스트까지**(정식 스토어 출시는 이번 범위 밖 — 필요해지면 지원 시작 후 진행). 이미 Swift 개인 앱으로 App Store 정식 출시 경험이 있어 재증명 가치가 낮고, RN+Android+Backend+AI 완성도에 시간을 쓰는 게 취업 전환엔 더 효율적이라는 판단.

- [ ] Apple Developer Program 가입 ($99/년 — 승인 대기 있을 수 있어 최우선 착수 권장)
- [ ] Google Play Console 가입 ($25 1회 — 신규 계정 인증에 며칠 걸릴 수 있음)
- [ ] iOS/Android **실기기**로 실제 동작 확인 — 지금까지 전부 시뮬레이터/에뮬레이터만 검증됨. **(2026-08-25 iOS 대부분 검증)** 실제 iPhone(무료 Apple ID 개인 서명)에 Development Build 설치 → LAN IP로 캐릭터 목록 로드 + 채팅방 진입 + 메시지 전송 + 실제 Gemini 응답 수신까지 확인. 시뮬레이터/코드 리뷰로는 못 잡았던 **실기기 전용 UX 버그 2개 발견/수정**: (1) `keyboardVerticalOffset={80}`이 시뮬레이터 기준 매직 넘버라 실기기에서 키보드가 입력창을 가림 — `useHeaderHeight()`로 동적 계산하도록 수정. (2) 전송 버튼의 `disabled={isSending}`이 스타일 변화 없이 그냥 안 눌리기만 해서, Gemini 응답 대기(20~40초) 중 사용자가 "안 눌리는" 버튼을 보고 실패한 줄 알고 재입력을 시도하는 혼란스러운 UX였음 — 버튼에 스피너/dim 스타일 추가 + "답변을 생각하는 중이에요…" 배너 신규 추가로 해결. Android 실기기는 USB 디버깅 트러블슈팅 실패(위 4주차 항목 참고)로 여전히 미검증. 음성 통화/설정 등 나머지 iOS 화면도 미검증.
- [ ] 스토어 필수 관문 항목 준비: 개인정보처리방침 URL, Google Play Data Safety 설문, Apple App Privacy 설문 + Export Compliance
- [ ] **(2026-08-25 확인)** `apps/client/assets/icon.png`가 실제로 Expo 기본 템플릿 아이콘(파란 "A" 블루프린트 스타일)인 것으로 확인됨 — Storia 브랜딩 아이콘으로 교체 필요. 디자인 방향(컬러/심볼)은 사용자 판단 필요.
- [ ] Release 빌드 파이프라인 — Fastlane 대신 **EAS Build** 검토(Expo 프로젝트라 서명 관리가 더 간단할 수 있음)
- [ ] iOS Release Build → TestFlight 내부 테스트 배포
- [ ] Android Release AAB → Google Play 내부 테스트 배포
- [x] **(2026-08-25)** README에 아키텍처/배포 방식/구현 범위 정리 (아래 "문서화" 섹션의 API 명세/블로그와는 별개 항목) — 루트 `README.md`에 "구현 범위"/"배포 현황" 섹션 신규 추가, Android 실행법/FCM·Sentry 환경변수/테스트·CI/Docker 빌드 명령어 보강, `docs/architecture/README.md`의 stale한 "Week 5" 헤딩도 정정.
- [ ] DeepLink는 이번 목표에서 필수 아님 — "Push/DeepLink/Streaming 중 핵심 기능" 요건은 Push(FCM)+Streaming(WS)으로 이미 충족됨, 시간 남으면 보너스로만 고려

**(2026-08-25 결정) 백엔드는 localhost/LAN 유지해도 됨 — 클라우드 배포는 이번 목표에 필수 아님**: TestFlight 내부 테스터(App Store Connect Users, 최대 100명)는 Apple Beta App Review 자체가 없고, Google Play 내부 테스트 트랙도 가벼운 정책 체크만 거쳐 정식 리뷰가 없음 — 즉 리뷰어가 백엔드 기능을 실제로 테스트하지 않음. 본인 폰으로 직접 확인할 때만 집 Wi-Fi(LAN IP)에 있으면 됨(단 iOS ATS/Android cleartext traffic 차단 때문에 평문 HTTP 예외 설정은 필요 — `Info.plist NSAllowsArbitraryLoads` 또는 도메인 예외, Android `usesCleartextTraffic`/network security config). 면접 등에서 집 밖에서 직접 시연하려면 그 시점에 ngrok 터널을 예비책으로 켜둘 것 — 지금 단계에서 클라우드 배포를 서두를 필요는 없음.

## 문서화 (진행 중 계속 갱신)

- [x] **(2026-08-25 확인)** API 명세 문서 — `docs/api/`는 빈 디렉토리(Swagger UI로 충분하다고 이미 판단된 흔적)이고, 실제 정리는 이미 [`docs/api.md`](./docs/api.md)에 REST/WS/WebRTC 엔드포인트별로 상세히 되어있었음(이전 세션에서 작성됐으나 이 체크박스에 반영이 안 돼있던 것으로 보임). 오늘 세션에서 stale해진 부분(3주차 per-device WS 토픽 스코프 변경 미반영)만 발견해 수정.
- [x] **(2026-08-25)** B안/C안 선택 이유 기술 블로그 초안 (PRD 7절 포트폴리오 활용 방안 참고) — [`docs/blog-webrtc-tradeoffs.md`](./docs/blog-webrtc-tradeoffs.md)에 초안 작성 완료. `docs/decisions.md` ADR-004(원본 결정 + 갱신 1~3)를 근거 자료로 사용. 실제 블로그 플랫폼에 발행하기 전 다듬을 여지 있음 — 초안 자체가 완료 기준.
