# PRD — AI 캐릭터 스토리 챗 (가칭 "Storia") v3 (병합본)

## 0. 변경 이력

- **v2**: 백엔드 레이어 신설 (Spring Boot). 클라이언트가 Gemini API를 직접 호출하던 구조를 서버 경유 구조로 변경하고, WebSocket 기반 실시간 스트리밍을 추가. RN 클라이언트 단독 포트폴리오에서 **풀스택 포트폴리오**로 확장. (DB: PostgreSQL)
- **v3**: **WebRTC 기반 음성 통화 기능 추가.** AI 캐릭터 서비스 + 실시간 음성 인터랙션을 요구하는 채용 공고 전반의 자격요건·주요업무를 폭넓게 커버하는 것을 목표로 범위 확장. **DB를 PostgreSQL에서 MariaDB로 변경.**
- **v3 병합본**: v3 각 섹션에 남아있던 `(v2와 동일)` 참조를 v2 원문으로 전개해 단일 문서로 완결. 내용 변경 없음 — 상호 참조 없이도 이 문서 하나로 전체 요구사항을 읽을 수 있게 하는 것이 목적. v2/v3 각각의 스냅샷은 [`Storia_PRD_v2.md`](./Storia_PRD_v2.md), [`Storia_PRD_v3.md`](./Storia_PRD_v3.md)로 유지.

---

## 1. 개요

**목적**: React Native + Spring Boot 기반 풀스택 학습 및 포트폴리오 프로젝트. AI 캐릭터와 텍스트 채팅뿐 아니라 **실시간 음성 통화**로도 상호작용할 수 있는 앱을, 클라이언트-백엔드-실시간 통신(WebSocket+WebRTC)-Native Module까지 전 구간 직접 구현하여 증명한다.

**타겟 포지션**: **"AI Artist / Software Engineer / Full Stack"** 유형의 타겟으로 하되, 클라이언트 파트만 발췌해 RN 전문 프론트엔드 포지션에도 재사용 가능하도록 설계.

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

저작권 문제를 피하기 위해 기존 IP를 차용하지 않고, 완전히 오리지널 캐릭터로 설계한다.

| 캐릭터 | 컨셉 | 말투/성격 | 아바타 |
| --- | --- | --- | --- |
| 아리아 (Aria) | 우주선 항법 AI가 인격을 얻어가는 SF 세계관 | 차분하고 논리적, 가끔 서툰 유머 | 파란 톤, 회로형 이어피스 |
| 렌 (Ren) | 폐업 위기의 작은 서점 운영자 | 따뜻하고 다정, 은근히 책 추천 강요 | 갈색 톤, 안경, 책장 배경 |
| 노아 (Noah) | 탐정 사무소 조수, 옴니버스 미스터리 | 능청스럽고 장난기, 사건 앞에선 진지 | 회갈색 톤, 페도라, 트렌치코트 |

캐릭터 시스템 프롬프트와 메타데이터는 **백엔드 DB(Character 테이블)에서 관리**하며, 신규 캐릭터 추가 시 코드 배포 없이 데이터 삽입만으로 가능한 구조로 설계한다. 캐릭터별로 **음성 프로필(TTS voice ID)**을 추가 메타데이터로 관리 — 캐릭터마다 톤이 다른 음성을 매핑.

> **주의**: 실존 인물, 유명 캐릭터, 로맨스/성적 뉘앙스는 배제하고 담백한 세계관으로 구성한다.

### 3.1 캐릭터 목록 화면

- 백엔드 `GET /api/characters`로 캐릭터 목록 + 마지막 대화 미리보기/시각 조회
- 목록 탭 시 채팅방으로 이동 (React Navigation)
- 캐릭터당 하나의 연속 대화만 유지 (다중 세션 관리는 범위 밖)

### 3.2 채팅방 화면

