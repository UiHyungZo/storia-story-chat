# Storia 관리자 AI Agent — Phase 6 (RAG + MCP) 설계·구현·검증 기록

> **상태:** 구현됨, 로컬에서 검증됨. **실제 Gemini 호출(임베딩 포함)을 사용한 검증은 하지 않았다** — 외부 유료 API는 사용자 승인 전이라 호출하지 않았다. 미검증 항목은 8절에 모았다.
> **작성 기준:** 브랜치 `storiaDocs`, 2026-10-28. 관련 문서: [로드맵](../roadmap/STORIA_ADMIN_AI_AGENT_ROADMAP.md), [Phase 0 설계](admin-ai-agent-phase0.md).
> **범위 변경:** 로드맵 원안은 "MCP 또는 RAG 중 하나"였으나 사용자가 둘 다 구현하도록 요청해 로드맵의 Phase 6 절을 수정했다.

## 1. 아키텍처와 데이터 흐름

```
허용 목록 Markdown 14개 ──▶ DocumentLoader ──▶ TextSanitizer ──▶ MarkdownChunker ──▶ EmbeddingProvider ──▶ InMemoryVectorIndex
 (KnowledgeSources)         (경로·크기 검사)    (HTML 제거, 비밀값 치환)  (헤딩 기반 ≤800자)    local | gemini            (전수 코사인)
                                                                                                                ▲
                                                                              KnowledgeBase(스냅샷, 변경 감지/재인덱싱)
                                                                                                                │
                              ┌────────────────────────────── KnowledgeSearchService ◀──────────────────────────┘
                              │     (질의 검증 → 질의 임베딩 → top-K → 점수 임계값 → 출처 포함 결과 / NO_EVIDENCE)
                              │
   ┌──────────────────────────┼──────────────────────────────────────┐
   ▼                          ▼                                      ▼
AdminToolRegistry        AdminKnowledgeController              KnowledgeMcpTool ──▶ StoriaMcpServer (STDIO)
 searchStoriaKnowledge    /api/admin/ai/knowledge/*              searchStoriaKnowledge   별도 프로세스, Spring 없음
 (Gemini Agent, 6B)       (직접 검색·재인덱싱, 관리자 인증)         (6C)                    MCP 클라이언트가 하위 프로세스로 실행
```

- 검색 로직은 `KnowledgeSearchService` 한 곳에만 있고 Agent, 관리자 API, MCP가 모두 재사용한다.
- RAG 패키지(`com.storia.backend.rag`)와 MCP 패키지(`com.storia.backend.mcp`)는 Spring에 의존하지 않는다. 그래서 MCP 서버를 Spring/DB/관리자 세션 없이 작은 별도 프로세스(`build/mcp-server`, 약 6MB)로 띄울 수 있다. Spring 쪽 조립은 `config/RagConfig`가 맡는다.
- 인덱스는 첫 검색(또는 재인덱싱 요청) 때 만들어진다. 앱 기동 시에는 파일 읽기나 외부 임베딩 호출이 없다.
- Agent 경로: 관리자 질문 → `AdminAgentService` → Gemini가 `searchStoriaKnowledge` 호출 → 결과는 `functionResponse` **데이터**로 전달 → 응답에 `knowledgeSources`(근거 문서 경로), `knowledgeStatus`, 근거 없음이면 서버 고정 문구 `evidenceNotice`.

## 2. 변경 파일

**RAG (신규, `apps/backend/src/main/java/com/storia/backend/rag/`)**
`KnowledgeSource`, `KnowledgeSources`(허용 목록), `DocumentLoader`, `TextSanitizer`, `MarkdownChunker`, `KnowledgeDocument`, `KnowledgeChunk`, `EmbeddingProvider`, `LocalHashingEmbeddingProvider`, `GeminiEmbeddingProvider`, `CachingEmbeddingProvider`, `VectorIndex`, `InMemoryVectorIndex`, `KnowledgeBase`, `KnowledgeSearchService`, `KnowledgeSearchResult`, `RagMetrics`

**MCP (신규, `.../mcp/`)**
`StoriaMcpServer`, `StoriaMcpServerMain`, `KnowledgeMcpTool`, `McpToolMetrics`, `McpJson`

