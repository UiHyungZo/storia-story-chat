# Storia

AI 캐릭터와 텍스트/음성으로 대화하는 스토리 챗 앱. React Native(Expo) 클라이언트와 Spring Boot 백엔드로 구성된 풀스택 포트폴리오 프로젝트입니다.

자세한 요구사항은 [`PRD/Storia_PRD_v3.md`](./PRD/Storia_PRD_v3.md)를 참고하세요.

## 구조

```
apps/
├── client/     # React Native (Expo, TypeScript)
└── backend/    # Spring Boot 3.x (Java 17)
docs/           # ERD, API 명세, 아키텍처 다이어그램
```

## 문서

- [아키텍처](./docs/architecture/README.md) — 현재 구현 상태 및 목표 아키텍처(WebSocket/WebRTC 다이어그램 포함)
- [ERD](./docs/erd/README.md)
- [API](./docs/api.md) — REST 엔드포인트, 레이어 흐름, WebSocket/WebRTC 연동 정책
- [의사결정 기록(ADR)](./docs/decisions.md) — DB 선택, 인증 방식, 음성 통화 B안/C안 등 주요 트레이드오프와 그 갱신 이력
- [에러 처리 정책](./docs/error-handling.md)
- [테스트 정책](./docs/testing.md)
- [기술 블로그 초안 — WebRTC 트레이드오프](./docs/blog-webrtc-tradeoffs.md) — 음성 통화를 왜 처음부터 완전한 실시간 파이프라인으로 짜지 않았는지
- [HANDOFF](./HANDOFF.md) — 현재 진행 상태 및 인계 사항
- [TODO](./TODO.md) — 남은 작업 목록

## 로컬 실행

### 1. DB

```bash
docker compose up -d
```

### 2. 백엔드 (Spring Boot)

Gemini 응답을 받으려면 실행 전에 API 키를 환경변수로 설정하세요 (없으면 채팅은 되지만 고정 안내 문구만 돌아옵니다):

```bash
export GEMINI_API_KEY=your-api-key
```

음성 통화(5주차)를 쓰려면 추가로 LiveKit Cloud 프로젝트 + Google Cloud STT/TTS 키가 필요합니다 (없으면 음성 통화는 503, 텍스트 채팅은 정상 동작):

```bash
export LIVEKIT_HOST=your-project.livekit.cloud   # scheme(wss://) 없이
export LIVEKIT_API_KEY=your-livekit-api-key
export LIVEKIT_API_SECRET=your-livekit-api-secret
export LIVEKIT_EGRESS_AUDIO_WS_URL=wss://your-tunnel.example.com/egress/audio  # 로컬 개발 시 ngrok 등 터널 필요 (localhost 불가 — HANDOFF.md 참고)
export STT_API_KEY=your-google-cloud-key
export TTS_API_KEY=your-google-cloud-key
```

FCM 원격 푸시를 실제로 발송하려면 Firebase 서비스 계정 JSON이 필요합니다 (없으면 토큰은 저장되지만 발송은 조용히 no-op):

```bash
export FIREBASE_CREDENTIALS_PATH=/path/to/service-account.json
```

Sentry(에러 모니터링)는 선택 사항입니다 (없으면 SDK가 조용히 비활성화됨):

```bash
export SENTRY_DSN=your-sentry-dsn
```

```bash
cd apps/backend
./gradlew bootRun
```

- REST API: `http://localhost:8080/api/characters`
- WebSocket(STOMP): `ws://localhost:8080/ws`
- Swagger UI: `http://localhost:8080/swagger-ui.html`

백엔드 테스트(H2 인메모리 DB 사용 — MariaDB 없어도 실행됨):

```bash
./gradlew test
```

Docker로도 백엔드를 빌드/실행할 수 있습니다 (`apps/backend/Dockerfile`, multi-stage):

```bash
docker build -t storia-backend apps/backend
```

### 3. 클라이언트 (Expo)

Expo Go는 지원하지 않습니다 (커스텀 Native Module 사용 — Development Build 필수).

```bash
cd apps/client
npm install

# iOS (시뮬레이터, Xcode + CocoaPods 필요)
SENTRY_DISABLE_AUTO_UPLOAD=true npx expo run:ios   # 최초 1회 (Development Build 생성)

# Android (에뮬레이터/실기기, Android SDK 필요)
SENTRY_DISABLE_AUTO_UPLOAD=true npx expo run:android

npx expo start     # 이후 개발 시(Metro만 재기동)
```

