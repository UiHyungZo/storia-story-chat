# PRD — AI 캐릭터 스토리 챗 (가칭 "Storia") v3

## 0. 변경 이력

- **v2**: 백엔드 레이어 신설 (Spring Boot). WebSocket 기반 실시간 스트리밍 추가.
- **v3**: **WebRTC 기반 음성 통화 기능 추가.** AI 캐릭터 서비스 + 실시간 음성 인터랙션을 요구하는 채용 공고 전반의 자격요건·주요업무를 폭넓게 커버하는 것을 목표로 범위 확장.

---

## 1. 개요

**목적**: React Native + Spring Boot 기반 풀스택 학습 및 포트폴리오 프로젝트. AI 캐릭터와 텍스트 채팅뿐 아니라 **실시간 음성 통화**로도 상호작용할 수 있는 앱을, 클라이언트-백엔드-실시간 통신(WebSocket+WebRTC)-Native Module까지 전 구간 직접 구현하여 증명한다.

**타겟 포지션**: **"AI Artist / Software Engineer / Full Stack"** 유형의 AI/엔터테인먼트 채용 공고를 1차 타겟으로 하되, 클라이언트 파트만 발췌해 RN 전문 프론트엔드 포지션에도 재사용 가능하도록 설계.

**핵심 증명 목표**
- RN + TypeScript + Expo 기반으로 실제 동작하는 앱을 처음부터 끝까지 만들 수 있다.
- Spring Boot 기반 백엔드를 설계·구현하고, RESTful API와 DB 모델링을 직접 할 수 있다.
- **WebSocket(텍스트 스트리밍) + WebRTC(음성 통화) 두 가지 실시간 통신 방식을 모두 구현할 수 있다.**
- LLM API 스트리밍 응답을 서버에서 받아 클라이언트로 실시간 중계할 수 있다.
- **AI가 생성한 텍스트를 TTS로 변환해 실시간 음성 스트림으로 송출하는 파이프라인을 구축할 수 있다.**
- Swift로 작성한 Native Module을 RN에서 호출할 수 있다.
- FCM 원격 푸시, Sentry 에러 모니터링까지 포함한 프로덕션급 구성을 다뤄봤다.
- TestFlight/App Store 제출 및 백엔드 배포까지 완주한 경험이 있다.

---

## 2. 사용자 시나리오

1. 유저가 앱을 열면 대화 가능한 캐릭터 목록이 보인다. (백엔드 API로부터 조회)
2. 캐릭터를 선택하면 채팅방으로 진입한다 (캐릭터당 하나의 연속 대화).
3. **텍스트 모드**: 유저가 메시지를 입력하면 WebSocket으로 전송 → 백엔드가 Gemini 스트리밍 응답을 청크 단위로 재전송 (타이핑 효과). 실패 시 REST 폴백.
4. **음성 통화 모드**: 유저가 채팅방 내 "통화" 버튼을 누르면 WebRTC 세션이 연결되고, 마이크로 말하면 STT → LLM 응답 생성 → TTS로 음성 합성된 캐릭터 음성이 실시간으로 재생된다 (턴제 음성 대화, 풀 듀플렉스 아님).
5. 응답 대기 중 로딩 상태가 표시되고, 실패 시 오류 메시지와 재시도 버튼이 뜬다.
6. 새 메시지가 도착하면 Haptic 피드백이 오고, 앱이 백그라운드/종료 상태면 FCM 푸시가 온다.
7. 대화 히스토리(텍스트)는 백엔드 DB에 저장되며, 로컬에도 캐싱되어 오프라인에서도 즉시 표시된다.
8. 앱/서버 에러는 Sentry로 수집되어 추적 가능하다.

---

## 3. 기능 요구사항

### 3.0 캐릭터 설계 (오리지널 캐릭터 3종)

*(v2와 동일)* 저작권 회피를 위해 완전 오리지널 캐릭터로 설계.

