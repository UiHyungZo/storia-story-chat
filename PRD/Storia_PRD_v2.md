# PRD — AI 캐릭터 스토리 챗 (가칭 "Storia") v2

## 0. 변경 이력

- **v2**: 백엔드 레이어 신설 (Spring Boot). 클라이언트가 Gemini API를 직접 호출하던 구조를 서버 경유 구조로 변경하고, WebSocket 기반 실시간 스트리밍을 추가. RN 클라이언트 단독 포트폴리오에서 **풀스택 포트폴리오**로 확장.

---

## 1. 개요

**목적**: React Native + Spring Boot 기반 풀스택 학습 및 포트폴리오 프로젝트. AI 캐릭터와 실시간으로 대화하며 스토리가 진행되는 채팅 앱을, 클라이언트부터 백엔드·실시간 통신·Native Module 연동까지 전 구간 직접 구현하여 증명한다. 특정 회사 하나를 겨냥한 게 아니라, RN + 백엔드를 함께 요구하는 AI/엔터테인먼트 채용 공고 전반에 재사용 가능한 범용 포트폴리오로 설계한다.

**핵심 증명 목표**
- RN + TypeScript + Expo 기반으로 실제 동작하는 앱을 처음부터 끝까지 만들 수 있다.
- **Spring Boot 기반 백엔드를 설계·구현하고, RESTful API와 DB 모델링을 직접 할 수 있다.**
- **WebSocket을 이용한 실시간 스트리밍 통신을 서버-클라이언트 양쪽에서 구현할 수 있다.**
- LLM API 스트리밍 응답을 서버에서 받아 클라이언트로 실시간 중계할 수 있다.
- Swift로 작성한 Native Module을 RN에서 호출할 수 있다 (JS↔Native 브릿지 설계 경험의 연장선).
- FCM 원격 푸시, Sentry 에러 모니터링까지 포함한 프로덕션급 구성을 다뤄봤다.
- TestFlight/App Store 제출 및 백엔드 배포까지 완주한 경험이 있다.

---

## 2. 사용자 시나리오

1. 유저가 앱을 열면 대화 가능한 캐릭터 목록이 보인다. (백엔드 API로부터 조회)
2. 캐릭터를 선택하면 해당 캐릭터와의 채팅방으로 진입한다 (캐릭터당 하나의 연속 대화).
3. 유저가 메시지를 입력하면, **클라이언트는 WebSocket으로 백엔드에 메시지를 전송**하고, **백엔드는 Gemini API에 스트리밍 요청을 보낸 뒤, 응답 청크를 다시 WebSocket으로 클라이언트에 실시간 중계**한다 (타이핑 효과). 스트리밍 실패 시 REST 기반 일반 응답으로 자동 폴백한다.
4. 응답 대기 중 로딩 상태가 표시되고, 실패 시 오류 메시지와 재시도 버튼이 뜬다.
5. 새 메시지가 도착하면 기기가 진동하고(Haptic), 앱이 백그라운드/종료 상태면 원격 푸시(FCM)로 알림이 온다.
6. 대화 히스토리는 **백엔드 DB에 저장**되며, 오프라인 대비용으로 로컬에도 캐싱되어 앱을 재시작해도 상태가 즉시 복원된다.
7. 앱 실행 중 발생하는 에러/크래시는 Sentry로 수집되어 추적 가능하다 (클라이언트+서버 양쪽).

---

## 3. 기능 요구사항

### 3.0 캐릭터 설계 (오리지널 캐릭터 3종)

*(v1과 동일)* 저작권 문제를 피하기 위해 기존 IP를 차용하지 않고, 완전히 오리지널 캐릭터로 설계한다.

| 캐릭터 | 컨셉 | 말투/성격 | 아바타 |
| --- | --- | --- | --- |
| 아리아 (Aria) | 우주선 항법 AI가 인격을 얻어가는 과정을 담은 SF 세계관 | 차분하고 논리적이지만 가끔 서툰 유머를 시도함 | 파란 톤, 회로형 이어피스 (제작 완료) |
| 렌 (Ren) | 폐업 위기의 작은 서점을 운영하는 인물 | 따뜻하고 다정하지만 은근히 책 추천을 강요함 | 갈색 톤, 안경, 책장 배경 (제작 완료) |
| 노아 (Noah) | 탐정 사무소 조수, 유저와 소소한 미스터리를 풀어나가는 옴니버스 스토리 | 능청스럽고 장난기 많지만 사건 앞에서는 진지함 | 회갈색 톤, 페도라, 트렌치코트 (제작 완료) |

