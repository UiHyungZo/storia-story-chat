# Decisions (ADR)

Storia 개발 과정에서 내린 주요 기술/설계 결정을 기록한다. 각 항목은 "왜 이렇게 했는가"를 남기는 게 목적 — 코드/PRD만 봐서는 알 수 없는 트레이드오프 판단을 추적한다. 새 결정은 아래에 이어서 추가한다 (최신이 아래).

---

## ADR-001: DB를 PostgreSQL → MariaDB로 변경

**날짜**: PRD v3, 커밋 `4e973a1`(문서), `395b2ca`(코드)

**배경**: PRD v2까지는 PostgreSQL이었으나, v3에서 MariaDB로 전환.

**결정**: 로컬/배포 DB로 MariaDB 사용.

**트레이드오프**: PostgreSQL 대비 일부 고급 기능(JSONB, 배열 타입 등)은 없지만, 포트폴리오 타겟 채용 공고 스택과의 정합성을 우선했다. `USER`, `CHARACTER` 등 MySQL/MariaDB 계열 예약어와의 충돌 이슈가 새로 생겨(ADR-002 참고) 마이그레이션 비용이 있었음.

**영향**: `docker-compose.yml`, `application.yml`의 datasource 설정, JPA dialect가 MariaDB 기준으로 변경됨.

---

## ADR-002: `User`/`Character` 엔티티를 각각 `app_user`/`story_character` 테이블로 매핑

**날짜**: 커밋 `075e53c`

**배경**: MariaDB(MySQL 계열)로 전환 후 `Character` 엔티티가 기본 테이블명 `character`로 매핑되는데, `CHARACTER`는 MySQL/MariaDB 예약어라 DDL이 실패했다. `USER`도 동일한 문제가 있어 선제적으로 함께 변경.

**결정**: `@Table(name = "story_character")`, `@Table(name = "app_user")`로 명시적 매핑.

**대안 검토**: 예약어를 백틱으로 escape하는 방법도 있으나, Hibernate DDL 자동 생성과 향후 raw SQL(마이그레이션 스크립트 등) 작성 시 매번 escape를 신경 써야 하는 부담이 있어 테이블명 자체를 바꾸는 쪽을 선택.

**영향**: 새 엔티티 추가 시 MariaDB/MySQL 예약어(`GROUP`, `ORDER`, `KEY` 등)와 충돌하는지 항상 확인해야 함 ([`HANDOFF.md`](../HANDOFF.md) 참고).

---

## ADR-003: 로컬 MariaDB 포트를 3307로 매핑

**날짜**: 커밋 `e7b568c`

**배경**: 개발 머신에 Homebrew로 설치된 mysqld가 이미 `127.0.0.1:3306`을 점유하고 있어, docker-compose가 3306을 그대로 바인드하면 충돌.

**결정**: `docker-compose.yml`에서 `3307:3306`으로 호스트 포트만 변경. 컨테이너 내부 포트(3306)와 `application.yml`의 JDBC URL(`localhost:3307`)을 함께 갱신.

**영향**: 3306이 비어있는 다른 개발 환경에서도 3307 그대로 사용 가능(문제 없음). 배포 환경(Docker 네트워크 내부 통신)에서는 호스트 포트 매핑이 무관하므로 영향 없음.

---

## ADR-004: 음성 통화 기능 — B안(RN STT + 서버 TTS) + C안(WebRTC 최소 데모) 조합을 기본 전략으로 채택

**날짜**: PRD v3 3.9절

**배경**: JD 요구사항인 "WebRTC 기반 음성 통화"를 1인 개발 일정(7주) 내에 증명해야 함. 풀 실시간 WebRTC 파이프라인(A안: 마이크 오디오 → WebRTC → 서버 STT → LLM → TTS → WebRTC 재생)은 난이도가 높아 일정 리스크가 크다.

**결정**:
- **메인 기능(B안)**: 클라이언트 RN STT로 로컬 음성 인식 → 텍스트만 서버에 전송 → 서버 LLM → 서버 TTS → 오디오 URL을 클라이언트가 받아 재생. WebRTC 미사용.
- **자격요건 증명용 최소 데모(C안)**: B안과 별개로 WebRTC 시그널링 서버(Offer/Answer/ICE 교환)와 1:1 P2P 오디오 스트리밍 연결만 별도 데모 화면으로 구현. LLM 파이프라인과 통합될 필요 없음.
- A안은 시간 여유가 있을 때만 확장 목표로 시도.

**트레이드오프**: B안은 "엄밀히는 WebRTC가 아니므로" JD의 "WebRTC 연동 경험" 요구사항을 부분적으로만 충족한다. C안을 병행해 코드 수준에서 WebRTC 연동 경험 자체는 증명하되, 메인 기능은 현실적인 완성도를 우선한다.