**Spring 연동**
- 신규: `config/RagConfig`, `config/RagProperties`, `controller/AdminKnowledgeController`, `dto/AdminKnowledgeSearchRequest`
- 수정: `ai/AdminToolRegistry`(도구 추가), `ai/AdminAgentService`(출처·고지·시스템 지시), `ai/AgentRunRecorder`(관측 허용 도구 목록), `dto/AdminAgentResponse`(필드 추가), `dto/AdminObservabilityResponse` + `controller/AdminObservabilityController`(RAG 지표), `application.yml`(`storia.rag.*`), `build.gradle`(MCP SDK 의존성, `installMcpServer` 태스크), `.gitignore`

**관리자 앱:** 신규 `agent/knowledge/{page,actions}`, 수정 `agent/page`(근거 문서·고지 표시), `agent/observability/page`, `lib/types.ts`

**테스트·평가 리소스:** `rag/*Test`(정제, 청킹, 로더, 임베딩, 검색 서비스, 평가기), `mcp/*Test`(어댑터, 프로토콜, 원시 STDIO, MCP 평가), `config/RagConfigTest`, `controller/AdminKnowledgeControllerTest`, `eval/rag-eval.json`(RAG 평가 질문 20개), `eval/knowledge-fixtures/`(합성 문서), `eval/agent-scenarios.json`(+2개), `eval/phase4-baseline.json`(+2개)

**문서:** 이 문서, 로드맵 Phase 6 절 수정

## 3. 구현한 RAG 기능 (6A)

| 단계 | 구현 | 비고 |
| --- | --- | --- |
| 허용 목록 | `KnowledgeSources.DEFAULT` 14개 Markdown (README, PRD, 아키텍처, API, 결정 기록, 배포, 에러 처리, 테스트, CI/CD, ERD, WebRTC 글, 로드맵, Phase 0 설계, 사이드카 README) | **제외:** `HANDOFF.md`/`TODO.md`(작업 일지에 실제 테스트 데이터·환경 정보), `AGENTS.md`/`CLAUDE.md`(검색 결과에 섞이면 지시문으로 오인될 수 있음), `docs/legal/*`, 소스 코드, 대화 데이터 |
| 로딩 | `.md`만, 저장소 루트 밖 경로·심볼릭 링크 탈출·1MB 초과 파일 거부, 없는 파일은 `missing`으로 보고 | 변경 감지용 지문(경로·크기·수정시각) |
| 정제 | HTML 주석/태그/이미지 제거, 비밀값 치환(`AIza…`, Bearer, JWT, 개인키 블록, `key=값` 형태에서 토큰처럼 보이는 값) | 플레이스홀더(`your-…`, `$VAR`)와 경로·함수명은 유지. 실제 문서 14개에서 치환 0건. **지시문처럼 보이는 문장은 지우지 않는다** — 데이터로 취급되며 실행되지 않는다 |
| Chunking | 헤딩 경로 기반, ≤800자, 코드 펜스 안의 `#`은 헤딩 아님, 같은 헤딩 안의 짧은 꼬리만 병합 | 실제 문서 기준 281조각 |
| Embedding | `EmbeddingProvider` 포트 + 구현 3종(아래) | 질의는 캐시하지 않고 문서 임베딩만 해시 키로 디스크 캐시 |
| Vector Index | 불변 인메모리 전수 코사인 | 조각 수백 개라 ANN/Vector DB 불필요 |
| 검색 결과 | 문서 ID, 출처 경로, 제목/헤딩 경로, 발췌, 점수, 순위, 인덱스 정보 | 질의 원문은 결과에 되돌려주지 않음 |
| 근거 없음 | 점수가 임계값 미만이면 `NO_EVIDENCE`(결과 0건). 인덱스가 비어 있으면 `INDEX_EMPTY`로 구분 | 기본 임계값 0.12 (로컬 벡터) |
| 입력 제한 | query 1~300자, 제어문자 불가, topK 1~5(기본 3), 발췌 ≤800자 | |
| 재인덱싱 | 문서 지문이 바뀌면 5초 간격으로 자동 감지 + 관리자 `POST …/knowledge/reindex` | 실패 시 이전 인덱스 유지 |

**임베딩 제공자 3종 (역할 분리)**