- 메시지 리스트 (FlatList, 역순 렌더링), 최초 진입 시 `GET /api/conversations/{characterId}/messages`로 히스토리 로드
- 유저 메시지 / AI 메시지 말풍선 스타일 구분
- 하단 텍스트 입력창 + 전송 버튼 → 전송 시 WebSocket으로 메시지 발행
- AI 응답 스트리밍 중 타이핑 인디케이터(점 3개 애니메이션), 서버로부터 청크 수신할 때마다 갱신
- 키보드 표시 시 레이아웃 자동 조정 (KeyboardAvoidingView)
- **(v3 추가) 통화 버튼**: 채팅방 상단에 전화 아이콘 추가, 탭 시 3.9의 통화 화면으로 전환

### 3.3 AI 응답 연동 (텍스트)

- **클라이언트는 Gemini API를 직접 호출하지 않는다.** 모든 LLM 요청은 Spring Boot 백엔드를 경유.
- 백엔드가 Google Gemini API와 통신 (API 키는 서버에서만 관리 → 클라이언트에 키 노출 없음, 보안상으로도 개선점)
- **WebSocket(STOMP) 기반 실시간 스트리밍**: 유저 메시지 발행 → 서버가 Gemini 스트리밍 응답 수신 → 청크 단위로 클라이언트에 재전송
- 스트리밍 실패/타임아웃 시 REST `POST /api/conversations/{characterId}/messages`로 자동 폴백 (일반 응답)
- 캐릭터별 시스템 프롬프트는 DB에서 조회하여 매 요청 시 컨텍스트로 주입

### 3.4 로딩 · 오류 · 재시도

- API/WebSocket 요청 중 로딩 상태를 명확히 표시 (스켈레톤 또는 타이핑 인디케이터)
- 요청 실패 시 오류 메시지와 재시도 버튼 노출
- WebSocket 연결 끊김 감지 시 자동 재연결 로직 (exponential backoff)
- 네트워크 재연결 감지 시 자동 재시도 옵션 제공

### 3.5 데이터 저장 및 상태 복원

- 원본 데이터는 백엔드 DB(**MariaDB**)에 저장 — User, Character, Conversation, Message 테이블
- 클라이언트는 AsyncStorage에 최근 대화를 캐싱하여 오프라인 진입 시 즉시 표시, 온라인 복귀 시 서버와 동기화
- 앱 재실행 시 마지막 화면, 마지막 대화 상태를 그대로 복원
- **(v3 추가)** 음성 통화는 **텍스트로 전사된 요약만** 대화 히스토리에 남기고 음성 자체는 저장하지 않음 (저장 비용/개인정보 이슈 최소화, 포트폴리오 범위에서 충분)

### 3.6 푸시 알림

- **로컬 알림**: 새 메시지 도착 시 Haptic 피드백 + 포그라운드 로컬 알림 (Native Module)
- **원격 푸시 (FCM)**: 앱이 백그라운드/종료 상태일 때 **백엔드가 FCM Admin SDK로 발송**. 헤이영에서 다뤘던 "WebView 준비 상태와 푸시 도착 시점 어긋남" 문제의식을 RN 환경(JS 컨텍스트 초기화 시점, 알림 권한 상태)에 맞게 재적용

### 3.7 Native Module — iOS(Swift) 중심 (핵심 증명 포인트)

- Swift로 Native Module 작성 (`RCTBridgeModule` 채택)
- Haptic 피드백 + 로컬 알림 트리거를 RN에서 `NativeModules.HapticNotifier.notify()` 형태로 호출
- 기존 IBK 프로젝트의 JS↔Native 브릿지(PluginRegistry 패턴)와 동일한 사고방식을 RN 환경에 적용했음을 코드로 증명

### 3.8 에러/크래시 모니터링 (Sentry)

- Sentry SDK를 RN 프로젝트 및 Spring Boot 백엔드 양쪽에 연동
- JS 레이어 에러, Native(Swift) 레이어 크래시, 백엔드 예외(WebSocket 연결 오류, Gemini API 타임아웃 등)를 모두 수집
- **(v3 추가)** 수집 대상에 **WebRTC 연결 실패 시나리오** 추가
- 의도적으로 에러 시나리오 1~2개(클라이언트 1개, 백엔드 1개)를 만들어, Sentry 대시보드에서 실제로 에러를 확인하고 수정하는 과정을 문서화

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