**영향**: 5주차(B안 파이프라인)와 6주차(C안 WebRTC 데모)가 별도 작업으로 분리되어 있음 ([`TODO.md`](../TODO.md)). WebRTC를 처음부터 메인 파이프라인에 통합하려는 설계 변경은 이 결정과 충돌하므로, 변경하려면 이 ADR을 갱신할 것.

**갱신 1 (5주차 구현 초반)**:
- **STT 패키지를 `@react-native-voice/voice` → `expo-speech-recognition`으로 변경 시도**. PRD/이 ADR 원문이 명시한 패키지였지만, 설치하려던 시점에 npm이 `@react-native-voice/voice@3.2.4`를 "deprecated — use expo-speech-recognition instead"로 표시하고 있어 그대로 채택하지 않음. (→ 갱신 2에서 클라이언트 STT 자체를 서버 사이드로 옮기면서 이 패키지도 결국 제거됨.)
- **음성 통화는 별도 WS 채널을 신설하지 않고 기존 텍스트 채팅 파이프라인(REST)을 재사용**하기로 함 — 이 결정은 갱신 2 이후에도 유지됨. `useConversationStore#sendMessageViaRest`(REST 전용, 응답 완료까지 대기 후 assistantMessage 반환 — WS 스트리밍 경로는 응답 완료 시점을 기다리지 않고 바로 resolve되므로 "오디오를 언제 가져올지" 결정할 수 없어 음성 통화엔 못 씀)를 신설. TTS는 오디오를 별도 저장소에 미리 합성해두지 않고 요청이 올 때 그때그때 합성(`GET /api/messages/{id}/audio`) — 포트폴리오 스코프 트레이드오프(재요청마다 재합성 비용, 프로덕션이면 캐싱 필요).

**갱신 2 (5주차 구현 중 — B안에서 "축소판 A안"으로 범위 확장)**:
사용자가 "이 김에 A안(WebRTC)까지 가면 더 빠르지 않나?"라고 제안, 완전한 양방향 실시간 A안은 서버 쪽 WebRTC 미디어 처리가 JVM에 마땅한 라이브러리가 없어 일정 리스크가 크다고 판단해(리서치 스파이크로 확인) 아래처럼 축소한 중간 지점으로 합의:

- 클라이언트 ↔ **LiveKit**(Cloud, 무료 티어로 충분) 구간은 진짜 WebRTC로 마이크 오디오 전송 — 이력서에 "WebRTC 연동 경험"이라고 쓸 수 있는 실제 근거가 됨.
- 서버는 WebRTC 미디어를 직접 다루지 않고, **LiveKit Track Egress → WebSocket**(raw PCM, `pcm_s16le`)으로 오디오를 받아 갱신 1에서 만든 배치 STT/Gemini/TTS 파이프라인에 그대로 흘려보냄. Egress를 S3 등 클라우드 스토리지로 보내는 대신 **WebSocket 직접 스트리밍 옵션**을 씀 — 별도 스토리지 계정/비용이 아예 필요 없어짐(리서치로 확인).
- 이 결과 **클라이언트 사이드 STT(`expo-speech-recognition`)는 완전히 제거**하고 서버 사이드 배치 STT(Google Cloud Speech-to-Text)로 대체됨 — 오디오가 어차피 서버까지 오므로 클라이언트에서 따로 인식할 필요가 없어짐.
- 서버가 합성한 TTS 응답을 다시 WebRTC로 실시간 되쏘는 것(완전한 A안)은 여전히 범위 밖 — 응답은 갱신 1과 동일하게 오디오 URL로 반환.
- 부수 효과: 6주차 C안(WebRTC 시그널링 최소 데모)이 사실상 상당 부분 선행 충족됨 — 6주차는 검증/재평가 위주로 가벼워질 전망.

**영향(갱신)**: `LiveKitProperties`/`SttProperties` 등 새 자격증명이 필요해짐(LiveKit Cloud 프로젝트, Google Cloud STT 키) — `HANDOFF.md`/`TODO.md`에 다음 세션 준비물로 정리됨. 로컬 개발 시 LiveKit Cloud가 이 백엔드의 `/egress/audio`로 다시 접속해와야 하므로 ngrok 등 터널이 필요하다는 제약도 새로 생김.

**갱신 3 (6주차 — C안 재평가, 별도 데모 없이 종료)**:
6주차 착수 시점에 "5주차 결과로 원래 C안의 목적(WebRTC 연동 경험 증명)이 이미 충족됐는지"를 사용자와 다시 검토함.

