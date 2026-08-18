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

## 로컬 실행

### 1. DB

```bash
docker compose up -d
```

### 2. 백엔드 (Spring Boot)

```bash
cd apps/backend
./gradlew bootRun
```

- REST API: `http://localhost:8080/api/characters`
- Swagger UI: `http://localhost:8080/swagger-ui.html`

### 3. 클라이언트 (Expo)

```bash
cd apps/client
npx expo run:ios   # 최초 1회 (Development Build 생성)
npx expo start     # 이후 개발 시
```

Expo Go는 지원하지 않습니다 (커스텀 Native Module 사용 예정 — Development Build 필수).

## 개발 현황

Week 1 완료: RN 클라이언트(캐릭터 목록/채팅방 UI, 더미 데이터) + Spring Boot 백엔드(REST API, DB 스키마, 캐릭터 시딩) 스캐폴딩.