- **Spring Boot 3.x + Java(or Kotlin)**
- 계층 구조: Controller(REST) / WebSocket Handler(STOMP) / Service / Repository(JPA) / Entity
- **DB**: **MariaDB** — 주요 테이블
  - `User` (인증은 간단한 디바이스 ID 기반 또는 익명 세션으로 최소화, 범위 확장 시 JWT 인증 추가)
  - `Character` (이름, 시스템 프롬프트, 아바타 URL, 세계관 메타데이터)
  - `Conversation` (User-Character 1:1 매핑, 캐릭터당 단일 세션)
  - `Message` (role: user/assistant, content, timestamp, conversationId FK)
- **WebSocket**: `spring-boot-starter-websocket` + STOMP, 토픽 구독 방식(`/topic/conversation/{id}`)으로 메시지 브로드캐스트
- **Gemini 연동**: 백엔드에서 Gemini 스트리밍 API 호출, Server-Sent Events 형태 응답을 파싱해 WebSocket 프레임으로 재발행
- **API 문서화**: Swagger/OpenAPI로 REST 엔드포인트 문서 자동화
- **(v3 추가) Signaling 서버 역할**: WebRTC Offer/Answer/ICE Candidate를 WebSocket 채널로 중계 (STUN 서버는 공개 Google STUN 사용, TURN은 범위 밖)

### 3.11 배포 전략 (iOS / Android / Backend)

- **iOS**: Fastlane으로 코드 서명·archive·업로드 자동화 후 TestFlight 배포 (App Store 정식 출시는 선택)
- **Android**: **Google Play Console 개발자 등록은 하지 않음** — 등록비 결제와 심사 절차가 필요해 완성 목표 일정에 맞지 않음. 대신 **Firebase App Distribution**으로 서명된 APK/AAB를 테스터에게 배포
- **Backend**: **Docker 컨테이너화** 후 클라우드(AWS EC2 또는 GCP Cloud Run/Compute Engine) 배포. Managed DB(MariaDB) 사용. GitHub Actions로 빌드·테스트·배포 자동화
- **자동화**: GitHub Actions로 3개 파이프라인 구성 — iOS(Fastlane→TestFlight), Android(Fastlane→Firebase App Distribution), Backend(Docker build→클라우드 배포)

> **표현 주의**: 이력서/포트폴리오에 기술할 때 "Android 출시"라고 쓰지 말고 **"Android 테스트 배포(Firebase App Distribution)"**로 정확히 구분해서 표기한다.
> 예시 문구: "React Native 클라이언트와 Spring Boot 백엔드를 함께 설계·구현. WebSocket 기반 실시간 스트리밍으로 LLM 응답을 중계하고, iOS는 App Store/TestFlight, Android는 Firebase App Distribution으로 테스트 배포. 백엔드는 Docker화하여 클라우드에 배포하고 GitHub Actions로 전체 파이프라인 자동화."

### 3.12 상태 관리

- Zustand로 클라이언트 전역 상태 관리: 캐릭터 목록, 대화 히스토리, **WebSocket/WebRTC 연결 상태**, 네트워크/로딩 상태

---

## 4. 비기능 요구사항

- iOS 시뮬레이터 및 실기기에서 정상 동작
- 앱 크래시 없이 네트워크 끊김/재연결, WebSocket 재연결 시나리오 처리
- 백엔드는 동시 다중 WebSocket 세션을 안정적으로 처리 (최소 부하 테스트 수준 검증)
- 코드에 최소한의 단위/통합 테스트 포함
  - 클라이언트: Native Module 호출부 목업 테스트
  - 백엔드: Service 레이어 단위 테스트 + WebSocket 통합 테스트
- Git 기반 커밋 히스토리를 의미 단위로 관리 (실제 협업처럼 보이도록 커밋 단위/메시지 정리), 프론트/백엔드 리포지토리 분리 또는 모노레포 구조 중 선택
- **(v3 추가)** WebRTC 연결 실패/재연결 시나리오 처리, 통화 중 네트워크 끊김 시 안전한 세션 종료 처리

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
| DB | MariaDB + JPA/Hibernate |
| API 문서화 | Swagger/OpenAPI |
| AI API | Google Gemini API (텍스트), TTS API (음성 합성) |
| 푸시 | FCM(백엔드 Admin SDK), Native Module 로컬 알림 |
| 모니터링 | Sentry (RN SDK + Spring Boot SDK) |
| 백엔드 배포 | Docker, AWS/GCP, GitHub Actions |
| 클라이언트 배포 | Fastlane + TestFlight(iOS), Firebase App Distribution(Android 테스트 배포) |
| 테스트 | Jest + RNTL(클라이언트), JUnit5 + Spring Boot Test(백엔드) |

