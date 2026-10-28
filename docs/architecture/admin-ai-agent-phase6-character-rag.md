# Phase 6E — 캐릭터 세계관 RAG

> 상태: 구현·자동 검증 완료(로컬 JUnit). **실제 Gemini 호출은 하지 않았다.** 실모델 응답 품질, Gemini 임베딩, 실기기/서버 동작은 **미검증**이다 (8절).
> 이 문서의 표기: **[확인]** 코드·테스트로 확인 / **[제안]** 설계 선택 / **[미확인]** 검증하지 못함.
> 기반: [Phase 6](admin-ai-agent-phase6.md)의 RAG 엔진. 새 Phase 번호는 만들지 않았다.

## 1. 목표와 범위

관리자 문서 검색(6A)과 달리, **실제 Storia 사용자와 AI 캐릭터가 대화할 때** 해당 캐릭터의 세계관 지식을 검색해 System Prompt에 참고 자료로 덧붙인다. 기능은 Feature Flag로 켜며 **기본은 꺼짐**이다. 꺼져 있으면 기존 채팅 코드 경로의 프롬프트·메시지·이벤트가 변하지 않는다.

하지 않은 것: MCP Tool 추가, Python Sidecar 변경, 클라이언트 변경, Dockerfile/배포 설정 변경, 새 벡터 DB, 유료 API 호출.

## 2. 아키텍처

```
사용자 메시지 ─ REST ConversationController ─┐
              STOMP ConversationStompController ├─> ChatContextAssembler.systemPromptFor(characterId, basePrompt, history)
              VoiceCallService.completeTurn  ──┘        │ (플래그 꺼짐/실패/근거없음이면 basePrompt 그대로)
                                                        ▼
                                          CharacterLoreService.searchCharacterLore(characterId, query, topK)
                                            ├ characterId ─(DB 이름 조회)→ CharacterLoreCatalog → loreKey
                                            └ 그 키의 KnowledgeBase(전용 인덱스)만 검색 ─> KnowledgeSearchService (Phase 6 재사용)
                                                        ▼
                    GeminiService.streamReply(systemPrompt + <lore-reference>…, history)   ← 시그니처 불변
```

| 구성요소 | 위치 | 역할 |
| --- | --- | --- |
| `ChatContextAssembler` | `service/` | 대화 컨텍스트 조립 계층. 예외를 던지지 않음. `GeminiService`는 수정하지 않음 |
| `CharacterLoreService` | `lore/` | `searchCharacterLore(characterId, query, topK)`. 시간 예산, 캐릭터별 인덱스, 격리 이중 확인 |
| `CharacterLoreCatalog` | `lore/` | 캐릭터 이름 → 세계관 키/문서 경로 매핑 |
| `CharacterLoreMetrics` | `lore/` | 관측(캐릭터·상태·지연만, 내용 없음) |
| `ClasspathDocumentLoader`, `KnowledgeDocumentLoader` | `rag/` | 문서 로더 포트화. 기존 `DocumentLoader`(파일)와 `KnowledgeBase`가 같은 인터페이스를 쓴다 |
| `CharacterRagProperties`, `CharacterRagConfig` | `config/` | `storia.character-rag.*`, 빈 조립, 기동 후 비동기 워밍업 |

재사용(변경 없음): `MarkdownChunker`, `TextSanitizer`, `EmbeddingProvider`/`LocalHashing…`/`GeminiEmbeddingProvider`/`CachingEmbeddingProvider`, `InMemoryVectorIndex`, `KnowledgeSearchService`. 변경한 기존 코드: `DocumentLoader`(인터페이스 구현 선언), `KnowledgeBase`(로더 타입을 인터페이스로, `peek()` 추가), 세 호출 경로(아래 4절), 관측 응답에 `characterLore` 필드 추가.

## 3. 캐릭터별 세계관 지식

- 위치: `apps/backend/src/main/resources/characters/{aria,ren,noah}/lore.md` — **클래스패스 리소스라 JAR에 포함**된다 [확인: `bootJar` 산출물에서 3개 파일 확인].
  - 요청서가 제안한 `docs/characters/…` 대신 리소스로 둔 이유: `apps/backend/Dockerfile`은 `apps/backend`만 복사하므로 저장소 루트의 `docs/`는 컨테이너 이미지에 없다 (8절 참조).