| 캐릭터 | 컨셉 | 말투/성격 | 아바타 |
| --- | --- | --- | --- |
| 아리아 (Aria) | 우주선 항법 AI가 인격을 얻어가는 SF 세계관 | 차분하고 논리적, 가끔 서툰 유머 | 파란 톤, 회로형 이어피스 |
| 렌 (Ren) | 폐업 위기의 작은 서점 운영자 | 따뜻하고 다정, 은근히 책 추천 강요 | 갈색 톤, 안경, 책장 배경 |
| 노아 (Noah) | 탐정 사무소 조수, 옴니버스 미스터리 | 능청스럽고 장난기, 사건 앞에선 진지 | 회갈색 톤, 페도라, 트렌치코트 |

캐릭터별로 **음성 프로필(TTS voice ID)**을 추가 메타데이터로 관리 — 캐릭터마다 톤이 다른 음성을 매핑.

> **주의**: 실존 인물, 유명 캐릭터, 로맨스/성적 뉘앙스는 배제.

### 3.1 캐릭터 목록 화면
*(v2와 동일)* `GET /api/characters`로 조회, 목록 탭 시 채팅방 이동.

### 3.2 채팅방 화면
*(v2와 동일)* + **통화 버튼 추가** — 채팅방 상단에 전화 아이콘, 탭 시 3.9의 통화 화면으로 전환.

### 3.3 AI 응답 연동 (텍스트)
*(v2와 동일)* WebSocket(STOMP) 기반 Gemini 스트리밍 중계, 실패 시 REST 폴백.

### 3.4 로딩 · 오류 · 재시도
*(v2와 동일)*

### 3.5 데이터 저장 및 상태 복원
*(v2와 동일)* PostgreSQL + AsyncStorage 캐싱. 음성 통화는 **텍스트로 전사된 요약만** 대화 히스토리에 남기고 음성 자체는 저장하지 않음 (저장 비용/개인정보 이슈 최소화, 포트폴리오 범위에서 충분).

### 3.6 푸시 알림
*(v2와 동일)*

### 3.7 Native Module — iOS(Swift)
*(v2와 동일)* Haptic + 로컬 알림.

### 3.8 에러/크래시 모니터링 (Sentry)
*(v2와 동일, 대상에 WebRTC 연결 실패 시나리오 추가)*

### 3.9 음성 통화 기능 (WebRTC) — 신규 핵심 파트

**목표**: JD의 "AI 생성 음성/영상 스트리밍 기반 통화 기능"을 최소 스코프로 증명. 영상은 제외하고 **음성 통화**로 범위를 좁혀 1인 개발 기준 현실적인 완성도를 확보한다.

**흐름 (턴제 음성 대화 — 풀 듀플렉스 실시간 아님)**
1. 클라이언트가 WebRTC 연결을 백엔드(Signaling 서버 역할 겸함)와 수립 — Offer/Answer + ICE Candidate 교환
2. 유저가 마이크 버튼을 누르고 말하면, 오디오 스트림을 캡처해 서버로 전송 (또는 클라이언트 STT 사용 후 텍스트만 전송하는 단순화 버전 중 택1, 아래 "구현 난이도별 대안" 참고)
3. 서버가 STT로 텍스트 변환 → 캐릭터 시스템 프롬프트 컨텍스트로 Gemini 호출 → 응답 텍스트 생성
4. 서버가 응답 텍스트를 TTS(예: Google Cloud TTS 또는 ElevenLabs 무료 티어)로 음성 합성
5. 합성된 오디오를 WebRTC 채널로 클라이언트에 스트리밍 재생

