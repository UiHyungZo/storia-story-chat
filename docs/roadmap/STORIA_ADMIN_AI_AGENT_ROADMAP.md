# Storia Admin AI Agent — Implementation Roadmap

> **Status: proposed, not implemented.**
>
> **Core scope:** Phase 0–4. **Optional extensions:** Phase 5–6, based on demonstrated product requirements.
>
> Purpose: extend the existing Storia admin console with bounded AI-assisted inspection and human-approved configuration updates, supported by reproducible evaluation and regression checks.

## Existing components and integration points

| Component | Current behavior | Integration approach |
| --- | --- | --- |
| `apps/backend/src/main/java/com/storia/backend/service/GeminiService.java` | Uses Gemini streaming responses and extracts text chunks from SSE | Reuse model configuration where practical; handle function-call parts in an **isolated admin orchestration path** |
| `apps/backend/src/main/java/com/storia/backend/controller/ConversationController.java` | Saves user and assistant messages; assistant flow may produce notifications | Do **not** invoke this endpoint from evaluations |
| `apps/backend/src/main/java/com/storia/backend/controller/ConversationStompController.java` | WebSocket/STOMP chat stream | Preserve existing chat behavior |
| `apps/backend/src/main/java/com/storia/backend/controller/AdminCharacterController.java` | Admin character GET/PUT | Reuse existing authorized read/update services |
| `apps/backend/src/main/java/com/storia/backend/controller/AdminConversationController.java` | Admin conversation search and message paging | Reuse bounded, read-only queries |
| `apps/backend/src/main/java/com/storia/backend/config/AdminWebConfig.java` | Authentication gate for `/api/admin/**` | Keep new admin endpoints under the same gate |
| `apps/admin/src/lib/backend.ts` | Server-only backend adapter with admin session cookie | Reuse for admin UI and server actions |
| `apps/backend/src/test/resources/application.yml` | H2 test database in MySQL compatibility mode | Isolate integration tests from real application data |

**Design note:** `GeminiService.streamReply` currently parses text from SSE; it is not itself a function-calling orchestrator. Parsing model tool-call parts, validating arguments, executing allowlisted functions, and returning results are separate implementation tasks.

## Phase overview

| Phase | Scope | Completion artifact |
| --- | --- | --- |
| **0 — Architecture & isolation** | Inspect data flow, identify side effects, define evaluation boundaries | Integration note and test strategy |
| **1 — Response evaluation** | Establish 20 synthetic scenarios and baseline comparison | Reproducible results and failure analysis |
| **2 — Read-only tool calling** | Admin tool declarations, dispatch, secure retrieval, UI | Two functional, bounded lookup tools |
| **3 — Human-approved changes** | Configuration proposal and explicit confirmation | Tested propose/review/apply flow |
| **4 — Regression evaluation** | Expand to 30–50 scenarios, automated assertions and reports | Re-runnable evaluation report |
| **5 — Observability** | Trace and latency instrumentation | Optional, scoped instrumentation |
| **6 — External tools or retrieval** | MCP **or** RAG, when a concrete need is identified | Optional integration with tests |

Phases 0–4 form a discrete implementation increment. Any additional integration should address an identified functional or operational need, rather than expand the stack by default.

---

## Phase 0 — Architecture review and safe execution boundary

**Objective:** document the minimal code paths needed to add admin AI functionality without altering user chat behavior.

- [ ] Trace Gemini request/response handling and error paths, including timeouts and model configuration.
- [ ] Trace REST/STOMP conversation writes, notification side effects, and production data dependencies.
- [ ] Verify admin session enforcement, backend endpoints, and Next.js server-only API calls.
- [ ] Specify a non-production evaluation path with no message writes or push notifications.
- [ ] Choose a deterministic local/mock execution mode for default tests. Keep actual Gemini requests explicit and cost-bounded.
- [ ] Define scoped modules, input schemas, allowed fields, query limits, and test ownership.

**Done when:** a brief technical design records component boundaries, security assumptions, evaluation isolation, and concrete files to change. Do not begin Phase 1 automatically.

## Phase 1 — Baseline LLM response evaluation

**Objective:** establish a reproducible baseline for character responses and identify measurable regressions.

- [ ] Create an evaluation dataset with **20 synthetic scenarios** covering character consistency, fabricated memories, multi-turn context, correction/uncertainty, and error conditions.
- [ ] Define dataset fields: `id`, `system_prompt`, `history`, `input`, `expected_behavior`, `rubric`, `tags`.
- [ ] Add a runner that calls an isolated response-generation service or an explicit test adapter; do not route evaluation through conversation-save endpoints.
- [ ] Record model identifier, generation settings, prompt version, evaluation method, and run ID.
- [ ] Separate deterministic assertions from human-scored response characteristics. Record `pass`, `fail`, and `undetermined` without silently promoting ambiguous results to passing.
- [ ] Document at least one observed failure, a specific correction attempt, and re-evaluation under comparable conditions.