- 세 문서 모두 **테스트용 예시 세계관**이다 (파일 머리의 주석에 명시). `CharacterSeeder`의 설정(우주선 항법 AI/폐업 위기 서점/탐정 사무소 조수)과 모순되지 않게 썼으며, 확정된 서비스 설정이 아니다. 등장인물·장소·사건은 모두 이 작업에서 새로 만든 가상 내용이다.
- 크기: 문서당 약 2.4~2.7KB, 청크 7~8개.

### 매핑과 ID 안정성 [제안·확인]
DB의 자동 증가 ID는 DB를 새로 만들면 달라질 수 있다. ID로 매핑하면 다른 캐릭터의 세계관에 붙을 위험이 있으므로, **시딩된 캐릭터 이름이 정확히 일치할 때만** 세계관 키(`aria`/`ren`/`noah`)에 연결한다. 이름이 바뀌면(관리자 수정 등) 매핑이 끊겨 세계관이 빠진다 — 잘못된 세계관이 붙는 것보다 빠지는 쪽을 안전한 실패로 택했다.
- 테스트: 같은 이름이 다른 ID(777)로 재생성되어도 같은 세계관 연결, 이름 바뀐 캐릭터/없는 ID는 `NO_LORE` (`CharacterLoreServiceTest`), 실제 Spring 컨텍스트에서 DB가 발급한 ID로 올바른 키 연결(`CharacterLoreSpringTest`).
- 한계: 새 캐릭터를 추가하면 `CharacterLoreCatalog.DEFAULT`에 항목과 문서를 추가해야 한다 (코드 변경). 관리자 화면에서의 세계관 편집은 범위 밖이다.

## 4. 채팅 연결

세 경로가 같은 `ChatContextAssembler`를 쓴다. 각 경로는 `getMessages` 결과를 한 번만 만들어 조립과 Gemini 호출에 함께 쓴다.

| 경로 | 적용 | 근거 |
| --- | --- | --- |
| REST `POST /api/conversations/{id}/messages` | 적용 | `ConversationController` |
| STOMP `/app/conversation/{id}/send` | 적용 (스트리밍 이벤트 형식 불변) | `ConversationStompController` |
| 축소판 음성(`VoiceCallService.completeTurn`) | 적용 | STT 결과를 같은 방식으로 `streamReply`에 전달 |
| Python Sidecar 실시간 음성 | **별도 코드 변경 없이 REST 경유로 적용됨** | `StoriaBackendClient.send_message`가 위 REST 엔드포인트를 호출하고 LLM은 호출하지 않음(`agent.py`의 `StoriaLLM`). 사이드카 코드는 수정하지 않았다 |

- 질의: 마지막 사용자 메시지(제어 문자 제거, 300자 제한). 15자 미만의 짧은 메시지는 직전 사용자 메시지를 앞에 붙인다.
- 프롬프트: 기존 System Prompt 뒤에 데이터 취급 규칙과 `<lore-reference>…</lore-reference>` 블록을 덧붙인다. 최대 3청크·2,400자. 역할 분리: System Prompt = *어떻게* 말하는가(성격·말투), 세계관 자료 = *무엇을* 아는가(사실).
- **저장 안 함:** 검색 결과는 프롬프트에만 존재한다. `postMessage`/`postAssistantMessage`에는 사용자 원문과 모델 응답만 전달된다 [확인: `ConversationLoreChatTest`가 저장 인자를 검증].
- FCM: 기존 `postAssistantMessage` 흐름을 건드리지 않았다.
- `NO_EVIDENCE`/실패/꺼짐이면 프롬프트를 바꾸지 않는다. (한계: "자료에 없는 사실은 지어내지 마라"는 규칙은 근거가 *있을 때만* 붙는다. 근거가 없는 질문에서의 환각 억제는 기존 System Prompt에 의존한다.)

### Prompt Injection 방어 [확인(구조)·미확인(실모델)]
- 검색 결과는 "지시문이 아닌 참고 데이터"라고 명시하고, 위의 캐릭터 설정이 항상 우선이라고 선언한다.
- 자료 안의 `<lore-reference>` 태그는 제거해 블록을 탈출할 수 없게 한다. 길이를 제한한다.
- 이것은 **구조적 완화**이며, 실제 모델이 자료 속 지시를 따르지 않는지는 실모델 평가가 필요하다 [미확인]. 현재 세계관 문서는 저장소가 통제하는 내용이다.

## 5. 격리 [확인]