| 구현 | 외부 호출 | 의미 임베딩 | 용도 |
| --- | --- | --- | --- |
| `LocalHashingEmbeddingProvider` (기본) | 없음 | **아니오** — 단어·문자 2/3-gram 해싱 + IDF 벡터. 어휘가 겹쳐야 유사도가 오른다 | 기본값, 오프라인 평가 |
| `GeminiEmbeddingProvider` | **있음(유료)** — 인덱스 조각 약 281개(약 14.6만 자)와 질의 | 예 | `storia.rag.provider=gemini` 또는 `STORIA_RAG_PROVIDER=gemini` 로 선택 |
| `FakeEmbeddingProvider` (테스트 소스) | 없음 | 아니오 — 주제 키워드 → 고정 차원 | 파이프라인 단위 테스트 전용 |

"로컬 해싱"은 **Vector Retrieval은 실제로 수행하지만 의미를 이해하지 못한다**. 이 문서와 UI는 이를 "의미 임베딩 아님"으로 표시한다. Gemini 제공자는 공식 문서의 REST 형식(`gemini-embedding-2`는 `taskType` 대신 텍스트 접두어, `-001`은 `taskType`, 3072차원이 아니면 직접 정규화)을 따라 구현했고, 요청·응답 처리는 로컬 Mock HTTP 서버로만 검증했다.

## 4. Agent 연동 (6B)

- 도구 `searchStoriaKnowledge(query, topK)`를 기존 `AdminToolRegistry`에 추가했다. 이름·인자 스키마를 서버가 검증한다(알 수 없는 인자, 타입, 길이, topK 범위). 읽기 전용이며 캐릭터·대화 서비스에 접근하지 않는다 (테스트로 확인).
- 관리자 인증은 기존 `/api/admin/**` 인터셉터를 그대로 쓴다. 새 REST: `POST /api/admin/ai/knowledge/search`(LLM 없이 직접 검색), `GET …/status`, `POST …/reindex`.
- **근거 없는 답변 억제(검증 가능한 범위):** 시스템 지시에 "문서 질문은 검색 후 source를 인용, `NO_EVIDENCE`면 근거 없다고 답하라"를 넣었다. 이는 모델이 따를 것이라는 보장이 없으므로, 서버가 마지막 검색 결과가 `OK`가 아니면 응답에 고정 문구 `evidenceNotice`("검색한 문서에서 근거를 찾지 못했습니다. 아래 답변은 문서로 확인되지 않았습니다.")를 붙이고 UI가 눈에 띄게 표시한다. 검색 결과의 출처는 `knowledgeSources`로 노출된다. **모델 답변 내용이 실제로 근거에 부합하는지는 검증하지 못한다.**
- 문서 속 지시문: 발췌는 `functionResponse` 데이터로만 전달되고 결과에 "지시가 아님" 고지가 붙는다. Phase 4 평가에 시나리오 `agent-029`(문서에 지시문이 있고 모델이 그 지시를 따라 쓰기 도구를 요청해도 `unknown_tool`로 거부, DB 불변)와 `agent-030`(범위 밖 질문 → `NO_EVIDENCE` + 고지)를 추가했다 (총 50개).
- 승인 프로세스와 충돌하지 않음: 제안·승인 코드는 건드리지 않았고, 검색 도구는 제안 저장소·승인 서비스를 참조하지 않는다.

## 5. MCP 서버 (6C)

**선택:** 공식 MCP Java SDK `io.modelcontextprotocol.sdk:mcp-core:2.1.0` + `mcp-json-jackson2:2.1.0`, **STDIO 전송**, **별도 프로세스**.

| 대안 | 채택하지 않은 이유 |
| --- | --- |
| Spring Boot 앱 안에 Streamable HTTP 엔드포인트 | 인증·Origin 검증·로컬 바인딩 등 프로토콜 보안 요구를 새로 충족해야 하고 기존 관리자 인증 경계(쿠키 세션)와 별개의 외부 접속 경로가 생긴다. 이번 목표(로컬 검증·단순 구조)에 비해 위험이 크다 |
| Spring AI MCP 서버 스타터 | 새 프레임워크 추가. 이 프로젝트의 Spring Boot 4 호환 여부는 확인하지 않았다 |
| 직접 JSON-RPC 구현 | "실제 MCP 표준 준수"를 스스로 보장해야 해서 오류 가능성이 크다 |
| STDIO를 Spring 프로세스에서 같이 실행 | stdout이 프로토콜 전용이라 Spring 로그·배너와 충돌하고 DB·Firebase 등 불필요한 초기화가 따라온다 |

