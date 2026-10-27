# Storia — AX Engineer / AI Agent 고도화 Phase 로드맵

> **상태: 개발 계획(아직 구현되지 않음)** · 작성 기준: 2026-10-27  
> **필수 목표: Phase 0~4** · Phase 5~6은 채용공고상 필요할 때만 선택  
> 목적: Storia의 기존 구조를 재사용하여 **도구 호출·관리자 승인·LLM/Agent Evaluation**을 검증 가능한 경험으로 만든다. **서류 합격이나 상용 AI 경력 인정은 보장하지 않는다.**

## 범위 원칙

1. 새 앱, 새 백엔드, 새 음성 Agent를 만들지 않는다. 기존 **Spring Boot + Gemini + MariaDB + Next.js Admin**을 활용한다.
2. 관리자용 업무 Agent와 일반 사용자 **캐릭터 채팅/React Native/LiveKit 음성 통화는 분리**한다. Phase 0~4는 모바일 앱 빌드 및 실기기 테스트를 기본적으로 요구하지 않는다.
3. 코드가 실제로 존재하는지 확인한 뒤 구현한다. 이 문서의 Tool 함수명과 새 파일 이름은 **제안 명세**이지 현재 구현을 뜻하지 않는다.
4. 매 Phase는 별도 커밋/PR, 실행한 테스트와 남은 실패·제약을 기록하고 **종료 후 멈춘다**. 요청 없이 후속 Phase로 넘어가지 않는다.
5. 평가 데이터는 **합성 대화**만 사용한다. 실사용자 로그를 평가 파일·모델 추적 서비스·Git에 무단 포함하지 않는다.
6. Gemini API 키/토큰/실사용자 데이터와 무관한 **격리된 테스트 환경**을 사용한다. 명시적 비용 동의 없이 CI에서 외부 유료 API를 호출하는 테스트를 추가하지 않는다.
7. 구현 완료 후에도 개인 프로젝트 구현과 기업의 상용 AI 운영 경력을 구분한다.

## 저장소 현황 — 실제 경로 확인됨

| 기존 코드 | 현재 기능 | 이번 작업에서의 활용 |
| --- | --- | --- |
| `apps/backend/src/main/java/com/storia/backend/service/GeminiService.java` | `streamReply(String, List<Message>)`로 Gemini 스트리밍 텍스트 수신 | Phase 1 실제 응답 생성 경로 재사용 |
| `apps/backend/src/main/java/com/storia/backend/controller/ConversationController.java` | REST 사용자 메시지 저장/LLM 호출/응답 저장 | **평가 러너에서 직접 호출 금지**(DB 쓰기·푸시 가능) |
| `apps/backend/src/main/java/com/storia/backend/controller/ConversationStompController.java` | 일반 캐릭터 대화 스트리밍 | 수정 최소화; Admin Tool 경로와 분리 |
| `apps/backend/src/main/java/com/storia/backend/service/ConversationService.java` | 메시지/대화 생성, 저장; assistant 저장 시 푸시 | 격리 없는 평가에서 호출 금지 |
| `apps/backend/src/main/java/com/storia/backend/controller/AdminCharacterController.java` | `GET /api/admin/characters/{id}`, `PUT /api/admin/characters/{id}` | 설정 조회·승인된 변경 재사용 |
| `apps/backend/src/main/java/com/storia/backend/controller/AdminConversationController.java` | `GET /api/admin/conversations`, `GET /api/admin/conversations/{id}/messages` | 로그 조회·페이지 제한 재사용 |
| `apps/backend/src/main/java/com/storia/backend/config/AdminWebConfig.java` | `/api/admin/**` 인증 인터셉터 등록 | Admin Tool도 같은 인증 경계 적용 |
| `apps/admin/src/lib/backend.ts` | `server-only` + 관리자 세션 쿠키로 Spring 호출 | Admin Agent UI/Server Action 재사용 |
| `apps/admin/src/app/(admin)/characters/[id]/actions.ts` | 관리자 설정 변경 Server Action | Phase 3 승인 후 변경 패턴 참고 |
| `apps/admin/src/app/(admin)/conversations/page.tsx` | 필터/페이지네이션 조회 UI | Phase 2 최소 UI 참고 |
| `apps/backend/src/test/resources/application.yml` | H2 `MODE=MySQL` 테스트 datasource | DB 관련 테스트 격리 재사용 |
| `apps/python-sidecar/agent.py` | LiveKit 음성 Agent | Phase 0~4 변경 대상 아님 |