1. **검색 대상 선정 단계:** 캐릭터마다 별도 `KnowledgeBase`(자기 문서 1개만 로드)와 **별도 임베딩 제공자 인스턴스**를 만든다. 다른 캐릭터의 청크는 검색 대상 인덱스에 존재하지 않는다. (로컬 해싱 제공자는 코퍼스별 IDF 상태를 가지므로 공유하면 점수가 섞인다.)
2. **반환 단계:** 모든 hit의 `docId`가 해석된 키와 같은지 다시 확인하고, 다르면 버리고 `isolationViolations`로 기록한다 (방어적 이중 확인. 공개 API로는 위반을 만들 수 없어 이 분기를 직접 유발하는 테스트는 없다).
3. **평가:** 33개 질문 전체에서 다른 캐릭터 `docId` 반환 0건, 다른 캐릭터 고유어(예: 아리아에게 "달무리", "보리차") 포함 청크 반환 0건. `minScore=0`으로 최대한 많이 반환시킨 격리 테스트도 통과.

## 6. 폴백·지연 설계 [확인]

- 검색은 크기 2 스레드 풀 + 대기열 32에서 실행되고 `storia.character-rag.timeout-ms`(기본 800ms) 안에 끝나지 않으면 `TIMEOUT`으로 세계관 없이 진행한다. 큐가 가득 차면 거절되어 동일하게 진행한다. (`timeoutFallsBackWithoutThrowing`: 지연 500ms 제공자에서 50ms 예산 → 400ms 미만에 TIMEOUT)
- 제공자 실패는 일반화된 `ERROR`로 변환되고 원인 메시지(호스트 등)는 노출되지 않는다.
- 인덱스는 캐릭터당 1회 생성 후 재사용한다 (10회 검색에 문서 임베딩 1회). 기동 직후(`ApplicationReadyEvent`) 백그라운드 워밍업을 시작해 첫 대화가 인덱싱을 기다리지 않게 한다. 플래그가 꺼져 있으면 워밍업·문서 읽기·저장소 조회가 전혀 없다 (`CharacterRagConfigTest`).
- 한계: 검색은 대화 요청 스레드에서 **동기적으로 최대 예산만큼** 기다린다. 로컬 제공자에서는 p50 0.06ms지만, Gemini 임베딩 제공자는 질의마다 네트워크 호출이 1회 추가되므로 지연 영향은 실측이 필요하다 [미확인].

## 7. 평가 결과 (로컬 해싱, 합성 질문 33개)

실행: `./gradlew test --tests '*CharacterLoreEvalTest'` → `build/eval/character-lore-eval.json`. 외부 호출 없음.
질문 구성: 세계관 18(캐릭터당 6) / 패러프레이즈 3 / 타 캐릭터 지식 6 / 세계관에 없는 질문 4 / 질의 속 Prompt Injection 2. 질문은 세계관 문서를 쓴 같은 사람이 만들었으므로 **블라인드가 아니다.** 근거 인정 점수 임계값(`0.08`)은 calibration 분할(17문항)의 점수로만 골랐고, holdout(16문항)으로 확인했다 (기본값 0.12는 짧은 질문에 너무 엄격했다). 표본이 작아 수치의 오차 범위가 크다.

| 지표 | 전체 | calibration | holdout |
| --- | --- | --- | --- |
| 범위 내 질문 수 | 23 | 12 | 11 |
| Hit@1 | 0.652 | 0.667 | 0.636 |
| Hit@3 | 0.913 | 0.917 | 0.909 |
| Hit@5 | 0.957 | 0.917 | 1.000 |
| MRR@5 | 0.772 | 0.778 | 0.765 |
| 범위 내인데 근거 없음 처리(임계값 적용 후) | 0.217 | 0.167 | 0.273 |
| 범위 밖 질문을 NO_EVIDENCE로 처리 | 0.700 | 0.800 | 0.600 |
| 범위 밖인데 근거로 채택(오탐) | 0.300 | 0.200 | 0.400 |

- 관련도 정의: 반환 청크의 헤딩이 기대 헤딩을 포함하면 hit (문서가 캐릭터당 1개뿐이라 문서 수준 hit는 의미가 없다).
- 범주별: 세계관 Hit@3 0.944(18), 패러프레이즈 0.667(3), 타 캐릭터 질문 NO_EVIDENCE 0.667(6), 세계관에 없는 질문 NO_EVIDENCE 0.75(4), 질의 속 Injection 문구가 있어도 검색 Hit@3 1.0(2).
- **캐릭터 간 지식 누출 0건** (docId 기준 0, 고유어 내용 기준 0).
- 지연(`searchCharacterLore`, 워밍업 후 165회): p50 0.056ms, p95 0.088ms, max 0.158ms.
- 한계: 임계값을 적용하면 holdout 범위 내 질문의 27%가 근거 없음으로 처리되고 범위 밖 질문의 40%가 오탐으로 채택된다. 어휘 기반 검색의 한계다 (예: "경찰이랑은 어떻게 협력해?"는 어휘가 겹치지 않아 실패). 타 캐릭터 질문이 오탐(예: 아리아에게 "서도윤 소장은?")이면 *그 캐릭터 자신의* 관련 없는 청크가 붙을 뿐 다른 캐릭터 내용은 들어오지 않는다.