캐릭터 시스템 프롬프트와 메타데이터는 **백엔드 DB(Character 테이블)에서 관리**하며, 신규 캐릭터 추가 시 코드 배포 없이 데이터 삽입만으로 가능한 구조로 설계한다.

> **주의**: 실존 인물, 유명 캐릭터, 로맨스/성적 뉘앙스가 있는 캐릭터는 배제하고 담백한 세계관으로 구성한다.

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

### 3.3 AI 응답 연동 (백엔드 경유 구조로 변경)
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

### 3.5 데이터 저장 및 상태 복원 (백엔드 DB + 로컬 캐시)
- **원본 데이터는 백엔드 DB(PostgreSQL)에 저장** — User, Character, Conversation, Message 테이블
- 클라이언트는 AsyncStorage에 최근 대화를 캐싱하여 오프라인 진입 시 즉시 표시, 온라인 복귀 시 서버와 동기화
- 앱 재실행 시 마지막 화면, 마지막 대화 상태를 그대로 복원

### 3.6 푸시 알림
- **로컬 알림**: 새 메시지 도착 시 Haptic 피드백 + 포그라운드 로컬 알림 (Native Module)
- **원격 푸시 (FCM)**: 앱이 백그라운드/종료 상태일 때 **백엔드가 FCM Admin SDK로 발송**. 헤이영에서 다뤘던 "WebView 준비 상태와 푸시 도착 시점 어긋남" 문제의식을 RN 환경(JS 컨텍스트 초기화 시점, 알림 권한 상태)에 맞게 재적용

### 3.7 Native Module — iOS(Swift) 중심 (핵심 증명 포인트)
*(v1과 동일)*
- Swift로 Native Module 작성 (`RCTBridgeModule` 채택)
- Haptic 피드백 + 로컬 알림 트리거를 RN에서 `NativeModules.HapticNotifier.notify()` 형태로 호출
- 기존 IBK 프로젝트의 JS↔Native 브릿지(PluginRegistry 패턴)와 동일한 사고방식을 RN 환경에 적용했음을 코드로 증명

### 3.8 에러/크래시 모니터링 (Sentry)
- Sentry SDK를 RN 프로젝트 **및 Spring Boot 백엔드** 양쪽에 연동
- JS 레이어 에러, Native(Swift) 레이어 크래시, **백엔드 예외(WebSocket 연결 오류, Gemini API 타임아웃 등)**를 모두 수집
- 의도적으로 에러 시나리오 1~2개(클라이언트 1개, 백엔드 1개)를 만들어, Sentry 대시보드에서 실제로 에러를 확인하고 수정하는 과정을 문서화

### 3.9 백엔드 아키텍처 (신규)
- **Spring Boot 3.x + Java(or Kotlin)**
- 계층 구조: Controller(REST) / WebSocket Handler(STOMP) / Service / Repository(JPA) / Entity
- **DB**: PostgreSQL — 주요 테이블
  - `User` (인증은 간단한 디바이스 ID 기반 또는 익명 세션으로 최소화, 범위 확장 시 JWT 인증 추가)
  - `Character` (이름, 시스템 프롬프트, 아바타 URL, 세계관 메타데이터)
  - `Conversation` (User-Character 1:1 매핑, 캐릭터당 단일 세션)
  - `Message` (role: user/assistant, content, timestamp, conversationId FK)
- **WebSocket**: `spring-boot-starter-websocket` + STOMP, 토픽 구독 방식(`/topic/conversation/{id}`)으로 메시지 브로드캐스트
- **Gemini 연동**: 백엔드에서 Gemini 스트리밍 API 호출, Server-Sent Events 형태 응답을 파싱해 WebSocket 프레임으로 재발행
- **API 문서화**: Swagger/OpenAPI로 REST 엔드포인트 문서 자동화

### 3.10 배포 전략 (iOS / Android / Backend)