- 갱신 2에서 클라이언트↔LiveKit 구간에 실제 WebRTC(마이크 오디오 캡처, ICE, DTLS-SRTP, 트랙 publish)가 이미 들어갔으므로, C안이 원래 존재했던 이유("B안엔 WebRTC가 전혀 없다"는 공백)가 사라짐.
- 남는 차이는 LiveKit SDK가 Offer/Answer/ICE 교환(시그널링)을 내부적으로 처리해준다는 것 — 즉 "시그널링 프로토콜을 직접 구현한 코드"는 없음. 이 차이를 메우려고 `react-native-webrtc` 기반 수동 P2P 시그널링 데모(원래 C안 그대로)를 별도로 새로 만드는 옵션도 검토했으나, **추가 코드 없이 종료**하기로 결정.
- **판단 근거**: (1) 인터뷰/이력서 관점에서 "LiveKit(WebRTC SFU)으로 실 미디어 스트리밍 구현"이라는 근거는 이미 충분하다고 판단, (2) 5주차 자체가 아직 실행 검증이 거의 안 된 상태(LiveKit/STT/TTS 자격증명 전부 미설정, 실기기 미검증)라 남은 개발 시간을 새 데모보다 기존 기능 검증에 쓰는 게 우선순위가 높다고 판단.
- 이 결정은 되돌릴 수 있는 여지를 남겨둠 — "수동 시그널링을 직접 짜본 경험"을 나중에 굳이 더 증명하고 싶어지면 TODO.md 6주차 항목을 다시 열면 됨.

**영향(갱신 3)**: 6주차는 코드 작업 없이 문서 정리(TODO.md 체크 처리, 이 ADR 갱신)로 종료. 7주차(모니터링/배포)에 LiveKit/STT/TTS 관련 배포 시크릿·라우팅 확인 항목이 새로 추가됨(`TODO.md` 7주차 참고) — 5주차 확장이 7주차 스코프에도 영향을 줬음을 반영.

---

## ADR-005: 정식 인증 대신 디바이스 ID 기반 익명 세션

**날짜**: PRD v3 9절 (Out of Scope)

**배경**: JWT/OAuth 등 정식 인증 시스템은 포트폴리오 범위 대비 구현 비용이 크고, 핵심 증명 목표(실시간 통신, LLM/TTS 파이프라인)와 직접 관련이 없다.

**결정**: `X-Device-Id` 헤더 기반 익명 유저 식별로 최소화. 서버는 처음 보는 deviceId를 즉시 `app_user`로 생성한다.

**영향**: 멀티 디바이스 로그인, 계정 복구, 소셜 로그인 등은 범위 밖. 클라이언트가 UUID를 잃어버리면(재설치 등) 이전 대화 히스토리에 다시 접근할 수 없음 — 포트폴리오 범위에서는 허용 가능한 제약으로 판단.

---

## ADR-006: WebClient만 부분 도입(전체 리액티브 스택 전환 안 함), REST 폴백은 `.block()`으로 동기화

**날짜**: PRD v3 2주차, TODO.md "2주차 — Gemini 연동 & WebSocket"

**배경**: Gemini의 `streamGenerateContent`(SSE) 스트리밍 응답을 소비하려면 리액티브 HTTP 클라이언트가 필요하다. Spring이 기본 제공하는 리액티브 클라이언트는 WebClient인데, 표준 도입 경로인 `spring-boot-starter-webflux`는 애플리케이션 전체를 리액티브 스택(Netty 서버, 리액티브 컨트롤러)으로 전환하는 것을 전제로 한다. 그러나 이 프로젝트는 JPA(블로킹 JDBC)와 Spring MVC 기반 REST/STOMP 컨트롤러를 그대로 유지해야 한다.

**결정**: `spring-boot-starter-webflux` 대신 `spring-webflux` + `reactor-netty-http` 라이브러리만 개별 추가해 `WebClient` 빈만 사용한다. 앱은 계속 Servlet(MVC) 스택으로 동작한다. WebSocket(STOMP) 경로에서는 `Flux<String>`을 `Schedulers.boundedElastic()`으로 옮겨 구독(`subscribe()`)해 비동기로 청크를 발행하고, REST 폴백 경로(`POST /api/conversations/{characterId}/messages`)에서는 전체 응답을 모아야 하므로 `.collect(Collectors.joining())` 후 `.block()`으로 동기 반환한다.

**트레이드오프**: MVC 요청 스레드에서 `.block()`을 호출하는 것은 일반적으로 리액티브 안티패턴으로 간주되지만, 여기서는 "REST는 스트리밍이 필요 없는 폴백 경로"라는 제약 덕분에 허용 가능한 단순화로 판단했다. 요청량이 많아지면 서블릿 스레드 풀 고갈 위험이 있으나, 포트폴리오 트래픽 규모에서는 문제 없음.

**영향**: 새로운 리액티브 코드를 추가할 때 앱을 리액티브 스택으로 전환하려는 시도(예: 컨트롤러를 `Mono`/`Flux` 반환으로 바꾸는 것)는 이 결정과 충돌하므로, 전면 전환이 필요해지면 이 ADR을 갱신할 것.