### 적용 전후 비교 구조 (프롬프트 컨텍스트 수준)
같은 질문으로 플래그 꺼짐/켜짐 프롬프트를 조립해 정답 사실 문자열이 프롬프트에 있는지만 센다.

| 지표 | 값 |
| --- | --- |
| 정답 사실이 프롬프트에 있음 — RAG 꺼짐 | 0.0 (23문항) |
| 정답 사실이 프롬프트에 있음 — RAG 켜짐 | 0.652 (23문항) |
| 플래그 꺼짐일 때 프롬프트 변경 없음 | 1.0 (33문항) |
| 범위 밖 질문에 불필요한 세계관이 붙은 비율 | 0.3 (10문항) |

이는 **근거 가용성**이며 **LLM 응답 품질이 개선되었다는 증거가 아니다.** 꺼짐의 0.0은 기본 System Prompt에 세계관 사실이 없기 때문에 당연한 값이다.

### 테스트 ↔ 요청 평가 범위 대응
| 요청 범위 | 테스트 | 실모델 필요 |
| --- | --- | --- |
| 아리아/렌/노아 질문, 캐릭터 간 격리, 없는 사실, 검색 실패, Injection | `CharacterLoreEvalTest`, `CharacterLoreServiceTest` | 답변 품질은 필요 |
| Prompt Injection(프롬프트 구조) | `ChatContextAssemblerTest` | 모델이 따르지 않는지는 필요 |
| 타임아웃·오류 | `CharacterLoreServiceTest` | 아니오 |
| RAG 꺼짐 시 기존 동작 | `ConversationLoreChatTest`, `VoiceCallServiceLoreTest`, `CharacterRagConfigTest` | 아니오 |
| REST/STOMP/음성 연결, 저장 불변, 이벤트 형식 | `ConversationLoreChatTest`, `VoiceCallServiceLoreTest` | 실서버/실기기는 필요 |

## 8. 배포와 문서 제공 경로 [확인·제안]

- **캐릭터 세계관:** 클래스패스 리소스라 JAR에 포함되어 Docker 이미지에서도 누락되지 않는다 (`bootJar` 산출물 확인). 이 부분은 배포 환경 설정이 필요 없다.
- **기존 관리자 Wiki RAG(Phase 6)의 배포 문제 [확인(코드)·미확인(실배포)]:** 관리자 문서 RAG는 저장소 루트의 `docs/` 등을 파일 시스템에서 읽는다(`storia.rag.docs-root`, 기본 `../..`). `apps/backend/Dockerfile`은 `apps/backend`만 복사하므로 컨테이너에는 해당 문서가 없고 `INDEX_EMPTY`가 된다 (오류가 아니라 "검색할 문서가 인덱싱되어 있지 않습니다"로 정상 처리된다). 해결 후보: (a) Docker 빌드 컨텍스트를 저장소 루트로 바꾸고 허용 목록 문서를 이미지에 복사, (b) 허용 목록 문서를 빌드 시 `processResources`로 JAR에 복사해 `ClasspathDocumentLoader` 사용, (c) 문서 볼륨 마운트 + `STORIA_RAG_DOCS_ROOT`. 이번 작업은 배포 설정을 바꾸지 않는다는 규칙(AGENTS.md)에 따라 **변경하지 않았고**, 이 항목은 별도 결정이 필요하다. 이번에 도입한 `KnowledgeDocumentLoader`/`ClasspathDocumentLoader` 덕분에 (b)는 작은 변경으로 가능하다.
- **CI:** `.github/workflows/ci.yml`은 `apps/backend`에서 `./gradlew test`를 실행하므로 이번 테스트(외부 호출 없음)가 그대로 포함된다. 라이브 테스트는 기존대로 `test`에서 제외된다.

## 9. 관측