**구현 난이도별 대안 (일정 리스크 대비, 우선순위 순)**
- **A안 (풀 구현)**: 클라이언트 마이크 오디오를 WebRTC로 서버에 실시간 전송 → 서버 STT → LLM → TTS → WebRTC로 재생까지 전 구간 실시간 파이프라인
- **B안 (단순화, 권장 최소선)**: 클라이언트에서 RN 기본 STT(예: `@react-native-voice/voice`)로 로컬 음성 인식 후 **텍스트만** 서버에 WebSocket으로 전송 → LLM 응답 → 서버 TTS → **오디오 파일 URL**을 클라이언트가 받아 재생 (WebRTC 없이 WebSocket+오디오 스트리밍으로 대체). 이 경우 "실시간 음성 상호작용" 데모는 가능하지만 엄밀히는 WebRTC가 아니므로, JD의 "WebRTC 연동 경험" 자격요건은 **부분적으로만** 충족
- **C안 (WebRTC 자격요건 확보용 최소 데모)**: B안으로 핵심 기능은 완성하되, **별도로 WebRTC 시그널링 서버(Offer/Answer/ICE 교환)와 1:1 P2P 오디오 스트리밍 연결 자체만 최소 데모**로 구현해 "WebRTC 연동 경험"을 코드로 증명 (반드시 LLM 파이프라인과 통합될 필요는 없음 — 별도 데모 화면으로도 충분)

> **권장**: 일정과 난이도를 고려해 **B안(메인 기능) + C안(WebRTC 최소 데모 병행)** 조합을 기본 전략으로 한다. A안은 시간 여유가 있을 때 확장 목표로 둔다.

### 3.10 백엔드 아키텍처
*(v2 내용 유지)* + **Signaling 서버 역할** 추가 — WebRTC Offer/Answer/ICE Candidate를 WebSocket 채널로 중계 (STUN 서버는 공개 Google STUN 사용, TURN은 범위 밖).

### 3.11 배포 전략 (iOS / Android / Backend)
*(v2와 동일)* Fastlane+TestFlight, Firebase App Distribution, Docker+AWS/GCP+GitHub Actions.

### 3.12 상태 관리
*(v2와 동일)* Zustand — 캐릭터 목록, 대화 히스토리, WebSocket/WebRTC 연결 상태, 네트워크/로딩 상태.

---

## 4. 비기능 요구사항

*(v2와 동일)* + WebRTC 연결 실패/재연결 시나리오 처리, 통화 중 네트워크 끊김 시 안전한 세션 종료 처리.

---

## 5. 기술 스택

| 영역 | 기술 |
| --- | --- |
| 클라이언트 프레임워크 | React Native + Expo (Development Build) |
| 클라이언트 언어 | TypeScript |
| 상태 관리 | Zustand |
| Navigation | React Navigation |
| 로컬 캐시 | AsyncStorage |
| Native Module | Swift, `RCTBridgeModule` |
| **음성 통화** | **WebRTC (`react-native-webrtc`), STUN(Google 공개 서버)** |
| **음성 인식/합성** | **RN STT(`@react-native-voice/voice`) 또는 서버 STT, Google Cloud TTS / ElevenLabs(무료 티어)** |
| 백엔드 프레임워크 | Spring Boot 3.x |
| 백엔드 언어 | Java 또는 Kotlin |
| 실시간 통신 | WebSocket(STOMP) — 텍스트 스트리밍 + **WebRTC 시그널링 중계** |
| DB | PostgreSQL + JPA/Hibernate |
| API 문서화 | Swagger/OpenAPI |
| AI API | Google Gemini API (텍스트), TTS API (음성 합성) |
| 푸시 | FCM(백엔드 Admin SDK), Native Module 로컬 알림 |
| 모니터링 | Sentry (RN SDK + Spring Boot SDK) |
| 백엔드 배포 | Docker, AWS/GCP, GitHub Actions |
| 클라이언트 배포 | Fastlane + TestFlight(iOS), Firebase App Distribution(Android 테스트 배포) |
| 테스트 | Jest + RNTL(클라이언트), JUnit5 + Spring Boot Test(백엔드) |

---

## 6. 마일스톤 (예상 일정 — 7주로 조정)