- **iOS**: Fastlane으로 코드 서명·archive·업로드 자동화 후 TestFlight 배포 (App Store 정식 출시는 선택)
- **Android**: **Google Play Console 개발자 등록은 하지 않음** — 등록비 결제와 심사 절차가 필요해 완성 목표 일정에 맞지 않음. 대신 **Firebase App Distribution**으로 서명된 APK/AAB를 테스터에게 배포
- **Backend**: **Docker 컨테이너화** 후 클라우드(AWS EC2 또는 GCP Cloud Run/Compute Engine) 배포. RDS(PostgreSQL) 또는 managed DB 사용. GitHub Actions로 빌드·테스트·배포 자동화
- **자동화**: GitHub Actions로 3개 파이프라인 구성 — iOS(Fastlane→TestFlight), Android(Fastlane→Firebase App Distribution), Backend(Docker build→클라우드 배포)

> **표현 주의**: 이력서/포트폴리오에 기술할 때 "Android 출시"라고 쓰지 말고 **"Android 테스트 배포(Firebase App Distribution)"**로 정확히 구분해서 표기한다.
> 예시 문구: "React Native 클라이언트와 Spring Boot 백엔드를 함께 설계·구현. WebSocket 기반 실시간 스트리밍으로 LLM 응답을 중계하고, iOS는 App Store/TestFlight, Android는 Firebase App Distribution으로 테스트 배포. 백엔드는 Docker화하여 클라우드에 배포하고 GitHub Actions로 전체 파이프라인 자동화."

### 3.11 상태 관리
- Zustand로 클라이언트 전역 상태 관리: 캐릭터 목록, 현재 대화 히스토리, WebSocket 연결 상태, 네트워크/로딩 상태

---

## 4. 비기능 요구사항

- iOS 시뮬레이터 및 실기기에서 정상 동작
- 앱 크래시 없이 네트워크 끊김/재연결, WebSocket 재연결 시나리오 처리
- 백엔드는 동시 다중 WebSocket 세션을 안정적으로 처리 (최소 부하 테스트 수준 검증)
- 코드에 최소한의 단위/통합 테스트 포함
  - 클라이언트: Native Module 호출부 목업 테스트
  - 백엔드: Service 레이어 단위 테스트 + WebSocket 통합 테스트
- Git 기반 커밋 히스토리를 의미 단위로 관리 (실제 협업처럼 보이도록 커밋 단위/메시지 정리), 프론트/백엔드 리포지토리 분리 또는 모노레포 구조 중 선택

---

## 5. 기술 스택

| 영역 | 기술 |
| --- | --- |
| **클라이언트 프레임워크** | React Native + Expo (Development Build) |
| **클라이언트 언어** | TypeScript |
| 상태 관리 | Zustand |
| Navigation | React Navigation |
| 로컬 캐시 | AsyncStorage |
| Native Module | Swift, `RCTBridgeModule` |
| **백엔드 프레임워크** | **Spring Boot 3.x** |
| **백엔드 언어** | **Java 또는 Kotlin** |
| **실시간 통신** | **WebSocket (STOMP), `spring-boot-starter-websocket`** |
| **DB** | **PostgreSQL + JPA/Hibernate** |
| **API 문서화** | **Swagger/OpenAPI** |
| AI API | Google Gemini API (백엔드에서 스트리밍 호출) |
| 푸시 | Firebase Cloud Messaging (백엔드에서 Admin SDK로 발송), Native Module 기반 로컬 알림 |
| 모니터링 | Sentry (React Native SDK + Spring Boot SDK) |
| **백엔드 배포** | **Docker, AWS/GCP, GitHub Actions** |
| 클라이언트 배포 | Fastlane + TestFlight (iOS), Firebase App Distribution (Android 테스트 배포) |
| 테스트 | Jest + React Native Testing Library (클라이언트), JUnit5 + Spring Boot Test (백엔드) |

> **참고**: Native Module 작성을 위해서는 Expo Development Build가 필요합니다 (Expo Go에서는 커스텀 Native Module 실행 불가).

---

## 6. 마일스톤 (예상 일정 — 5주로 조정)

