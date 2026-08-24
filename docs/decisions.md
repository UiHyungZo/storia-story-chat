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
- **메인 기능(B안)**: 클라이언트 RN STT(`@react-native-voice/voice`)로 로컬 음성 인식 → 텍스트만 WebSocket 전송 → 서버 LLM → 서버 TTS → 오디오 URL을 클라이언트가 받아 재생. WebRTC 미사용.
- **자격요건 증명용 최소 데모(C안)**: B안과 별개로 WebRTC 시그널링 서버(Offer/Answer/ICE 교환)와 1:1 P2P 오디오 스트리밍 연결만 별도 데모 화면으로 구현. LLM 파이프라인과 통합될 필요 없음.
- A안은 시간 여유가 있을 때만 확장 목표로 시도.

**트레이드오프**: B안은 "엄밀히는 WebRTC가 아니므로" JD의 "WebRTC 연동 경험" 요구사항을 부분적으로만 충족한다. C안을 병행해 코드 수준에서 WebRTC 연동 경험 자체는 증명하되, 메인 기능은 현실적인 완성도를 우선한다.

**영향**: 5주차(B안 파이프라인)와 6주차(C안 WebRTC 데모)가 별도 작업으로 분리되어 있음 ([`TODO.md`](../TODO.md)). WebRTC를 처음부터 메인 파이프라인에 통합하려는 설계 변경은 이 결정과 충돌하므로, 변경하려면 이 ADR을 갱신할 것.

---

## ADR-005: 정식 인증 대신 디바이스 ID 기반 익명 세션

**날짜**: PRD v3 9절 (Out of Scope)

**배경**: JWT/OAuth 등 정식 인증 시스템은 포트폴리오 범위 대비 구현 비용이 크고, 핵심 증명 목표(실시간 통신, LLM/TTS 파이프라인)와 직접 관련이 없다.

**결정**: `X-Device-Id` 헤더 기반 익명 유저 식별로 최소화. 서버는 처음 보는 deviceId를 즉시 `app_user`로 생성한다.

**영향**: 멀티 디바이스 로그인, 계정 복구, 소셜 로그인 등은 범위 밖. 클라이언트가 UUID를 잃어버리면(재설치 등) 이전 대화 히스토리에 다시 접근할 수 없음 — 포트폴리오 범위에서는 허용 가능한 제약으로 판단.