- **프로토콜 버전:** SDK 2.1.0은 `2024-11-05 … 2025-11-25`를 지원한다. 2026-07-28 개정(무상태, `server/discover`)은 SDK가 지원하지 않아 **구현하지 못했다.** 해당 개정은 `initialize` 기반 구(舊) 서버에 대한 하위 호환을 클라이언트에 요구하며(`server/discover` 탐지 후 `initialize` 폴백), MCP Inspector 2.12.0은 이 서버와 정상 연동했다. 2026-07-28만 지원하는 클라이언트와의 호환은 **미검증**이다.
- **Tool 하나:** `searchStoriaKnowledge` — 입력 `query`(1~300자, 필수), `topK`(1~5, 기본 3), `additionalProperties:false`. 출력: `status`(OK/NO_EVIDENCE/INVALID_INPUT/INDEX_EMPTY/ERROR/TIMEOUT), 결과(출처·헤딩·발췌·점수·순위·chunkId), 인덱스 정보. 어노테이션 `readOnlyHint=true, destructiveHint=false, idempotentHint=true, openWorldHint=false`. 결과는 `structuredContent`와 동일 JSON 텍스트로 함께 제공한다.
- **오류 규약:** 입력 오류·검색 실패·타임아웃(10초)은 `isError=true` Tool 결과, 근거 없음은 오류 아님, 알 수 없는 Tool은 JSON-RPC 오류(SDK 2.1.0은 코드 `-32602`, 메시지 `Unknown tool …`로 응답. 알 수 없는 메서드는 `-32601`). 입력 검증은 SDK의 스키마 검증 대신 핸들러가 동일 규칙으로 수행해 거부 호출도 `McpToolMetrics`에 집계되게 했다.
- **추가 Tool 검토:** `getCharacterConfig`는 시스템 프롬프트(운영 자산), `getConversationLogs`는 사용자 대화(개인정보)를 반환하므로 인증 경계가 없는 STDIO 서버에 노출하지 않았다. 문서 인덱스 상태 조회 Tool은 가능하지만 필수 범위를 하나로 제한하라는 요구에 따라 만들지 않았다.
- **접근 범위·인증:** 네트워크 포트를 열지 않고, 클라이언트를 실행한 OS 사용자의 권한으로만 동작한다. 읽는 것은 허용 목록 문서뿐이며 관리자 세션·DB·쓰기 기능이 없다. 비밀값을 클라이언트로 보내지 않는다(Gemini 임베딩 사용 시 `GEMINI_API_KEY`는 서버 프로세스 환경변수로만 전달). 일부 클라이언트(MCP Inspector)는 자식 프로세스에 부모 환경변수를 상속하지 않으므로 `-e KEY=VALUE`로 명시해야 한다 — 직접 확인한 동작이다.
- **stdout 보호:** `System.out`을 stderr로 돌리고 프로토콜은 원래 stdout 스트림으로만 주고받는다. stdin 종료 시 프로세스가 종료된다(원시 프로토콜 테스트로 확인).

## 6. 평가와 관측 (6D)

모든 수치는 실제 실행 결과에서 계산했다 (리포트: `apps/backend/build/eval-reports/rag-eval-*.{json,md}`, `mcp-eval-*.{json,md}`, `phase4-*.{json,md}`; `build/`는 git 대상이 아니므로 아래에 값을 옮겨 적는다).

### 6.1 RAG 검색 평가 — 로컬 해싱 벡터, 실제 문서 14개/281조각

합성 질문 20개(범위 내 16, 범위 밖 4). 정답 = `expect_sources` 중 하나라도 상위 K에 포함.