`GET /api/admin/ai/observability`에 `characterLore` 필드를 추가했다 (관리자 인증 하에서만). 구조: `enabled`, `searches`, `byStatus`, `byCharacter`(캐릭터×상태), `avgLatencyMs`/`p50LatencyMs`/`p95LatencyMs`(밀리초, 소수), `isolationViolations`, `indexes`(캐릭터별 `NOT_BUILT/READY/EMPTY`, 제공자, `semantic`, 문서/청크 수, 생성 시각). 질의·대화 원문은 기록하지 않는다 (`metricsRecordNoQueryContent`). 메모리 링 버퍼 500건이며 재시작 시 사라진다. 관리자 UI(`apps/admin`) 표시는 추가하지 않았다 [미구현].

## 10. MCP 재사용 가능성 (구현하지 않음)

`storia-knowledge` MCP 서버는 프로젝트 문서 검색용으로 그대로 둔다. 외부 MCP 클라이언트에서 세계관을 조회하려면, `CharacterLoreService.searchCharacterLore`가 MCP 서버 프로세스(Spring 없는 독립 프로세스)에서 쓰기에 적합한 구조인가를 보면 된다:
- 가능한 점: 검색 로직은 Spring과 무관한 순수 객체이고 문서는 클래스패스에 있다. 캐릭터 ID→이름 조회(`LongFunction`)와 제공자 팩토리만 주입하면 된다.
- 막히는 점: MCP 프로세스는 DB에 접근하지 않으므로 `characterId` 대신 *세계관 키*(`aria`/`ren`/`noah`)를 입력으로 받는 별도 진입점이 필요하다. 임의의 캐릭터 세계관을 외부 클라이언트에 열어주는 것은 접근 제어 결정(인증 없는 STDIO 서버의 노출 범위)이 필요하다.
- 이번에는 MCP Tool을 추가하지 않았다.

## 11. 변경 파일

신규: `lore/{CharacterLoreCatalog,CharacterLoreService,CharacterLoreResult,CharacterLoreMetrics,LoreStatus}`, `rag/{KnowledgeDocumentLoader,ClasspathDocumentLoader}`, `service/ChatContextAssembler`, `config/{CharacterRagProperties,CharacterRagConfig}`, `resources/characters/{aria,ren,noah}/lore.md`, 이 문서, 테스트 7종(`CharacterLoreServiceTest`, `CharacterLoreEvalTest`, `CharacterLoreSpringTest`, `ChatContextAssemblerTest`, `ConversationLoreChatTest`, `VoiceCallServiceLoreTest`, `CharacterRagConfigTest`) + `LoreTestSupport` + 테스트 리소스(`character-lore-eval.json`, `lore-fixtures/injected/lore.md`).
수정: `ConversationController`, `ConversationStompController`, `VoiceCallService`(조립 계층 호출), `DocumentLoader`/`KnowledgeBase`(로더 인터페이스), `AdminObservabilityController`/`AdminObservabilityResponse`(`characterLore`), `application.yml`(`storia.character-rag`), 기존 테스트 2종(`GlobalExceptionHandlerTest`에 `@MockitoBean` 1줄, `AdminObservabilityControllerTest`에 `characterLore` 검증), 로드맵(6E 항목).

## 12. 활성화와 후속 검증

설정: `STORIA_CHARACTER_RAG_ENABLED=true`(기본 false), `STORIA_CHARACTER_RAG_PROVIDER=local|gemini`(기본 local), `STORIA_CHARACTER_RAG_TIMEOUT_MS`(기본 800).
**Gemini 제공자는 코드 경로만 있고 호출한 적이 없다.** `gemini`를 켜면 첫 검색/워밍업에서 세계관 3개 문서 임베딩(약 8KB)과 대화마다 질의 임베딩 1회의 유료 호출이 발생한다. 이 제공자의 근거 점수 임계값은 보정되지 않았다(`null` → 제공자 기본 0.55, 자리표시).

집에서 할 후속 검증(모두 사용자 승인 후):
1. 실서버에 플래그를 켜고(`local` 제공자) 앱에서 아리아/렌/노아에게 세계관 질문을 해 답변에 반영되는지 확인, 꺼진 상태와 같은 질문 비교(동일 질문 N회, 사람이 판정).
2. 실모델 Prompt Injection: 세계관 문서를 일시적으로 위험 문구가 든 문서로 바꾼 로컬 환경에서 모델이 따르는지 확인.
3. Gemini 임베딩: 승인 후 `provider=gemini`로 33개 질문을 돌려 임계값 재보정(캐시 사용), 패러프레이즈 질문 개선 여부 확인.
4. 지연: STOMP 첫 CHUNK까지 시간을 플래그 꺼짐/켜짐(local, gemini)으로 비교.
5. Python Sidecar 경로에서 실제 통화 한 번으로 REST 경유 적용 확인.
