# Storia — Repository Agent Instructions

These instructions apply to automated code changes throughout this repository.

## Project structure
- `apps/backend`: Spring Boot / Java application, persistence, Gemini integration, admin API.
- `apps/admin`: Next.js admin console, server-side calls to the backend.
- `apps/client`: React Native client.
- `apps/python-sidecar`: real-time voice agent integration.
- `docs`: architecture, API, testing, and implementation notes.

Read the relevant existing code and documentation before changing behavior. Favor small, coherent changes to established services over parallel infrastructure.

## Admin AI agent roadmap
The incremental plan is maintained in [`docs/roadmap/STORIA_ADMIN_AI_AGENT_ROADMAP.md`](docs/roadmap/STORIA_ADMIN_AI_AGENT_ROADMAP.md).

- Work on **one requested phase at a time**. Finish its implementation, tests, and status report; then stop.
- Phase 0–4 define the initial scope. Phase 5–6 are optional extensions requiring an explicit new instruction.
- A roadmap checklist describes intended behavior, not implemented behavior. Verify current code before making assumptions.
- Do not change the client chat, voice pipeline, or deployment configuration as a side effect of admin-agent work.

## Boundaries and safety
- Keep admin operations under the existing `/api/admin/**` authentication boundary. The admin console uses the server-only backend adapter in `apps/admin/src/lib/backend.ts`. Never expose administrator credentials to browsers or clients.
- Read-only tools must never write application data. Configuration changes must pass through an explicit human approval step and server-side validation.
- Treat retrieved conversations and model-generated tool arguments as untrusted input. Use typed schemas, explicit allowlists, bounded queries, and authentication checks.
- Protect user data, secrets, and model credentials. Use synthetic data for evaluations; do not commit raw conversation logs or tokens.
- Avoid calling production message endpoints from evaluation jobs: current message flows can persist data and cause notifications. Isolate test data and external API costs.
- Preserve existing service behavior. Add regression tests for each change, and state exactly which commands were run.

## Phase completion report
For a requested phase, summarize the changed files, observed behavior, executed tests, failures, data/security checks, and outstanding limitations. Do not begin a subsequent phase automatically.