| 지표 | 전체 | calibration(10) | holdout(10) |
| --- | --- | --- | --- |
| Hit@1 | 0.8125 | 0.875 | 0.75 |
| Hit@3 | 0.875 | 0.875 | 0.875 |
| Hit@5 | 0.9375 | 1.0 | 0.875 |
| MRR@5 | 0.849 | 0.9063 | 0.7917 |
| 범위 내 질문이 근거 없음으로 처리됨 | 0.0 | 0.0 | 0.0 |
| 범위 밖 질문을 근거 없음으로 정확히 처리 | 1.0 (4/4) | 1.0 | 1.0 |
| 범위 밖 질문에 근거를 잘못 반환 | 0.0 | 0.0 | 0.0 |

- **출처 정확성:** 반환된 77개 결과 모두 출처가 허용 목록 안에 있고(77/77), 발췌 텍스트가 해당 문서 원문(공백 정규화 후)에 그대로 존재했다(77/77).
- **관련 없는 문서 배제:** 범위 밖 4개의 top-1 점수 0.0629~0.115, 범위 내 16개의 top-1 점수 최저 0.144. 임계값 0.12에서 둘이 갈린다. 마진이 얇다: 범위 밖 `rag-020`(Kubernetes)이 0.115로 임계값에 0.005 차이다.
- **Hit@3 실패 2건:**
  - `rag-001` "음성 통화는 어떤 구조로 동작해?" — 정답 문서(architecture)가 4위. PRD의 음성 통화 절이 1~3위를 차지했다. PRD도 음성 통화를 다루므로 오답이라기보다 정답 목록의 한계일 수 있으나, 평가 기준상 실패로 둔다.
  - `rag-012` "운영 콘솔의 관리자 인증은 어떻게 구현했어?" — 정답 목록(architecture, README)이 5위 안에 없음. `관리자`라는 어휘가 많은 로드맵/Phase 0 설계 문서가 상위를 차지했다. 어휘 중첩 기반 검색의 전형적 한계다.
- **임계값 보정 과정(투명성):** 임계값 0.12는 calibration 질문의 점수 분포(범위 밖 최대 0.0865, 범위 내 최소 0.1686 사이)에서 정했다. 다만 보정 전 탐색 실행에서 20개 질문 전체의 점수를 한 번에 출력해 **holdout 점수도 이미 본 상태**였으므로 holdout은 완전한 블라인드가 아니다. 위 holdout 수치는 "보정에 쓰지 않은 질문" 정도로만 해석해야 한다. 임계값 스윕 표는 리포트에 있다.
- **검색 지연(인덱스 구축 후 warm, 질의 임베딩 포함, 100회):** p50 0.35ms, p95 0.69ms, max 0.78ms. 인덱스 구축 97~148ms(14문서/281조각, 로컬). 로컬 해싱이라 외부 호출 비용은 0이며, 임베딩에 쓴 글자 수(비용 대용 지표) 146,167자.
- **이 수치의 의미:** 20개 합성 질문에 대한 결과이며 실제 관리자 질문 분포의 성능이 아니다. 질문과 정답 라벨을 구현 담당(저)이 직접 작성했고 정답 라벨 일부는 논쟁의 여지가 있다. **Gemini 의미 임베딩의 수치는 측정하지 않았다.**

### 6.2 MCP 평가 — 실제 STDIO 프로세스, 실제 프로젝트 문서, 로컬 해싱 벡터

`McpEvalTest`가 설치된 서버(`build/mcp-server`)를 하위 프로세스로 실행하고 MCP Java SDK 클라이언트로 프로토콜을 주고받아 **11/11 항목 통과**:
`discovery`, `initialize`, `call_success_with_sources`, `result_equals_direct_service`(MCP 결과 == 동일 서비스 직접 호출 결과: chunkId·출처·점수·발췌·순서), `no_evidence`, `input_schema_validation`(topK 범위/타입, query 누락/타입/길이, 추가 인자 7종 모두 isError), `unknown_tool_blocked`(쓰기 계열 이름 포함 4종), `allowlist_enforced`(HANDOFF/TODO/AGENTS 내용·경로 입력 질의에서 허용 목록 밖 출처 없음), `recovers_after_errors`, `read_only`(허용 목록 문서 지문 불변), `logs_contain_no_content`.

