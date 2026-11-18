# Test execution report

**Date:** 2026-11-18  
**Spec basis:** `spec/test-strategy.md`, `spec/evaluation-strategy.md`  
**Environment:** macOS, Java 21, Node (project-local `frontend/node_modules`), no Docker, **no Ollama** during the default cycle.

## Executive summary

| Suite | Command | Executed | Passed | Failed | Skipped |
|---|---|---:|---:|---:|---:|
| Backend (Maven) | `cd backend && ./mvnw verify` | **58** | **58** | **0** | **0** |
| Frontend lint | `cd frontend && npm run lint` | — | **Pass** | **0** | — |
| Frontend types | `cd frontend && npm run typecheck` | — | **Pass** | **0** | — |
| Frontend unit/component | `cd frontend && npm run test` | **12** | **12** | **0** | **0** |
| Frontend production build | `cd frontend && npm run build` | — | **Pass** | **0** | — |
| RAG evaluation (Ollama) | *Not in default build* | **0** | **0** | **0** | **N/A (not run)** |

**Mocked vs real AI (default build):**

- **Embedding:** `TestEmbeddingConfig` (`@Profile("test")`) — deterministic 768-dim vectors; **not** Ollama / `nomic-embed-text`.
- **Chat:** `ScriptableTestChatModel` (`TestChatConfig`, test profile) — queued scripted replies; **not** a live LLM.
- **Production/local (not exercised in this report):** Ollama embedding + chat per `application.yml`.

---

## Backend

### Unit — domain and state machine

| Item | Value |
|---|---|
| **Command** | `cd backend && ./mvnw test -Dtest=TicketStateMachineTest,TicketStatusTest,TicketPriorityTest,TicketEntityTest` |
| **Classes** | `TicketStateMachineTest`, `TicketStatusTest`, `TicketPriorityTest`, `TicketEntityTest` |
| **Executed** | 12 |
| **Passed** | 12 |
| **Failed** | 0 |
| **Skipped** | 0 |

**Covers (partial vs `spec/test-strategy.md`):** UT-ST-01 matrix counts (`TicketStateMachineTest.fullMatrixHasTwentyRejectsAndFiveAllows`), same-status rejections, entity/priority/status enums.

**Limitations:** UT-ST-02–06 (status **service** with fake ingestion writer) are not isolated unit tests; transition apply/optimistic-lock behaviour is only partially covered via HTTP integration.

---

### Unit — validation

| Item | Value |
|---|---|
| **Command** | `cd backend && ./mvnw test -Dtest=TicketValidatorTest,AskQuestionValidatorTest` |
| **Classes** | `TicketValidatorTest` (4), `AskQuestionValidatorTest` (3) |
| **Executed** | 7 |
| **Passed** | 7 |
| **Failed** | 0 |
| **Skipped** | 0 |

**Covers (partial):** UT-VAL-01/02/04/06 fragments (search length, unknown status, `%` literal escape, reserved assignee; ask blank/over-limit/trim).

**Limitations:** Full UT-VAL-01–07 matrix (every field max length, paging bounds, malformed enums on every endpoint) is **not** fully enumerated in unit tests; many cases are deferred to integration or unimplemented.

---

### Unit — RAG ingestion (pure)

| Item | Value |
|---|---|
| **Command** | `cd backend && ./mvnw test -Dtest=TicketDocumentExtractorTest,TicketChunkerTest,ChunkIdFactoryTest` |
| **Classes** | extractor (1), chunker (1), chunk ids (1) |
| **Executed** | 3 |
| **Passed** | 3 |
| **Failed** | 0 |
| **Skipped** | 0 |

**Covers (partial):** UT-ING-01/02/04/05-style checks (metadata, header layout, assignee `UNASSIGNED`).

**Limitations:** ING-01–ING-20 / UT-ING-03 (oversized paragraph splits), UT-ING-06–08 (priority re-chunk, embedding rollback) are **not** fully automated.

---

### Unit — RAG retrieval, citations, ask orchestration (mocked store / models)

