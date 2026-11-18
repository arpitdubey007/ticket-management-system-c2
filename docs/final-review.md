# Final independent code and specification review

**Status:** Post-remediation submission snapshot (2026-11-18).  
**Evidence:** `cd backend && ./mvnw verify` (**63/63**), `cd frontend && npm run lint && npm run typecheck && npm run test && npm run build` (**13/13** tests, build pass). **No Ollama** in this gate.

**SpecStory transcripts:** `.specstory/history/` — indexed in `docs/prompt-history.md`.

---

## 1. Executive summary

The system implements ticket CRUD, state machine, search/filter, async RAG ingestion into `SimpleVectorStore`, and grounded ask with code-level citation checks. After fixing high-priority findings from the initial review, the **default automated suite is green**. Several assignment items remain **partially verified** (live semantic RAG, full test-strategy matrix, E2E UI).

| Severity (initial review) | Count | Post-fix status |
|---|---:|---|
| Critical | 0 | — |
| High | 8 | **5 fixed**, 1 documented waiver (H-8), 2 remain limitations (H-6 Ollama eval, partial H-5 depth) |
| Medium | 12 | **4 addressed** (M-4, M-6, M-7, M-8 partial); others documented |

---

## 2. Remediation log (confirmed fixes)

| Id | Spec | Files changed | Tests added/updated | Verified |
|---|---|---|---|---|
| **H-1** | `spec/rag-api-contract.md`, `spec/api-contract.md`, `ai-grounding.mdc` | `AskService`, `AnswerNotVerifiedException`, `GlobalExceptionHandler`, `errors.ts` | `AskServiceTest`, `AskApiIntegrationTest`, `errors.test.ts` | 502 on bad citations |
| **H-2** | `evaluation-strategy.md` §8.2, RAG-14 | `CitationValidator` | `CitationValidatorTest` | Bare `TKT-9999` rejected |
| **H-3** | FR-12 AC4, A-15 | `RetrievalService` | `RetrievalServiceTest` | No similarity when all named ids missing |
| **H-4** | NFR-OBS-03 intent, architecture §13 | `IndexReadiness`, `IngestionOutcomeWriter` (behaviour) | `IndexReadinessTest` | Failed tickets block `isReady()` |
| **H-5** | NFR-MNT-04, `api-standards.mdc` | `pom.xml` (springdoc), `OpenApiConfig`, `OpenApiParityTest` | `OpenApiParityTest` | `/v3/api-docs` paths |
| **M-4** | `java-springboot.mdc` logging | `GlobalExceptionHandler` | (manual) | ERROR log on 500 |
| **M-6** | Security / local dev | `application.yml` `local` profile | — | H2 console off |
| **M-7** | `rag-vector-store.mdc` | Rule metadata keys aligned to `ChunkMetadataKeys` | — | Rule review |
| **M-8** | requirements §10 | `docs/prompt-history.md` | — | SpecStory index |
| **H-7** | requirements §10 | `docs/architecture-decisions.md` | — | Filled |
| **H-8** | implementation-plan D-3 | `docs/architecture-decisions.md` | — | **Waiver** documented (no ETag) |

**Not fixed (documented limitations):** H-6 live Ollama evaluation harness; M-1 full test-strategy matrix; M-2 E2E UI; M-5 Actuator health; M-9 documentation skill content; M-10 embedding prefixes tuning.

---

## 3. Submission traceability

Status values: **Verified** (automated test or explicit check this session), **Partial** (fragment coverage), **Manual** (requires Ollama/runtime), **Not done**.