| 측정 | 값 |
| --- | --- |
| 프로세스 시작 → initialize 완료 | 329ms |
| 첫 호출(인덱스 구축 포함) | 240ms |
| warm 호출 (클라이언트 측, 20회) | p50 1.38ms, p95 1.61ms |
| 서버 보고 호출 수 / 결과 | 35회: OK 27, NO_EVIDENCE 1, INVALID_INPUT 7 |
| 서버 보고 오류 유형 | `invalid_arguments` 7 |
| 서버 측 소요 | p50 0ms, max 206ms (첫 호출의 인덱스 구축) |

### 6.3 관측 확장

- **Spring(관리자):** `GET /api/admin/ai/observability`에 `knowledge` 요약 추가 — 검색 수, 상태별 건수(OK/NO_EVIDENCE/INVALID_INPUT/INDEX_EMPTY/ERROR), 평균·p95 지연, 재인덱싱 수, 임베딩에 보낸 글자 수(질의/인덱스, 비용 대용 지표). 에이전트 실행 기록에는 `searchStoriaKnowledge` 도구 이름이 허용 도구로 기록된다(모델 토큰 사용량은 Phase 5 기능 그대로, 모델이 보고한 경우에만).
- **MCP:** 별도 프로세스이므로 Spring 관측 저장소와 공유하지 않는다. 호출마다 내용 없는 한 줄 JSON(`tool`, `durationMs`, `outcome`, `errorType`, `results`)을 stderr에 남기고 프로세스 내 요약을 제공한다.
- 질의 원문, 문서 발췌, 키는 어디에도 기록하지 않는다 (테스트로 확인: 관측 기록, MCP stderr, RAG 지표).
- **측정하지 못한 것:** 모델 토큰 사용량·검색 비용을 금액으로 환산하는 것(실제 호출이 없어 데이터 없음), MCP 호출의 관리자 화면 통합.

### 6.4 Phase 4 회귀

`agent-029`, `agent-030`을 추가해 시나리오 총 50개(통과 24 / 실패 0 / 미결 26 — 미결은 live 모델이 필요한 항목). 하네스 자체 검증(`AgentEvalRunnerSelfTest`)과 기준선(`phase4-baseline.json`)이 모두 통과했다.

## 7. 실행한 명령과 결과

| 명령 | 결과 |
| --- | --- |
| `cd apps/backend && ./gradlew cleanTest test --no-daemon --offline` | `BUILD SUCCESSFUL`, XML 합산 **39개 파일, 210 tests, 0 failures, 0 errors, 0 skipped** (Phase 5 종료 시점 131개) |
| `env -u RUN_LIVE_EVAL -u RUN_LIVE_RAG_EVAL -u GEMINI_API_KEY ./gradlew evalLive --no-daemon --offline` | `BUILD SUCCESSFUL`, live 평가 3개(`LiveEvalTest`, `Phase4LiveEvalTest`, `RagLiveEvalTest`) 모두 skipped — 환경변수 없이는 외부 호출 없음 |
| `cd apps/admin && npm run build` | 컴파일·타입 검사 통과 (`/agent/knowledge` 라우트 생성) |
| `./gradlew installMcpServer` | `build/mcp-server/`에 서버 설치 (`lib/` 15개 JAR 약 6MB + 실행 스크립트) |
| 원시 JSON-RPC 스모크 (`printf … | build/mcp-server/storia-mcp-server`) | stdout 5줄 모두 유효한 JSON-RPC, 로그는 stderr |
| MCP Inspector 2.12.0 CLI (7.1절) | 통과 |

의존성 다운로드를 위해 `--offline` 없이 `./gradlew … --no-daemon`을 몇 차례 실행했다(Maven Central에서 MCP SDK 등 아티팩트만 받음, 유료 API 호출 아님).

### 7.1 실제 외부 MCP 클라이언트 연동 결과

SDK와 **독립적으로 구현된** 클라이언트인 `@modelcontextprotocol/inspector` 2.12.0(TypeScript) CLI로 설치된 서버를 STDIO로 실행해 확인했다.