> **참고**: Native Module 작성을 위해서는 Expo Development Build가 필요합니다 (Expo Go에서는 커스텀 Native Module 실행 불가).

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

- README에 아키텍처 다이어그램 포함: RN ↔ WebSocket ↔ Spring Boot ↔ Gemini 흐름, RN ↔ Native Module 브릿지 구조, 푸시/모니터링 통합 구조
- IBK 프로젝트의 JS↔Native 브릿지 설계, 헤이영의 FCM 진입 흐름 개선 경험과 이 프로젝트의 구현을 나란히 비교하는 섹션 작성
- **백엔드 설계 문서(ERD, API 명세)를 별도로 정리하여 풀스택 역량을 명확히 증명**
- Sentry 대시보드 스크린샷 및 에러 추적/해결 과정을 문서화해 트러블슈팅 역량의 실물 증거로 활용
- GitHub 공개 리포지토리(클라이언트+백엔드) + TestFlight 링크(iOS) + Firebase App Distribution 링크(Android) + 백엔드 API 엔드포인트(데모용)를 이력서/포트폴리오에 첨부
- 이력서 표기 시 "Android 출시"가 아닌 "Android 테스트 배포(Firebase App Distribution)"로 정확히 구분해서 기재
- **"Spring Boot를 백엔드로 선택한 이유"를 한 줄 명시** (기존 실무 스택 재적용, 안정적 설계·배포까지 완주 가능)
- **(v3 추가) README에 "텍스트 스트리밍(WebSocket) vs 음성 통화(WebRTC)" 아키텍처를 구분해서 다이어그램으로 정리** — JD의 "WebSocket/WebRTC 연동 경험"을 명확히 시각적으로 어필
- **(v3 추가)** B안/C안 구조를 선택한 이유(1인 개발 일정 내 현실적 완성도 확보)를 기술 블로그 형태로 문서화 — 풀 실시간 파이프라인(A안)과의 트레이드오프를 스스로 인지하고 설계했다는 점을 어필 포인트로 전환
- **(v3 추가) AI Artist Full Stack 지원 시**: 백엔드+WebSocket+WebRTC+LLM+TTS 파이프라인 전체를 동일 비중으로 강조
- **(v3 추가) RN 전문 포지션 지원 시**: 클라이언트 파트(RN 앱 구조, Native Module, 실시간 UX, 통화 화면 UI/상태관리)를 메인으로, 백엔드는 "협업 이해를 위해 직접 구현" 정도로 보조 언급

---

## 8. 리스크 및 제약

- Gemini API 무료 등급 요청 속도 제한 → 데모용으로는 충분, 실서비스 수준 아님을 명시
- Expo Development Build 세팅 난이도
- WebSocket + Gemini 스트리밍 연동은 구현 난이도가 있어, 최악의 경우 REST 폴백만으로 데모하고 스트리밍은 부분 구현으로 문서화할 가능성 있음
- **WebRTC는 난이도가 높은 영역 — 풀 구현(A안) 실패 가능성을 전제로 B/C안 우선순위를 명확히 잡아둠. 최악의 경우 C안(WebRTC 최소 데모)만 완성하고 A안은 "다음 확장 계획"으로 문서화**
- 일정이 4주(v1) → 5주(v2, 백엔드 추가) → **7주(v3, WebRTC 추가)**로 늘어남 → 지원 시점과 완성 목표일을 미리 조율 필요
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

> v2 시점에는 음성 기능(TTS/STT) 자체와 WebRTC 기반 음성/영상 통화가 이 목록(Out of Scope)에 있었으나, v3에서 핵심 기능(3.9)으로 범위에 편입되었다.