| Requirement / assignment item | Implementation | Test evidence | Status |
|---|---|---|---|
| FR-01 Create ticket | `TicketService`, `TicketController` | `TicketApiIntegrationTest` | Partial |
| FR-02 List / paginate | `TicketService.listTickets` | `TicketApiIntegrationTest` | Partial |
| FR-03 Detail + `allowedTransitions` | `TicketMapper` | `TicketApiIntegrationTest` | Partial |
| FR-04 Update fields | `UpdateTicketRequest`, `TicketService` | `TicketApiIntegrationTest` | Partial |
| FR-05 State machine | `TicketStateMachine`, `TicketStatusService` | `TicketStateMachineTest`, API IT fragments | Partial |
| FR-06 Comments | `CommentService` | `TicketApiIntegrationTest.addCommentPersists` | Partial |
| FR-07 Keyword search | `TicketValidator.escapeLikeLiteral` | `TicketValidatorTest`; IT-API-06 **not** full | Partial |
| FR-08 Status filter | `TicketService` | API IT fragments | Partial |
| FR-09 Persistence / restart | H2 file URL, Flyway | IT + manual | Partial |
| FR-10–FR-11 Validation / errors | `TicketValidator`, `GlobalExceptionHandler`, frontend `errors.ts` | Validator + API + Vitest | Partial |
| FR-12 Ask grounded | `AskService`, `RetrievalService` | `AskApiIntegrationTest` (mock chat) | Partial |
| FR-13 Citations | `CitationValidator`, `CitationSnippets` | Unit + API IT | **Verified** for bracket + bare id (post-fix) |
| FR-14 No-match | `AskResult.NO_MATCH_ANSWER` | `AskApiIntegrationTest`, frontend constants | **Verified** for empty retrieval |
| RAG ingestion | `IngestionProcessor`, worker | `IngestionPipelineIntegrationTest` | Partial (fake embed) |
| RAG re-ingestion | Versioned chunks, delete stale | Same IT | Partial |
| Top-K / threshold | `RagProperties`, `RetrievalService` | `RetrievalServiceTest` | **Verified** (unit) |
| Genuine embeddings | Ollama in non-test profile | **Not in CI** | **Manual** only |
| NFR-MNT-04 OpenAPI | springdoc `/v3/api-docs` | `OpenApiParityTest` | **Partial** (paths only) |
| NFR-TST-01 full SM matrix HTTP | — | Not exhaustive | Not done |
| UI workflows | `frontend/src/app/**` | Vitest only; no E2E | Partial |
| AI mistakes documented | `docs/ai-mistakes.md` | SpecStory cross-ref | **Verified** |
| Prompt history | `.specstory/history/` | `docs/prompt-history.md` | **Verified** |
| No secrets in repo | — | grep review | **Verified** |

---

## 4. Security (unchanged by design)

- No authentication (A-01).
- No secrets committed.
- H2 console disabled on `local` profile (M-6 fix).
- Prompt injection: static system prompt + citation validator; **not** fully evaluated against `evaluation-strategy.md` I-* cases without live model.

---

## 5. Known limitations (honest)

1. **`mvn verify` does not prove semantic retrieval** — `TestEmbeddingConfig` + scripted chat only (H-6).
2. **Large parts of `spec/test-strategy.md` are not automated** — see `docs/test-execution-report.md` § Gap summary.
3. **No browser E2E** against running backend.
4. **`If-Match` / ETag waived** — JPA version only (`docs/architecture-decisions.md`).
5. **Ingestion `FAILED` blocks entire assistant** until operator fixes data/Ollama — intentional after H-4 fix (may be strict for demos).
6. **OpenAPI parity test checks paths only**, not every status code and error schema.

---

## 6. Strengths (retained)

Layered backend, Flyway schema, state machine, ingestion versioning, fail-closed citation handling (now with distinct 502), Next.js UI with contract-shaped errors, Cursor rules/commands, SpecStory capture, and evidenced AI mistake log.

---

## 7. Recommended follow-up (post-submission)

1. Add optional `ollama-eval` Maven/CLI profile for `spec/evaluation-strategy.md`.
2. IT-API-06 (`%` search) and a minimal Playwright smoke test.
3. Actuator health for DB + index readiness (or explicit waiver in spec).
4. Resolve remaining `docs/spec-review.md` items (R-02 mixed named ids) if specs are formally approved.

---

## 8. Reviewer conclusion

The project is **submission-ready** as a **demonstration** with a **green default test gate** and **documented limitations**. Do **not** claim full assignment RAG quality verification without manual Ollama runs. Traceability and SpecStory references live in this file, `README.md`, and `docs/test-execution-report.md`.

**Session reference for this remediation:** [.specstory/history/2026-11-18_12-15-08Z-ai-mistakes-documentation.md](../.specstory/history/2026-11-18_12-15-08Z-ai-mistakes-documentation.md).