**중요한 구현 차이:** 현재 `GeminiService.streamReply`는 SSE 데이터에서 **텍스트 응답만 추출**한다. Function Calling 응답 구조는 현재 파서가 처리하지 않는다. Phase 2에서는 **관리자 전용 Gemini Tool Calling 경로를 분리하거나, 안전하게 구조화 응답을 처리하는 방식**을 검토하되 일반 채팅 스트리밍을 깨지 않는다.

## Phase 요약

| Phase | 상태 | 핵심 산출물 | 시간 예산(추정) |
| --- | --- | --- | ---: |
| **0 — 코드/격리 설계** | 미착수 | 실제 변경 지점, 테스트 격리 설계 | 0.5~1시간 |
| **1 — LLM Eval MVP** | 미착수 | 20개 평가셋, v1/v2 비교·실패 개선 | 3~5시간 |
| **2 — Read-only Tool Calling** | 미착수 | 캐릭터·로그 조회 도구 2개 | 3~5시간 |
| **3 — 승인형 변경 제안** | 미착수 | 변경 제안, diff, 승인/거절, 권한 검사 | 2~3시간 |
| **4 — 통합 Eval + 리포트** | 미착수 | 30~50개 케이스, 자동 회귀 리포트 | 3~5시간 |
| 5 — Langfuse/관측성 | 선택/보류 | 최소 Trace, 토큰·지연·실패 관측 | 별도 판단 |
| 6 — MCP **또는** RAG | 선택/보류 | 공고 관련 확장 1개 | 별도 판단 |

필수 구간 총 작업 예산은 **약 12~19시간**이며 실제 코드 수정 범위에 따라 달라질 수 있다. Phase 4보다 앞서 고비용 인프라를 구축하지 않는다.

---

## Phase 0 — 현행 구조 확인, 설계, 안전한 평가 실행 경로

**목표:** 확인된 구현 흐름을 연결하고 코드 변경 범위를 최소화한다.

- [ ] `GeminiService.streamReply`의 입력, SSE 응답 처리, 모델/생성 설정을 확인한다.
- [ ] REST·STOMP 메시지 경로의 **DB write/FCM side effect**를 목록화한다.
- [ ] Admin 인증(`AdminAuthInterceptor`, `AdminWebConfig`) 및 Next.js 서버 호출 방식을 확인한다.
- [ ] Admin 로그 조회(`AdminConversationService`)에 페이지 수·메시지 수 제한이 필요한 부분을 정한다.
- [ ] Phase 1 평가 실행 방식을 선택한다: **쓰기 없는 응답 생성 서비스/어댑터**를 우선 검토; 불가능하면 H2/격리 DB 또는 전용 테스트 계정을 사용하고 푸시를 차단한다.
- [ ] 유료 Gemini 호출 여부, 비용 상한, 모델 설정, 결과 파일 보관 범위를 명시한다.
- [ ] 기능 구현 없이 실제 변경 대상과 테스트 전략만 기록한다.

**완료 조건:** 실제 파일·API/추가할 코드 위치, 격리·권한·비밀 정보 보호 방안이 짧은 기록으로 남아야 한다.

## Phase 1 — LLM Evaluation 기초 (합성 사례 20개)

**목표:** 기존 Storia 캐릭터 답변의 실패를 측정하고 동일 조건에서 프롬프트 변경 전후를 비교한다.

- [ ] `evals/dataset.json`에 최소 20개 합성 사례를 만든다. 필드 후보: `id`, `character_prompt`, `history`, `user_input`, `expected_behavior`, `rubric`, `tags`.
- [ ] 최소 범주: **허위 기억 4, 페르소나 유지 4, 다중 턴 맥락 4, 불확실성·정정 4, 예외·경계 조건 4** (최종 개수와 분포는 기록).
- [ ] **실제 Gemini 생성 로직과 가능한 한 같은 프롬프트/메시지 변환**을 사용한다. DB 쓰기·FCM이 발생하는 `POST /api/conversations/{characterId}/messages`를 기본 평가 API로 쓰지 않는다.
- [ ] `evals/` 실행기·설명서를 만든다. 러너 언어는 현재 환경에 맞게 선택(별도 Python 서버 신설 금지).
- [ ] 모델/파라미터, 프롬프트 v1/v2, 맥락, 평가 기준, 실행 날짜를 기록한다. LLM 비결정성을 명시한다.
- [ ] 객관적 assertion과 주관적 행동 평가를 구분한다. 수동 루브릭이 필요한 항목을 성공으로 자동 계산하지 않는다.
- [ ] **실제로 관찰된 실패 하나**를 고치고 동일 평가셋으로 다시 실행한다. 개선되지 않았으면 있는 그대로 기록한다.