> `SENTRY_DSN`/조직 설정이 없는 상태에서는 `@sentry/react-native`의 빌드 스크립트가 소스맵을
> 업로드하려다 실패해서 로컬 빌드 자체가 깨질 수 있습니다 — `SENTRY_DISABLE_AUTO_UPLOAD=true`로 우회하세요.

실기기 테스트 시 백엔드 주소를 자동 감지하지 못하므로 개발 머신의 LAN IP를 직접 지정해야 합니다 (`src/api/config.ts`는 iOS 시뮬레이터=`localhost`, Android 에뮬레이터=`10.0.2.2`까지만 자동 분기):

```bash
export EXPO_PUBLIC_API_BASE_URL=http://<개발-머신-LAN-IP>:8080
```

Sentry(선택 사항, 없으면 비활성화):

```bash
export EXPO_PUBLIC_SENTRY_DSN=your-sentry-dsn
```

클라이언트 테스트(`jest-expo`):

```bash
npm test
```

CI는 push/PR마다 위 두 테스트 스위트를 각각 별도 job으로 돌립니다 (`.github/workflows/ci.yml`, 계정/시크릿 불필요).

## 구현 범위

PRD v3 마일스톤(1~7주차) 코드는 전부 작성 완료했고, 로컬 환경(Docker/시뮬레이터/에뮬레이터)에서
실제 실행 검증까지 상당 부분 진행했습니다. 최종 완성도와 미검증 항목은 [HANDOFF](./HANDOFF.md)에
세션별로, 남은 작업은 [TODO](./TODO.md)에 주차별로 정리되어 있습니다.

- **텍스트 채팅**: REST + WebSocket(STOMP) 스트리밍, Gemini 연동, WS 재연결(지수 백오프), 로딩/오류/재시도 UI, AsyncStorage 로컬 캐시, MariaDB 히스토리 영속화
- **Native Module**: iOS(Swift, `RCTBridgeModule`)/Android(Kotlin, `ReactContextBaseJavaModule`) 둘 다 Haptic + 포그라운드 로컬 알림 구현 (`apps/client/modules/storia-native`, `expo prebuild`로도 살아남는 로컬 모듈 구조)
- **푸시 알림**: FCM 백엔드 연동 + 클라이언트 SDK 토큰 등록, 재참여(re-engagement) 스케줄러
- **음성 통화**: 클라이언트↔LiveKit 구간은 실제 WebRTC, 서버는 Track Egress로 오디오만 받아 기존 배치 STT/Gemini/TTS 파이프라인에 흘려보내는 절충안("축소판 A안") — 왜 완전한 양방향 실시간 대신 이 구조를 택했는지는 [기술 블로그 초안](./docs/blog-webrtc-tradeoffs.md) 참고
- **모니터링/테스트**: Sentry(클라이언트+백엔드), 백엔드 18개/클라이언트 17개 자동화 테스트, GitHub Actions CI, 백엔드 Dockerfile, 전역 REST 예외 처리기

**의도적으로 범위 밖에 둔 것**: 정식 로그인(디바이스 ID 기반 익명 세션으로 대체), 다중 대화 세션, TURN 서버, 완전한 양방향 실시간 음성(서버가 합성 음성을 WebRTC로 실시간 재전송), 상시 운영 클라우드 배포 — 근거는 [PRD 9절](./PRD/Storia_PRD_v3.md)과 [ADR](./docs/decisions.md) 참고.

## 배포 현황

목표는 스토어 정식 출시가 아니라 **"RN으로 iOS/Android 양쪽 실제 배포 파이프라인까지 처리할 수 있음"을 증명하는 것**입니다. 목표선은 TestFlight + Google Play 내부 테스트까지 — 상세 배경은 [TODO.md의 "배포 목표 재정의"](./TODO.md) 참고.

- [x] GitHub Actions CI (테스트), 백엔드 Dockerfile
- [ ] Apple Developer Program / Google Play Console 가입
- [ ] iOS/Android 실기기 검증 (현재까지는 시뮬레이터/에뮬레이터만)
- [ ] EAS Build로 릴리즈 빌드 → TestFlight/Play 내부 테스트 배포

백엔드는 상시 운영하지 않고, TestFlight/Play 내부 테스트 심사나 실제 데모 시점에만 로컬/LAN으로 기동하는 것으로 충분합니다(둘 다 정식 스토어 리뷰가 없어 리뷰어가 백엔드를 실제로 호출하지 않음). 근거는 `docs/decisions.md` ADR-007 참고.