**Done when:** 20 scenarios can be executed and yield a baseline report with actual observations, limitations, and no writes to production conversation state.

## Phase 2 — Admin read-only tool calling

**Objective:** allow an authenticated administrator to request bounded application information using natural language.

### Proposed tools

| Tool | Operation | Constraints |
| --- | --- | --- |
| `getCharacterConfig(characterId)` | Read character configuration | Validate ID; return only required fields |
| `getConversationLogs(filters, limit)` | Search and summarize conversation records | Bounded pagination, permitted filters, minimal sensitive data |

- [ ] Add a dedicated admin-only Gemini function-calling orchestration path; do not repurpose the text-only streaming parser to infer tool calls.
- [ ] Declare typed tool schemas and a fixed server-side dispatch allowlist. Reject unknown functions and invalid arguments.
- [ ] Enforce admin session checks for every tool execution and keep Next.js credentials server-side.
- [ ] Add maximum row counts, timeouts, execution-step limits, and meaningful error handling.
- [ ] Treat model input and retrieved log messages as data. Embedded instructions within logs must not gain authority.
- [ ] Build a minimal admin UI for issuing a request and viewing retrieved information using the existing server-only backend adapter.
- [ ] Test correct selection, invalid arguments, missing records, unauthorized access, limits, and absence of DB writes.

**Done when:** both tool calls run through the authenticated admin flow, with bounded output and tested read-only guarantees.

## Phase 3 — Proposed configuration changes and human confirmation

**Objective:** support AI-assisted configuration review while preventing unapproved mutation.

- [ ] Implement `proposeCharacterConfigChange(characterId, proposedFields, reason)` as a **proposal operation only**; it must not write to the database.
- [ ] Restrict candidate fields to the existing admin update contract (`concept`, `systemPrompt`, `ttsVoiceId`) and validate field-level requirements.
- [ ] Show a before/after diff and explicit **Approve** / **Reject** actions in the admin console.
- [ ] Only the authenticated approval action may invoke the authorized character update path.
- [ ] Recheck target ID, field allowlist, current value, and session on approval. Prevent stale proposals from silently overwriting later changes.
- [ ] Add tests for rejection, approval, absent approval, invalid fields, outdated proposals, and injected instructions.

**Done when:** proposal/rejection leaves the DB unchanged, and only an authorized explicit approval can persist validated changes.

## Phase 4 — Integrated regression evaluation and reports

**Objective:** measure both response quality and operational correctness across the complete admin-agent flow.

- [ ] Extend the Phase 1 dataset to **30–50 synthetic scenarios** in total.
- [ ] Include tool selection, argument validation, authorization, bounded queries, unavailable records, approval gating, injected log content, and API failures.
- [ ] Run deterministic tool/authorization/state assertions with mocks and isolated test storage. Separate these from optional live model calls.
- [ ] Maintain explicit scoring rubrics for qualitative behavior; do not substitute automated confidence for unreviewed judgments.
- [ ] Produce machine-readable results (JSON) and a concise Markdown report with run parameters, totals, categories, observed failures, and unresolved cases.
- [ ] Compare at least two controlled runs, document material differences, and preserve regression cases.
- [ ] Run relevant backend tests and admin build checks. Report commands and results; do not claim checks that were not executed.
- [ ] Confirm evaluation does not trigger application messages, notifications, unauthorized reads, or unapproved configuration updates.

**Done when:** the scenario suite and reporting command are repeatable, authorization and approval invariants are tested, and findings can be independently reproduced.

## Phase 5 — Observability (optional)

Introduce tracing, latency, error classification, and measured usage only if Phase 4 reveals a concrete diagnostic gap. Keep secrets and conversation content out of telemetry by default. Evaluate retention and access control before using an external provider.

## Phase 6 — MCP or RAG (optional)

Implement **one** extension after defining its use case:

- **MCP:** expose a narrowly scoped, authenticated integration for selected admin tools and test caller permissions.
- **RAG:** retrieve from approved character/reference material; test citation accuracy, missing evidence, and retrieval failures.

Avoid expanding both directions without a concrete requirement.

---

## Verification conventions

Run the relevant commands in the actual local environment, for example:

```bash
cd apps/backend && ./gradlew test
cd apps/admin && npm run build
```

External paid-model evaluations should be explicitly triggered, with controlled cost and safe test data.

### Phase result template

```markdown
## Phase N
- Files changed:
- Implemented behavior:
- Commands executed and results:
- Observed failures and limitations:
- Authentication / data access / side-effect checks:
- Next phase: not started
```

**Documentation rule:** distinguish plans from shipped behavior. Scenario counts are targets until executed; synthetic evaluation is not evidence of real-user service performance.