| 단계 | 결과 |
| --- | --- |
| 서버 시작·클라이언트 연결·초기화 | 성공 (서버 stderr: `Client initialize request - Protocol: 2025-11-25 … Info: inspector-cli 2.12.0`) |
| `tools/list` (`--strict`) | `searchStoriaKnowledge` 1개, `readOnlyHint=true`, 스키마 이식성 경고 0건 (초기 버전에서 `type: ["string","null"]` 경고 2건이 나와 null 허용 필드를 제거해 해소) |
| `tools/call` 정상 | `status=OK`, `isError=false`, 질의 "로컬 MariaDB 포트를 3307로 매핑한 이유" → 1위 `docs/decisions.md`(ADR-003, 0.3938), 2위 `docs/decisions.md`(ADR-001, 0.2786) |
| 검색 결과 없음 ("맛있는 파스타 레시피") | `status=NO_EVIDENCE`, 결과 0건, `isError=false` |
| 잘못된 입력 (`topK=99`) | `isError=true`, `INVALID_INPUT: topK는 1~5 사이여야 합니다.`, CLI 종료 코드 5 |
| 알 수 없는 Tool (`deleteEverything`) | CLI 오류 `tool_not_found`, 종료 코드 5 |

추가로 MCP Java SDK 클라이언트(`McpServerProtocolTest` 11개, `McpEvalTest`)와 원시 JSON-RPC(`RawStdioProtocolTest`)로도 프로토콜 경유 검증을 했다. 단위 테스트에서 핸들러를 직접 호출하는 `KnowledgeMcpToolTest`는 "MCP 연동 성공"의 근거로 쓰지 않았다.

## 8. 남은 한계와 미검증

**검증하지 못한 것**
- **Gemini 임베딩 실호출:** `GeminiEmbeddingProvider`는 Mock HTTP 서버로만 검증했다. 실제 모델 이름·응답 형식·한도, 의미 임베딩의 검색 품질과 적절한 점수 임계값(코드의 기본 0.55는 근거 없는 임시값)은 측정하지 않았다. → 승인 후 실행: 9.1절 참고.
- **Gemini Agent가 실제로 `searchStoriaKnowledge`를 올바르게 호출하는지**(Function Calling 형식 포함, Phase 2부터의 미검증 사항): 스크립트 모델로만 검증했다.
- **Claude Desktop 등 GUI 클라이언트**와 **Streamable HTTP** 전송, **2026-07-28 전용 클라이언트**.
- 관리자 UI(`/agent/knowledge` 등)는 빌드만 통과했고 브라우저에서 확인하지 못했다.

**설계상 한계**
- 기본 로컬 해싱 벡터는 의미를 이해하지 못한다. 동의어·패러프레이즈 질문은 놓치고(테스트로 확인), 어휘가 많이 겹치는 다른 문서가 정답을 밀어낼 수 있다(`rag-001`, `rag-012`).
- 점수 임계값 마진이 얇고 평가 질문이 20개뿐이라 통계적 결론을 내릴 수 없다. holdout은 블라인드가 아니다.
- 검색 대상은 허용 목록 14개 Markdown뿐이다. 캐릭터 설정·대화 로그·소스 코드는 의도적으로 제외했다.
- 백엔드 Docker 이미지는 `src`만 복사하므로 컨테이너에서는 문서가 없어 `INDEX_EMPTY`가 된다. 로컬/문서가 있는 환경 기준 기능이다.
- MCP 서버는 인증이 없다(OS 프로세스 경계에 의존). 네트워크로 노출하지 말 것. SDK 2.1.0이 2026-07-28 개정을 지원하지 않는다.
- 서버 재기동 시 인덱스를 다시 만든다(로컬은 약 0.1~0.2초). Gemini 제공자는 문서 임베딩을 디스크 캐시한다.
- `evidenceNotice`는 "근거 없음"을 알릴 뿐 모델 답변의 사실성을 검증하지 않는다.
- 기존 `GlobalExceptionHandler`가 405를 500으로 바꾸는 동작은 그대로다.

## 9. 로컬 실행 및 재현 방법