| Item | Value |
|---|---|
| **Command** | `cd backend && ./mvnw test -Dtest=RetrievalServiceTest,CitationValidatorTest,CitationSnippetsTest,TicketIdExtractorTest,AskServiceTest` |
| **Executed** | 12 |
| **Passed** | 12 |
| **Failed** | 0 |
| **Skipped** | 0 |

| Test class | Focus | AI |
|---|---|---|
| `RetrievalServiceTest` (4) | Threshold, top-K, named ticket bypass, missing id | Fake store scores; **no** embedding call |
| `CitationValidatorTest` (3) | Invented id, no citation, valid citation | N/A |
| `CitationSnippetsTest` (2) | Snippet selection/truncation | N/A |
| `TicketIdExtractorTest` (1) | `TKT-\d+` in questions | N/A |
| `AskServiceTest` (2) | Empty retrieval → no LLM; citation reject → fail-closed | **Mocked** `ChatModel` |

**Covers (partial):** UT-RET-01/02/04, FR-13 AC2/AC3 deterministic paths, FR-14 empty retrieval.

**Limitations:** UT-RET-03/05/06/07 (config rebinding, visibility with DB, cap after filter, invalid config startup) only partially covered; UT-RET-05 visibility is exercised in integration ingestion, not in isolation.

---

### Integration — repositories

| Item | Value |
|---|---|
| **Command** | `cd backend && ./mvnw test -Dtest=TicketRepositoryIntegrationTest,CommentRepositoryIntegrationTest` |
| **Executed** | 4 |
| **Passed** | 4 |
| **Failed** | 0 |
| **Skipped** | 0 |

**Covers (partial):** IT-DB-01 fragments (persistence, comment ordering).

**Limitations:** IT-DB-02 (public id sequence across creates), dedicated Flyway-only IT-DB-03 assertion are **not** dedicated tests.

---

### Integration — services

| Item | Value |
|---|---|
| **Command** | `cd backend && ./mvnw test -Dtest=TicketServiceIntegrationTest` |
| **Executed** | 2 |
| **Passed** | 2 |
| **Failed** | 0 |
| **Skipped** | 0 |

**Limitations:** UT-TS-01–09 orchestration suite with Mockito fakes is **not** present; only two Spring integration scenarios.

---

### Integration — REST / controllers (tickets)

| Item | Value |
|---|---|
| **Command** | `cd backend && ./mvnw test -Dtest=TicketApiIntegrationTest` |
| **Executed** | 8 |
| **Passed** | 8 |
| **Failed** | 0 |
| **Skipped** | 0 |

**Covers (partial):** IT-API-01–05 fragments (create/list/get/update/search/transitions/errors).

**Limitations:** IT-API-06 (`%`/`_` literal search), IT-API-09 (500 body shape), IT-ST-04 (concurrent transitions), full SM-01–SM-16 HTTP matrix, explicit IT-API-07 (search must not call embedding bean) are **missing or incomplete**.

---

### Integration — RAG ingestion and re-ingestion

| Item | Value |
|---|---|
| **Command** | `cd backend && ./mvnw test -Dtest=IngestionPipelineIntegrationTest` |
| **Executed** | 1 |
| **Passed** | 1 |
| **Failed** | 0 |
| **Skipped** | 0 |

**Covers:** IT-RE-01 fragment — create → index → update → visible metadata refresh (fake embedding).

**Limitations:** IT-RE-02–04, IT-RE-03 (embedding throw + retry), worker `FAILED` observability, IT-RS-01/02 restart persistence and post-restart ask are **not** automated.

---

### Integration — grounded ask API (mocked chat, real fake embed in process)

| Item | Value |
|---|---|
| **Command** | `cd backend && ./mvnw test -Dtest=AskApiIntegrationTest` |
| **Executed** | 8 |
| **Passed** | 8 |
| **Failed** | 0 |
| **Skipped** | 0 |

| Scenario | AI |
|---|---|
| Grounded answer with citations | **Scripted** chat model |
| Irrelevant / empty index → no-match, no chat | Fake embed retrieval only |
| Invalid / uncited model output → fail-closed | Scripted bad answers |
| Blank / overlong question → 400 | Chat **not** called |
| Index not ready → 503 | Chat **not** called |

**Covers (partial):** FR-12/13/14, IT-API-07/08 fragments, grounding fail-closed (PI-D1/D3-style).

