# Claude Code 지침 — Storia

저장소 전체 개발 규칙은 [`AGENTS.md`](AGENTS.md)를 먼저 읽고 따른다.

관리자 AI Agent 및 Evaluation 작업은 [`docs/roadmap/STORIA_ADMIN_AI_AGENT_ROADMAP.md`](docs/roadmap/STORIA_ADMIN_AI_AGENT_ROADMAP.md)에 정의된 계획을 따른다.

특정 Phase의 구현을 요청받으면 다음 순서로 진행한다.

1. 현재 코드를 확인하고 로드맵의 전제 조건이 실제 구조와 일치하는지 검증한다.
2. 기존 기능을 유지하면서 요청받은 Phase만 구현한다.
3. 관련 테스트를 실행하고 실제 결과와 남은 한계를 보고한다.
4. 작업을 중단한다. 다음 Phase는 별도 요청이 있을 때만 진행한다.

**로드맵은 구현 계획이며, 완료된 작업의 기록이 아니다.**