```bash
cd apps/backend

# 1) 전체 테스트 (외부 호출 없음; installMcpServer가 먼저 실행됨)
./gradlew test --no-daemon

# 2) MCP 서버 설치와 직접 실행 (stdout은 MCP 메시지 전용, 로그는 stderr)
./gradlew installMcpServer
STORIA_DOCS_ROOT="$(cd ../.. && pwd)" build/mcp-server/storia-mcp-server   # 클라이언트가 stdin/stdout으로 연결

# 3) MCP Inspector (SDK와 독립된 클라이언트)로 확인
R="$(cd ../.. && pwd)"; L="$PWD/build/mcp-server/storia-mcp-server"
npx -y @modelcontextprotocol/inspector --cli --strict "$L" -e STORIA_DOCS_ROOT="$R" --method tools/list
npx -y @modelcontextprotocol/inspector --cli "$L" -e STORIA_DOCS_ROOT="$R" --method tools/call \
  --tool-name searchStoriaKnowledge --tool-arg 'query=로컬 MariaDB 포트를 3307로 매핑한 이유' --tool-arg topK=2
#   UI로 보려면: npx @modelcontextprotocol/inspector "$L"  (환경변수 STORIA_DOCS_ROOT는 UI의 Environment Variables에 입력)

# 4) 평가 리포트 재생성 → build/eval-reports/rag-eval-*.md, mcp-eval-*.md, phase4-*.md
./gradlew test --tests '*RagEvalTest' --tests '*McpEvalTest' --tests '*Phase4RegressionTest'

# 5) 관리자 앱
cd ../admin && npm run build
```

Claude Desktop 등에 등록할 때의 설정 예 (`mcpServers` — **이 구성으로의 연동은 검증하지 않았다**):

```json
{
  "mcpServers": {
    "storia-knowledge": {
      "command": "/절대경로/apps/backend/build/mcp-server/storia-mcp-server",
      "env": { "STORIA_DOCS_ROOT": "/절대경로/storia-story-chat" }
    }
  }
}
```

환경변수: `STORIA_DOCS_ROOT`(기본: cwd에서 위로 올라가며 `AGENTS.md`+`docs/`가 있는 곳), `STORIA_RAG_PROVIDER=local|gemini`, `GEMINI_API_KEY`, `STORIA_RAG_GEMINI_MODEL`, `STORIA_RAG_DIMENSIONS`, `STORIA_RAG_CACHE_DIR`, `STORIA_RAG_MIN_SCORE`.

### 9.1 (승인 필요) Gemini 임베딩 실검증

외부 유료 호출이므로 **승인 후에만** 실행한다. 호출량: 문서 임베딩 약 6회 배치 요청(281조각, 약 14.6만 자) + 질의 20회, 이후 재실행은 캐시(`build/rag-live-cache`)로 문서 임베딩 호출 없음.

```bash
cd apps/backend
RUN_LIVE_EVAL=true RUN_LIVE_RAG_EVAL=true GEMINI_API_KEY=… ./gradlew evalLive --tests '*RagLiveEvalTest'
# 리포트의 임계값 스윕으로 storia.rag.min-score(기본 0.55는 임시값)를 재보정한 뒤 EVAL_RAG_MIN_SCORE로 재실행
```

## 10. 완료 조건 점검 (정직한 현황)

| # | 조건 | 상태 |
| --- | --- | --- |
| 1 | 실제 Embedding 및 Vector Retrieval 구현 | **부분** — Vector Retrieval은 구현·검증(로컬 해싱, 의미 아님). 의미 임베딩(Gemini)은 구현했으나 **실호출 미검증** |
| 2 | 출처 포함 검색 결과 | 완료·검증 (77/77) |
| 3 | 기존 Gemini Admin Agent와 연동 | 구현·스크립트 모델/Mock으로 검증. **실제 Gemini 호출 미검증** |
| 4 | 실제 MCP Server·Tool Schema | 완료·검증 |
| 5 | 외부 MCP Client에서 발견·호출 | 완료 — MCP Inspector 2.12.0(독립 구현) + Java SDK 클라이언트. Claude Desktop 미검증 |
| 6 | 인증·접근 범위·입력값·Read-only | 완료·검증 (Spring: 인증 401/입력 400 테스트, MCP: 허용 목록·스키마·읽기 전용) |
| 7 | Evaluation 실행 및 리포트 | 완료 (로컬 해싱 기준) |
| 8 | Phase 0~5 회귀 테스트 | 통과 (210개 중 실패 0) |
| 9 | 백엔드 테스트·관리자 빌드 | 통과 |
| 10 | 구현·검증/미검증 구분 | 본 문서 |