**완료 조건:** 20개 **실행 결과**, v1/v2 비교, 통과/실패/미판정과 원인, 실서비스 데이터·푸시 부작용이 없다는 확인.

**중간 중단 가능 지점:** Phase 1만 끝나도 평가 결과 문서가 남아야 한다.

## Phase 2 — 관리자용 Read-only Tool Calling

**목표:** 관리자 자연어 요청 → Gemini의 Tool 선택 → 서버의 제한된 조회 → 결과 요약.

| 제안 Tool | 허용 작업 | 제한 |
| --- | --- | --- |
| `getCharacterConfig(characterId)` | 기존 캐릭터 설정 읽기 | 필요한 필드만 출력, 캐릭터 ID 검증 |
| `getConversationLogs(characterId?, filters?, limit?)` | 기존 관리자 대화·메시지 조회 | 페이지/범위 제한, 개인정보 최소 반환 |

- [ ] 관리자 전용 `/api/admin/**` 경로와 이미 있는 인증을 사용한다.
- [ ] Gemini에 도구 스키마/호출 결과를 주고받는 **별도 관리자 오케스트레이션**을 구현한다. 텍스트만 읽는 기존 SSE 파서를 Tool Calling으로 오인하지 않는다.
- [ ] 등록된 도구만 호출할 수 있게 허용 목록을 둔다. 임의 URL 요청/SQL 실행/파일 시스템 명령을 받지 않는다.
- [ ] 필터/페이지네이션/시간 제한/요청 오류를 검증하며 도구 호출 횟수에도 안전한 상한을 둔다.
- [ ] 조회된 대화 내용에 포함된 **프롬프트 인젝션**을 지시가 아닌 데이터로 취급한다.
- [ ] `apps/admin`에 최소 자연어 입력·조회 결과 UI를 추가한다. Spring 인증 쿠키는 브라우저에 직접 노출하지 않고 기존 `adminFetch`를 재사용한다.
- [ ] 미인증 접근, 잘못된 인자, 없는 데이터, 모델/API 오류, DB 쓰기 방지 테스트를 추가한다.

**완료 조건:** 조회 Tool **2개 실제 E2E 동작**, 비인가 및 잘못된 입력 차단, **조회 중 DB 쓰기 없음** 증명.

## Phase 3 — 변경 제안과 명시적 관리자 승인

**목표:** Agent는 캐릭터 설정을 제안할 수 있지만 **승인 없이 저장하지 못한다**.

- [ ] `proposeCharacterConfigChange(characterId, proposedFields, rationale)`는 **구조화된 제안만** 반환한다.
- [ ] 허용 필드는 현재 Admin 수정 DTO인 `concept`, `systemPrompt`, `ttsVoiceId`로 제한한다(필드별 null/필수 검증 확인).
- [ ] Admin 화면에 기존값/제안값 diff와 **적용/거절**을 보여준다.
- [ ] 관리자 승인 후에만 기존 `PUT /api/admin/characters/{id}` 경로로 적용한다. 서버 인증, DTO 유효성, 대상 캐릭터, 허용 필드를 별도로 검증한다.
- [ ] 이전 화면에서 이미 값이 바뀐 경우 잘못 덮어쓰지 않도록 최소한의 **stale-update 방지**를 고려한다.
- [ ] 거절, 미승인, 미인증, 미허용 필드, 교차 캐릭터 변경, 프롬프트 인젝션을 테스트한다.

**완료 조건:** 제안·거절 시 **DB 불변**; 승인·유효성 검사를 모두 거친 경우에만 변경됨.

## Phase 4 — Agent 통합 Evaluation 30~50개 및 자동 리포트

**목표:** Phase 1의 캐릭터 응답 품질뿐 아니라 실제 Tool Calling의 정확성과 안전성을 회귀 검증한다.

