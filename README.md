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

Expo Go는 지원하지 않습니다 (커스텀 Native Module 사용 예정 — Development Build 필수).

## 개발 현황

Week 3 코드 작성 완료(실행 검증 전): RN 클라이언트(캐릭터 목록/채팅방 UI, REST + WebSocket 스트리밍 연동, WebSocket 재연결, 로딩/오류/재시도 UI, AsyncStorage 로컬 캐시) + Spring Boot 백엔드(REST API, STOMP WebSocket, Gemini 스트리밍 연동, DB 스키마, 캐릭터 시딩, CORS). 상세 현황은 [HANDOFF](./HANDOFF.md) 참고.