**Limitations:** Scripted chat failure → 503 (IT-API-08 embed/chat outage) **not** tested; no assertion that ask creates no ticket row.

---

### Integration — application context smoke

| Item | Value |
|---|---|
| **Command** | `cd backend && ./mvnw test -Dtest=TicketManagementApplicationTests` |
| **Executed** | 1 |
| **Passed** | 1 |
| **Failed** | 0 |
| **Skipped** | 0 |

---

### Regression / full backend gate

| Item | Value |
|---|---|
| **Command** | `cd backend && ./mvnw verify` |
| **Executed** | **58** (all Surefire tests) |
| **Passed** | **58** |
| **Failed** | 0 |
| **Skipped** | 0 |

---

## Frontend

### Lint and typecheck

| Item | Value |
|---|---|
| **Commands** | `npm run lint`, `npm run typecheck` |
| **Result** | Pass (0 ESLint errors; `tsc --noEmit` clean) |

---

### Unit / component tests (Vitest, jsdom)

| Item | Value |
|---|---|
| **Command** | `cd frontend && npm run test` |
| **Executed** | 12 |
| **Passed** | 12 |
| **Failed** | 0 |
| **Skipped** | 0 |

| File | Group | Notes |
|---|---|---|
| `lib/api/client.test.ts` | API integration (mocked `fetch`) | Error parsing, network failure |
| `lib/api/errors.test.ts` | Error handling | Banner mapping for network / 503 |
| `lib/validation.test.ts` | Validation | Limits, no-match sentence, field errors |
| `lib/navigation.test.ts` | Citation navigation | Ticket detail paths from citation ids only |
| `components/ui/ErrorBanner.test.tsx` | Component | Error presentation |
| `components/assistant/AssistantDrawer.test.tsx` | AI citations | No fabricated citation ids |

**Limitations:**

- No Playwright/Cypress **UI** journeys (`UI-*` in test strategy): list columns, drawer open while navigating, manual responsive pass — **not** automated.
- No live `fetch` against a running backend in CI (Next rewrites assumed; E2E **not** run in this cycle).
- Full assistant drawer RTL test with mocked `POST /api/ai/ask` **not** implemented (rules tested via helpers + partial component coverage).

---

### Build (regression compile)

| Item | Value |
|---|---|
| **Command** | `cd frontend && npm run build` |
| **Result** | **Pass** |

---

## RAG evaluation (Level 4 — not run)

Per `spec/test-strategy.md` §4 and `spec/evaluation-strategy.md`, the following require **Ollama** (real embedding and/or chat) and a golden dataset. They are **out of scope for the default build** and were **not executed** in this cycle:

| Area | Status |
|---|---|
| Retrieval recall@K / hit@K on golden set | **Skipped** — Ollama not invoked |
| Answer groundedness / hallucination rate | **Skipped** |
| Prompt-injection resistance (real model) | **Skipped** |
| Numeric targets (0.80 recall, 0.90 citation precision, etc.) | **Not measured** |

**Command when Ollama is available:** to be defined when evaluation harness is added (not present in repo v1).

---

## Gap summary vs `spec/test-strategy.md`

The default automated gate is **green** (`58` backend + `12` frontend tests). Substantial strategy IDs remain **unimplemented or only partially covered**, including:

- UT-TS-* (TicketService with Mockito fakes)
- UT-ST-02–06, UT-CM-01–06 (dedicated service unit suites)
- Most UT-ING-* / ING-* ingestion edge cases
- IT-API-06, IT-API-09, IT-ST-04, full state-matrix HTTP sweep
- IT-RE-02–04, IT-RS-01/02
- All **EV-*** / golden-dataset evaluation
- Browser **UI-*** journeys

These gaps are **documented limitations**, not failures of the current suite.

---

## How to reproduce

```bash
# Backend — mocked AI in test profile
cd backend && ./mvnw verify

# Frontend
cd frontend && npm run lint && npm run typecheck && npm run test && npm run build
```

For local/manual verification with **real** embeddings and chat, run the backend on a non-`test` profile with Ollama (`nomic-embed-text` + configured chat model) and exercise `POST /api/ai/ask` and ingestion outside this report’s automated counts.