| 주차 | 목표 |
| --- | --- |
| 1주차 | 프로젝트 스캐폴딩(RN+Spring Boot), Navigation, 캐릭터 목록/채팅방 UI, 백엔드 REST API+DB 스키마 |
| 2주차 | 백엔드-Gemini 연동(스트리밍), WebSocket(STOMP) 채널 구축, REST 폴백 |
| 3주차 | 로딩/오류/재시도, WebSocket 재연결, DB 히스토리 저장/복원, 로컬 캐시 동기화 |
| 4주차 | Swift Native Module(Haptic+로컬 알림), FCM 원격 푸시 |
| **5주차** | **음성 통화 B안 구현: RN STT → 서버 LLM → 서버 TTS → 오디오 재생 파이프라인** |
| **6주차** | **WebRTC 시그널링 서버 + 1:1 오디오 스트리밍 최소 데모(C안), B안과의 통합 여부 판단(시간 여유 시 A안 확장 시도)** |
| 7주차 | Sentry 연동(클라이언트+백엔드) 및 에러 시나리오 검증, 테스트 코드, Docker/Fastlane/GitHub Actions 배포 파이프라인, iOS/Android/백엔드 배포, 마무리 |

---

## 7. 포트폴리오 활용 방안

*(v2 내용 유지)* +
- **README에 "텍스트 스트리밍(WebSocket) vs 음성 통화(WebRTC)" 아키텍처를 구분해서 다이어그램으로 정리** — JD의 "WebSocket/WebRTC 연동 경험"을 명확히 시각적으로 어필
- B안/C안 구조를 선택한 이유(1인 개발 일정 내 현실적 완성도 확보)를 기술 블로그 형태로 문서화 — 풀 실시간 파이프라인(A안)과의 트레이드오프를 스스로 인지하고 설계했다는 점을 어필 포인트로 전환
- **AI Artist Full Stack 지원 시**: 백엔드+WebSocket+WebRTC+LLM+TTS 파이프라인 전체를 동일 비중으로 강조
- **RN 전문 포지션 지원 시**: 클라이언트 파트(RN 앱 구조, Native Module, 실시간 UX, 통화 화면 UI/상태관리)를 메인으로, 백엔드는 "협업 이해를 위해 직접 구현" 정도로 보조 언급

---

## 8. 리스크 및 제약

- Gemini API 무료 등급 요청 속도 제한 → 데모용으로는 충분, 실서비스 수준 아님을 명시
- Expo Development Build 세팅 난이도
- **WebRTC는 난이도가 높은 영역 — 풀 구현(A안) 실패 가능성을 전제로 B/C안 우선순위를 명확히 잡아둠. 최악의 경우 C안(WebRTC 최소 데모)만 완성하고 A안은 "다음 확장 계획"으로 문서화**
- 일정이 5주 → 7주로 늘어남 → 지원 시점과 완성 목표일을 미리 조율 필요
- TTS API 무료 티어의 사용량 제한 (Google Cloud TTS 등) — 데모 수준에서는 충분
- 실무 대비 격차는 여전히 존재 — "직접 만들어봄"의 증거이지 실서비스 운영 경험을 완전히 대체하진 못함

---

## 9. 범위에서 제외한 것 (Out of Scope)

- **영상 통화** — JD의 "음성/영상" 중 영상은 제외. 음성만으로도 WebRTC 연동 경험 증명에는 충분하다고 판단.
- **다중 대화 세션 관리**
- **정식 유저 인증 시스템 (JWT/OAuth)** — 디바이스 ID 기반 익명 세션으로 최소화
- **TURN 서버 구축** — NAT 환경 대응은 STUN까지만, TURN은 인프라 비용/난이도 대비 포트폴리오 기여도가 낮아 제외 (README에 "프로덕션에서는 TURN 필요"로 명시)
- **Google Play Console 정식 등록/출시** — Firebase App Distribution으로 대체
- **Android Native Module (Kotlin)**
- **풀 듀플렉스 실시간 음성 대화 (동시 말하기/끊기 감지 등)** — 턴제 방식으로 단순화