| 주차 | 목표 |
| --- | --- |
| 1주차 | 프로젝트 스캐폴딩 (RN + Spring Boot 양쪽), Navigation, 캐릭터 목록/채팅방 UI (더미 데이터), 백엔드 기본 REST API + DB 스키마 설계 |
| 2주차 | 백엔드-Gemini 연동(스트리밍), WebSocket(STOMP) 채널 구축 및 클라이언트 연동, REST 폴백 처리 |
| 3주차 | 로딩/오류/재시도, WebSocket 재연결 로직, DB 기반 히스토리 저장/복원, 로컬 캐시 동기화 |
| 4주차 | Swift Native Module(Haptic+로컬 알림), FCM 원격 푸시 연동(백엔드 발송 포함) |
| 5주차 | Sentry 연동(클라이언트+백엔드) 및 에러 시나리오 검증, 테스트 코드 작성, Docker/Fastlane/GitHub Actions 배포 파이프라인, iOS TestFlight + Android Firebase App Distribution + 백엔드 클라우드 배포, 마무리 |

---

## 7. 포트폴리오 활용 방안

- README에 아키텍처 다이어그램 포함: RN ↔ WebSocket ↔ Spring Boot ↔ Gemini 흐름, RN ↔ Native Module 브릿지 구조, 푸시/모니터링 통합 구조
- IBK 프로젝트의 JS↔Native 브릿지 설계, 헤이영의 FCM 진입 흐름 개선 경험과 이 프로젝트의 구현을 나란히 비교하는 섹션 작성
- **백엔드 설계 문서(ERD, API 명세)를 별도로 정리하여 풀스택 역량을 명확히 증명**
- Sentry 대시보드 스크린샷 및 에러 추적/해결 과정을 문서화해 트러블슈팅 역량의 실물 증거로 활용
- GitHub 공개 리포지토리(클라이언트+백엔드) + TestFlight 링크(iOS) + Firebase App Distribution 링크(Android) + 백엔드 API 엔드포인트(데모용)를 이력서/포트폴리오에 첨부
- 이력서 표기 시 "Android 출시"가 아닌 "Android 테스트 배포(Firebase App Distribution)"로 정확히 구분해서 기재
- **"Spring Boot를 백엔드로 선택한 이유"를 한 줄 명시** (기존 실무 스택 재적용, 안정적 설계·배포까지 완주 가능)

---

## 8. 리스크 및 제약

- Gemini API 무료 등급의 요청 속도 제한 → 데모용으로는 충분하나 실제 서비스 수준은 아님을 명시
- Expo Development Build 세팅 난이도가 Expo Go보다 높음 → 학습 곡선 존재
- **백엔드 추가로 일정이 4주 → 5주로 늘어남 → 범위 관리에 유의**
- **WebSocket + Gemini 스트리밍 연동은 구현 난이도가 있어, 최악의 경우 REST 폴백만으로 데모하고 스트리밍은 부분 구현으로 문서화할 가능성 있음**
- 실무 RN/백엔드 개발자와의 격차를 완전히 메우진 못함 — 이 프로젝트는 "학습 중"을 "직접 만들어봄"으로 바꾸는 증거용

---

## 9. 범위에서 제외한 것 (Out of Scope)

- **음성 기능 (TTS/STT)** — 구현 난이도 대비 핵심 증명 목표에 기여하는 바가 적어 제외. 여유가 되면 추후 확장 가능.
- **다중 대화 세션 관리** — 캐릭터당 단일 연속 대화로 단순화.
- **정식 유저 인증 시스템 (JWT/OAuth)** — 최소 범위로는 디바이스 ID 기반 익명 세션으로 대체. 시간 여유 시 JWT 기반 인증 추가 고려.
- **Google Play Console 개발자 등록 및 정식 출시** — Firebase App Distribution을 통한 테스트 배포로 대체.
- **Android Native Module (Kotlin)** — iOS(Swift) Native Module만으로 핵심 역량 증명은 충분하다고 판단, 제외.
- **WebRTC 기반 음성/영상 실시간 통화** — 이번 프로젝트는 텍스트 채팅의 실시간 스트리밍(WebSocket)까지만 다루고, 음성/영상 스트리밍 통화는 범위 밖. 관련 JD 대응 시 "다음 확장 계획"으로 언급 가능.