- [ ] Phase 1의 20개를 유지하고 10~30개를 더해 **총 30~50개**로 확장한다.
- [ ] 새 사례에 정상 Tool 선택, 인자, 조회 실패, 호출 횟수, 권한, 미승인 수정 차단, 로그 내 프롬프트 인젝션, API 오류, 잘못된 캐릭터 ID 등을 포함한다.
- [ ] **자동 채점:** 예상 Tool/인자, status, 미승인 DB 쓰기 0, 권한 차단 등은 Mock + H2 assertion으로 검증한다.
- [ ] **사람 판단이 필요한 품질:** 페르소나·허위 기억·답변 적절성은 루브릭과 수동 평가를 유지한다. LLM-as-a-Judge를 넣을 경우 사람 점수와 불일치 사례를 비교하고 **미판정을 자동 통과로 계산하지 않는다**.
- [ ] 실행 결과를 `JSON`과 사람이 읽을 `Markdown`으로 자동 생성한다: 시나리오 수/통과/실패/미판정, 범주별 결과, 모델/프롬프트/평가자, 실패 예시, 수정 전후·한계.
- [ ] 인증/권한/상태 변경에 관한 테스트는 실제 격리된 서버 로직으로, 외부 유료 Gemini 평가 호출은 **수동 트리거**를 기본값으로 한다.
- [ ] 기존 `apps/backend` 테스트 및 `apps/admin` 빌드로 회귀 여부 확인. 실제로 실행한 명령만 기록.
- [ ] GitHub PR/README에 **실제로 발생한 실패 → 원인 → 변경 → 재평가**를 검증 범위를 명시해 정리한다.

**완료 조건:** 30~50개 실행 가능한 데이터셋, 자동 리포트 재생성, Tool/승인·보안 assertion 실행, 전후 비교와 한계 기록.

**여기서 필수 개발 종료.** AX 지원용 결과를 문서화하되 새 기술을 추가하기 위해 계속 확장하지 않는다.

---

## Phase 5 — Langfuse/관측성 **[선택]**

착수 조건: 채용공고에 평가 관측성/LLMOps가 중요하거나 Phase 4 산출물이 관리하기 어려운 경우.

- 실행 ID, Trace, 도구 호출, Prompt 버전, 응답 시간, 실제로 수집 가능한 Token/비용을 연결한다.
- 비밀키 및 실제 대화 원문은 기본 수집하지 않는다. 마스킹·보관·접근 권한을 검토한다.
- 도입만으로 기업 수준의 운영 경험을 주장하지 않는다.

## Phase 6 — MCP **또는** RAG 중 하나 **[선택]**

착수 조건: **지원할 공고에 직접 필요한 기술**이 확인되었고 구현 비용 대비 가치가 있을 때.

- **MCP 선택 시:** Phase 2 Read-only 도구를 최소 권한 MCP 서버로 노출하고 인증·외부 클라이언트 호출·권한 테스트까지 검증한다.
- **RAG 선택 시:** 합성/허가된 캐릭터 설정·세계관 문서를 대상으로 검색→근거 포함 답변→출처/부재 평가를 구성한다.
- 둘 중 하나만 선택한다. 구현하지 않은 기술을 이력서에 기재하지 않는다.

---

## 검증 및 진행 규칙

**기본 명령(실제 환경 확인 후 실행):**
- 백엔드: `cd apps/backend && ./gradlew test`
- Admin: `cd apps/admin && npm run build`
- 유료 Gemini 평가: **명시적 실행**, 결과는 민감 정보가 없는 요약만 커밋.
- 실기기/LiveKit: Phase 0~4의 기본 대상에서 제외.

**각 Phase 종료 보고 형식**

```markdown
## Phase N 결과
- 변경 파일:
- 구현된 동작 (미구현 구분):
- 실제 실행한 테스트와 결과:
- 오류/실패 및 남은 한계:
- 인증·DB 부작용·비용 확인:
- 다음 단계: 대기 (별도 요청 시 진행)
```

**이력서 반영:** 실제 완료/실행된 작업만 기록한다. `30~50개`는 목표치일 뿐 실행 전 성과가 아니다. 합성 평가 결과를 실사용자 정확도, 프로덕션 성능 개선, AI 기업 경력으로 표현하지 않는다.
