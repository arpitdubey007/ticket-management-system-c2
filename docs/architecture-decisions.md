# Architecture decisions

Recorded decisions for the Support Ticket Management System. When in doubt, see `spec/requirements.md` assumptions and `spec/architecture.md`.

## Runtime stack

| Decision | Choice | Rationale |
|---|---|---|
| Language / runtime | Java 21, Spring Boot 4.0.0 | Assignment and `.cursor/rules/java-springboot.mdc` |
| AI | Spring AI 2.0.2 (BOM) | GA line compatible with Boot 4 |
| Ticket DB | File-backed H2 (`jdbc:h2:file:./data/tickets`) in default/`local`; in-memory H2 in `test` profile | A-11 / test isolation |
| Vector store | In-memory `SimpleVectorStore` bean | A-10; no pgvector on H2 |
| Embeddings (production) | Ollama `nomic-embed-text` @ `http://127.0.0.1:11434` | Project assessment |
| Chat (production) | Ollama `llama3.2` | Configured in `application.yml`; not used in default CI |
| Embeddings (test) | `TestEmbeddingConfig` deterministic 768-dim vectors | `mvn verify` without Ollama |
| Chat (test) | `ScriptableTestChatModel` | Scripted replies in `AskApiIntegrationTest` |
| Frontend | Next.js App Router, `/api` rewrite to backend | Same-origin API from browser |

## Vector index lifecycle (A-12)

- **No JSON `save`/`load`** for `SimpleVectorStore` in this implementation.
- On **ApplicationReady**, `IndexReconciler` queues every ticket for re-ingestion; `IndexReadiness` blocks `POST /api/ai/ask` until pending (and failed) sets are clear.
- **Index readiness after failure:** A ticket that reaches `FAILED` ingestion stays in `IndexReadiness.failedTicketIds`, so the assistant remains unavailable until the failure is cleared (fix from `docs/final-review.md` H-4).

## RAG defaults (`application.yml`)

| Property | Default | Notes |
|---|---|---|
| `app.rag.top-k` | 5 | Configurable |
| `app.rag.similarity-threshold` | 0.5 (0.99 in test) | Configurable |
| Chunk max / overlap | 1500 / 150 | `TicketChunker` |
| Context cap | 6000 chars | `RetrievalService.trimContext` |
| Ask timeout | 60s | Virtual-thread executor in `AskService` |
| Embedding prefixes | `""` | Open point for `nomic-embed-text` tuning |

## API / grounding (post review fixes)

| Topic | Decision |
|---|---|
| Citation failure | HTTP **502** `ANSWER_NOT_VERIFIED` (not no-match text) — `spec/rag-api-contract.md` |
| No-match | HTTP 200, exact sentence `No relevant tickets found.` — empty retrieval only |
| Named missing ticket id | If question names ticket id(s) and **none** exist → empty retrieval (FR-12 AC4) |
| Citations | Bracket `[ticket:TKT-n]` required; bare `TKT-n` in prose must be in retrieved set |
| OpenAPI | springdoc `2.8.5`; parity test `OpenApiParityTest` on `/v3/api-docs` |

## Concurrency (R-19 / D-3 waiver)

- **JPA `@Version`** on `Ticket` → 409 `CONFLICT` on optimistic lock failure.
- **No `ETag` / `If-Match`** on PUT or status in v1 (documented waiver). Stale UI may get 409 after apply depending on timing.
- Comments use ticket version bump via `EntityManager` lock path in `CommentService`.

## Security (v1)

- **No authentication** (A-01).
- H2 console **disabled** even on `local` profile (M-6).
- Bind backend to localhost when demonstrating; no secrets in repository.

## Token optimisation (assignment §10)

- **No token-optimisation MCP/plugin** (Graphify, Caveman, etc.) was configured for this submission.
- **Prompt caching:** Static system prompt in `PromptBuilder.SYSTEM_PROMPT` is fixed string; not externally cached.

## SpecStory

Development prompts are captured under `.specstory/history/` (see `docs/prompt-history.md`).