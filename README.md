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

- [아키텍처](./docs/architecture/README.md) — 현재 구현 상태 및 목표 아키텍처(WebSocket/WebRTC)
- [ERD](./docs/erd/README.md)
- [API](./docs/api.md) — REST 엔드포인트, 레이어 흐름, WebSocket/WebRTC 연동 정책
- [의사결정 기록(ADR)](./docs/decisions.md)
- [테스트 정책](./docs/testing.md)
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

### 3. 클라이언트 (Expo)

```bash
cd apps/client
npx expo run:ios   # 최초 1회 (Development Build 생성)
npx expo start     # 이후 개발 시
```

Sentry(선택 사항, 없으면 비활성화):

```bash
export EXPO_PUBLIC_SENTRY_DSN=your-sentry-dsn
```

Expo Go는 지원하지 않습니다 (커스텀 Native Module 사용 예정 — Development Build 필수).

## 개발 현황

Week 5 코드 작성 완료(실행 검증 전): RN 클라이언트(캐릭터 목록/채팅방 UI, REST + WebSocket 스트리밍 연동, WebSocket 재연결, 로딩/오류/재시도 UI, AsyncStorage 로컬 캐시, Swift Native Module로 Haptic+포그라운드 로컬 알림, LiveKit 기반 턴제 음성 통화 — "축소판 A안") + Spring Boot 백엔드(REST API, STOMP WebSocket, Gemini 스트리밍 연동, DB 스키마, 캐릭터 시딩, CORS, FCM 푸시 백엔드 스켈레톤, LiveKit Track Egress 수신용 raw WebSocket, 배치 STT/TTS). 상세 현황은 [HANDOFF](./HANDOFF.md) 참고.
