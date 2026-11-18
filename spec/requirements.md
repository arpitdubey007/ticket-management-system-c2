# Requirements

AI-Powered Support Ticket Management System.

Status: draft for review. This file states what the system must do. API shapes, schemas, prompts, and class design belong in the other `spec/` files.

Sources: `Requirement.pdf` (assignment), `docs/project-assessment.md`, and the rules in `.cursor/rules/`.

Keywords MUST, SHOULD, and MAY follow RFC 2119. Requirement ids (A, FR, RAG, NFR) are stable and are referenced from other specs and tests.

---

## 1. Purpose and scope

Support agents manage tickets through a web UI and a REST API. The same ticket data feeds a retrieval-augmented question-answering feature. The assistant answers one question with one grounded response and cites the tickets it used. It is a single retrieve-then-generate flow, not an autonomous agent.

In scope:

- Ticket create, list, view, and update (no delete).
- The status state machine.
- Comments.
- Keyword search and status filter.
- Persistence and backend validation.
- Meaningful UI errors.
- Ticket ingestion into a vector store.
- `POST /api/ai/ask`.

Out of scope is listed in section 8.

---

## 2. Assumptions

These decisions fill gaps in the assignment. Each can be changed by editing this file, and downstream specs must follow the change. Nothing is currently blocking.

| Id | Assumption | Why |
|---|---|---|
| A-01 | No authentication or user accounts in v1. `assignee` and comment `author` are free-text names. | Assignment does not require auth. |
| A-02 | Ticket ids are `TKT-<number>`, starting at `TKT-1001`, assigned by the backend. | Assignment examples use `TKT-1001`. |
| A-03 | Priority is one of `LOW`, `MEDIUM`, `HIGH`, `CRITICAL`. Default is `MEDIUM`. | Not specified; common convention. |
| A-04 | Category is required at creation and is one of a configured list: `PAYMENT`, `SHIPPING`, `ACCOUNT`, `TECHNICAL`, `OTHER`. It cannot be edited afterward. | Metadata needs a category; the update list excludes it. |
| A-05 | Resolution notes are required when a ticket moves to `RESOLVED` and cannot be edited afterward. | Needed to answer "What was the resolution for ticket ...?". |
| A-06 | `CLOSED` and `CANCELLED` are terminal. Field updates and comments are rejected on terminal tickets. They are allowed on `OPEN`, `IN_PROGRESS`, and `RESOLVED`. | Keeps history stable; simplest consistent rule. |
| A-07 | Limits (configurable defaults): title 1-120 characters, description 1-5000, comment 1-2000, assignee 1-100, author 1-100, resolution notes 1-2000, question 1-500, search term 1-100. Values are trimmed; blank is invalid. | Needed for testable validation. |
| A-08 | Keyword search is case-insensitive over title, description, comment text, and resolution notes. | Matches what the assistant also reads. |
| A-09 | List and search results are paginated and sorted by most recently updated first. | Bounded responses; see `api-standards`. |
| A-10 | Expected volume is up to about 1,000 tickets. The vector store is the in-memory `SimpleVectorStore`. | Project assessment decision. |
| A-11 | Ticket data uses file-backed H2 so it survives restart. In-memory H2 is allowed only in automated tests. | "Data survives application restart." |
| A-12 | The vector index is rebuilt or reconciled from the database at startup, because the in-memory store does not survive restart. | Follows A-10 and A-11. |
| A-13 | The assistant is single-turn, with no conversation memory. | "Single retrieval then generate flow." |
| A-14 | Questions are in English. | Not specified. |
| A-15 | When a question names a ticket id, that ticket's chunks are looked up directly by `ticketId` and count as context regardless of similarity score. The threshold applies only to chunks found by similarity search. | The example "What was the resolution for ticket TKT-1001?" cannot depend on embedding similarity to an id string. |

Most worth a quick check: A-04, A-05, A-06, A-11, and A-15.

---

## 3. Ticket model (conceptual)

| Field | Required | Set by | Notes |
|---|---|---|---|
| ticketId | yes | system | Immutable (A-02) |
| title | yes | user | Editable while updates are allowed (A-06) |
| description | yes | user | Editable while updates are allowed |
| category | yes | user at create | Immutable (A-04) |
| priority | yes | user | Default `MEDIUM`; editable |
| status | yes | system | Changes only through a status transition (FR-05) |
| assignee | no | user | Free text; may be cleared |
| resolutionNotes | when `RESOLVED` | user at transition | Immutable afterward (A-05) |
| comments | no | user | Append-only: author, text, timestamp |
| createdAt, updatedAt | yes | system | UTC |

---

## 4. Functional requirements

Each requirement has a user story and acceptance criteria (AC). Each AC is written so it can become a test.

### FR-01 Create a ticket

Story: As a support agent, I want to create a ticket so that an issue is tracked.

- AC1: Given valid title, description, and category, the ticket is saved with status `OPEN`, a new unique `ticketId`, and system timestamps.
- AC2: Priority defaults to `MEDIUM` when omitted. A valid explicit priority is stored as given.
- AC3: A client-supplied `ticketId`, `status`, or timestamp is not accepted and never overrides system values.
- AC4: Invalid input (missing or blank required field, over-limit text, unknown category or priority) is rejected with a validation error naming each failing field. No ticket is created.
- AC5: From the UI, I can fill in a form and see the new ticket after submit.
- AC6: Creating a ticket triggers ingestion (RAG-04).

### FR-02 List tickets

Story: As a support agent, I want to see tickets so that I can find work.

- AC1: The list is sorted by most recently updated first and returned one page at a time (A-09).
- AC2: Each row shows at least `ticketId`, title, status, priority, assignee, category, and updated time.
- AC3: With no tickets, the result is an empty page, not an error. The UI shows an empty state.
- AC4: Invalid page or size values are rejected with a validation error.

### FR-03 View ticket details

Story: As a support agent, I want to open a ticket so that I can read everything about it.

- AC1: Details include every field in section 3, all comments in chronological order, and resolution notes when present.
- AC2: An unknown `ticketId` returns a not-found error, and the UI shows a clear not-found message.
- AC3: The UI shows which status changes are currently valid. This is informational only; the backend is authoritative (FR-05).

### FR-04 Update title, description, priority, and assignee

Story: As a support agent, I want to edit a ticket so that its details stay accurate and work can be reassigned.

- AC1: I can change any of title, description, priority, and assignee, in any combination, in one request. Fields I do not send are unchanged.
- AC2: Values are validated with the same rules as creation. Invalid input changes nothing.
- AC3: An update cannot change `ticketId`, `status`, `category`, `resolutionNotes`, comments, or timestamps. Such attempts do not alter those fields.
- AC4: Assignee can be set to a new name or cleared.
- AC5: Updating a `CLOSED` or `CANCELLED` ticket is rejected with a conflict error (A-06).
- AC6: A successful update refreshes `updatedAt` and triggers re-ingestion (RAG-04).
- AC7: An unknown `ticketId` returns not-found.

### FR-05 Change ticket status (state machine)

Story: As a support agent, I want to move a ticket through its lifecycle so that its state reflects the work.

The backend MUST enforce the state machine in section 5. Status is never changed through the generic update (FR-04).

- AC1: A valid transition changes the status, refreshes `updatedAt`, and triggers re-ingestion (RAG-04).
- AC2: Any transition not in the allowed list is rejected with a conflict error stating the current status, the requested status, and the allowed next statuses. The ticket is unchanged.
- AC3: Same-status requests (for example `OPEN` to `OPEN`) are rejected.
- AC4: Moving to `RESOLVED` requires non-blank resolution notes (A-05). Without them the request is a validation error and the status is unchanged.
- AC5: `CLOSED -> OPEN`, `RESOLVED -> OPEN`, and `CANCELLED -> OPEN` are each covered by a test.
- AC6: Concurrent conflicting transitions on one ticket cannot both succeed. At most one is applied and the other is rejected.
- AC7: An unknown status value is a validation error. An unknown ticket returns not-found.

### FR-06 Add comments

Story: As a support agent, I want to comment on a ticket so that progress and context are recorded.

- AC1: Given a non-blank `author` and `text` within limits (A-07), the comment is appended and returned with a timestamp.
- AC2: Comments are append-only. Edit and delete are out of scope.
- AC3: Comments on `CLOSED` and `CANCELLED` tickets are rejected with a conflict error (A-06).
- AC4: Invalid input returns field-level errors. An unknown ticket returns not-found.
- AC5: Adding a comment refreshes `updatedAt` and triggers re-ingestion (RAG-04).

### FR-07 Search tickets by keyword

Story: As a support agent, I want to search by keyword so that I can find related issues quickly.

- AC1: A term returns tickets whose title, description, comment text, or resolution notes contain it, case-insensitively (A-08).
- AC2: Results use the same fields, order, and pagination as the list (FR-02).
- AC3: A search with no matches returns an empty page, and the UI shows a "no results" state.
- AC4: A blank or missing term behaves as no search. A term over the length limit is rejected.
- AC5: Search combines with the status filter (FR-08); both conditions apply.
- AC6: Special characters in the term are treated as plain text, not query syntax.
- AC7: Keyword search is deterministic and separate from the assistant. It does not use embeddings.

### FR-08 Filter tickets by status

Story: As a support agent, I want to filter by status so that I can focus on one stage of work.

- AC1: Filtering by one status returns only tickets in that status.
- AC2: An unknown status value is a validation error that lists the allowed values.
- AC3: With no filter, all statuses are returned.
- AC4: Filtering works with pagination and with keyword search (FR-07).

### FR-09 Persist ticket data

Story: As an operator, I want data to survive restarts so that tickets are not lost.

- AC1: After a restart, all tickets, comments, statuses, and resolution notes are present and unchanged (A-11).
- AC2: A restart test, or a documented manual check, creates, updates, restarts, and verifies the data.
- AC3: Schema changes are applied repeatably. The schema may be recreated automatically only in tests.

### FR-10 Validate input at the backend

Story: As a developer, I want the backend to be the source of truth for validation so that no client can store invalid data.

- AC1: Every endpoint that accepts input validates it server-side, regardless of UI checks.
- AC2: A failed validation returns a client-error response that identifies each invalid field and the reason, in the standard error body from `api-standards`.
- AC3: Malformed JSON, wrong types, and unknown enum values are client errors, not server errors.
- AC4: Error messages are readable by a user and expose no stack traces, SQL, or class names.
- AC5: Validation rules and limits are defined once and referenced from tests (A-07).

### FR-11 Display meaningful errors in the UI

Story: As a support agent, I want clear error messages so that I know what to fix.

- AC1: Field validation errors are shown next to the matching form fields, using the backend message.
- AC2: A rejected status transition shows the current status and the valid next statuses.
- AC3: Not-found, conflict (terminal ticket), network failure, and server failure each show a distinct, readable message. The UI never shows a blank screen or a raw error body.
- AC4: A failure of the AI feature is shown as an error and is visibly different from the "no relevant tickets" result (FR-14).
- AC5: Errors do not clear what the user already typed.

### FR-12 Ask a natural-language question over ticket history

Story: As a support agent, I want to ask questions about past tickets in plain language so that I can reuse earlier knowledge.

Endpoint: `POST /api/ai/ask` with a JSON body containing `question`. The response shape is defined in `spec/rag-api-contract.md`.

Example questions the assistant must handle:

- "Have we seen payment failures before?"
- "What was the resolution for ticket TKT-1001?"
- "What are the common causes of shipment tracking issues?"
- "Show me similar resolved tickets."
- "Which high-priority tickets are related to payment?"

- AC1: A non-blank question within limits (A-07) is answered using only retrieved ticket context (RAG-09).
- AC2: A blank, missing, or over-limit question is rejected with a validation error before any model call.
- AC3: Each example question is in the evaluation set (`spec/evaluation-strategy.md`) with the ticket ids a correct answer must cite.
- AC4: A question that names a ticket id is answered from that ticket's chunks (A-15, RAG-15a). If that ticket does not exist, the answer is the no-match response, and no details are invented.
- AC5: Questions unrelated to support tickets (for example general knowledge) return the no-match response (FR-14), not a general-knowledge answer.
- AC6: The assistant only answers. It does not create or modify tickets, send notifications, or call other tools (section 8).
- AC7: The UI provides a question box and shows the answer with its citations, or the no-match message.
- AC8: If the embedding service, vector store, or chat model is unavailable or times out, the API returns a clear service-unavailable error. It never returns a made-up answer.

### FR-13 Cite ticket sources in AI answers

Story: As a support agent, I want each answer to name its source tickets so that I can verify it.

- AC1: Every answer lists the `ticketId` of each ticket used, and each listed ticket was retrieved for this question.
- AC2: A `ticketId` that is not in the retrieved set for this request is never returned as a citation. The application checks this in code and does not rely on the model's own statement.
- AC3: If the model produces a citation that fails the check, that answer is not returned as grounded. Either the bad citation is removed or the request falls back to a defined safe response (specified in `spec/rag-api-contract.md`).
- AC4: Citations in the UI link to the ticket details view (FR-03).
- AC5: Every factual statement about a ticket in the answer text carries a citation (see `ai-grounding`).

### FR-14 Explicit no-match response

Story: As a support agent, I want an honest "nothing found" result so that I am not misled.

- AC1: If retrieval produces no qualifying context, the response text is exactly `No relevant tickets were found for this question.`, with an empty citation list and a machine-readable no-match indicator.
- AC2: In that case the chat model is not asked to produce ticket facts.
- AC3: No-match is a normal successful outcome, distinct from validation errors and service errors.
- AC4: The UI shows the no-match message as a normal result, not as an error.
- AC5: Out-of-scope questions (FR-12 AC5) produce the same no-match response.

---

## 5. Mandatory state machine

Statuses: `OPEN`, `IN_PROGRESS`, `RESOLVED`, `CLOSED`, `CANCELLED`.

Allowed transitions:

| From | To |
|---|---|
| `OPEN` | `IN_PROGRESS` |
| `OPEN` | `CANCELLED` |
| `IN_PROGRESS` | `RESOLVED` |
| `IN_PROGRESS` | `CANCELLED` |
| `RESOLVED` | `CLOSED` |

All other transitions MUST be rejected, including same-status transitions and every transition out of `CLOSED` and `CANCELLED`.

Rejected examples: `CLOSED -> OPEN`, `RESOLVED -> OPEN`, `CANCELLED -> OPEN` (named in the assignment), plus `OPEN -> RESOLVED`, `OPEN -> CLOSED`, `IN_PROGRESS -> OPEN`, `IN_PROGRESS -> CLOSED`, `RESOLVED -> IN_PROGRESS`, and `RESOLVED -> CANCELLED`.

Full matrix (A = allowed, X = rejected):

| From \ To | OPEN | IN_PROGRESS | RESOLVED | CLOSED | CANCELLED |
|---|---|---|---|---|---|
| OPEN | X | A | X | X | A |
| IN_PROGRESS | X | X | A | X | A |
| RESOLVED | X | X | X | A | X |
| CLOSED | X | X | X | X | X |
| CANCELLED | X | X | X | X | X |

Notes:

- `RESOLVED -> CANCELLED` is not in the mandatory list, so it is rejected.
- The UI may hide invalid options but is not trusted (FR-05).
- Events, guards, and error text belong in `spec/state-machine.md`.

---

## 6. RAG requirements

Flow: tickets become knowledge documents, which are chunked, embedded, and stored. A question is embedded and matched against the store. The retrieved chunks are the only context given to the model. The answer and its verified citations are returned.

### Ingestion and searchability

- RAG-01: Ticket description, every comment, and resolution notes MUST be converted into searchable knowledge documents. The title SHOULD be included so each chunk is self-explanatory.
- RAG-02: Every vector document (every chunk) MUST carry metadata with `ticketId`, `status`, `priority`, `assignee`, and `category`. An unassigned ticket MUST use a defined value such as `UNASSIGNED`, not a missing key.
- RAG-03: Chunk metadata SHOULD also record the content kind (description, comment, resolution notes) and a content hash for staleness detection (see `rag-vector-store`).
- RAG-04: A ticket create, field update, comment add, or status transition MUST trigger re-ingestion of that ticket. After re-ingestion, no chunk from an earlier version of that ticket is retrievable, and all of its chunks show the current `status`, `priority`, and `assignee`.
- RAG-05: Re-ingestion MUST NOT make a ticket write fail or roll back if the embedding service is down. A pending or failed ingestion MUST be recorded and retried, and the staleness window MUST be observable (NFR-OBS-03).
- RAG-05a: At startup the vector index MUST be reconciled with the database so every ticket is indexed exactly once (A-12). Ingestion MUST be idempotent.
- RAG-05b: The chunking strategy for ticket data (paragraph, fixed-size, or semantic) and the embedding model choice, with its cost, latency, and quality trade-off, MUST be documented and justified in `spec/architecture.md`.

### Retrieval

- RAG-06: Retrieval MUST use a configurable top-K and a configurable similarity threshold, read from configuration. Neither may be hardcoded.
- RAG-07: Chunks found by similarity search are context only if their score is at or above the threshold, up to top-K. A best hit below the threshold is not context.
- RAG-08: Changing top-K or the threshold MUST NOT need a code change. Invalid values (non-positive top-K, threshold outside the valid range) MUST fail fast at startup.

### Grounding and citations

- RAG-09: The answer MUST be grounded in retrieved ticket context. The model MUST NOT answer support-specific questions from general knowledge.
- RAG-10: Citations MUST reference real tickets. Each cited `ticketId` exists and was retrieved for this request (FR-13).
- RAG-11: If no relevant context exists, the system MUST return the explicit no-match response (FR-14), not a plausible-sounding answer.
- RAG-12: Retrieved ticket text is untrusted data. Instructions inside ticket titles, descriptions, comments, or notes MUST NOT change the assistant's rules, citation behavior, or no-match behavior.
- RAG-13: The assistant MUST distinguish facts taken from retrieved tickets from assumptions. Assumptions MUST NOT be presented as ticket facts (see `ai-grounding`).
- RAG-14: Groundedness MUST be verified by application checks (citations match the retrieved set; the no-match path is taken when retrieval is empty), not by the model's claim.
- RAG-15: Questions that depend on metadata ("high-priority", "resolved") MUST use only the `priority` and `status` values in the retrieved metadata. Applying metadata filters before retrieval is optional in v1. If it is not done, the answer MUST NOT claim completeness (for example "all high-priority payment tickets"). This limitation is documented in `spec/rag-api-contract.md`.
- RAG-15a: When the question contains a ticket id in the `TKT-<number>` format, retrieval MUST fetch that ticket's chunks by `ticketId` metadata, in addition to similarity search (A-15). An id that matches no ticket contributes no context.

### Evaluation

- RAG-16: A labeled evaluation set MUST exist covering the five example questions, at least one question that must return no-match, and at least one near-miss below the threshold. Retrieval and citation checks run against it (see `spec/evaluation-strategy.md`).
- RAG-17: The evaluation set MUST include a question naming an existing ticket id and a question naming a ticket id that does not exist (RAG-15a).

---

## 7. Non-functional requirements

Targets are assumed defaults for the v1 data size (A-10). They are configurable or revisable and should be confirmed in `spec/test-strategy.md`.

### Maintainability

- NFR-MNT-01: The backend follows the layered structure and package conventions in `.cursor/rules/java-springboot.mdc`. Controllers contain no business rules.
- NFR-MNT-02: Entities and API DTOs are separate types. Entities are never returned by the API.
- NFR-MNT-03: Configurable values (top-K, threshold, limits, model names, ingestion settings, CORS origin, file paths) live in configuration, not code.
- NFR-MNT-04: API behavior is described in `spec/api-contract.md` and OpenAPI, and the two stay consistent (see `api-standards`).
- NFR-MNT-05: The embedding model and the vector store sit behind interfaces so they can be replaced (for example by pgvector later) without changing the ticket domain.
- NFR-MNT-06: Library versions are pinned to GA releases, not release candidates or milestones.

### Security

- NFR-SEC-01: No secrets (keys, tokens, passwords) are committed to the repository, logs, tests, or docs. Secrets come from the environment or local untracked configuration.
- NFR-SEC-02: All input is validated server-side (FR-10). Database access uses parameterized queries or equivalent. Search terms are never concatenated into queries.
- NFR-SEC-03: The UI renders ticket text and AI answers as plain text or safely escaped content, so stored text cannot run scripts.
- NFR-SEC-04: Cross-origin access is limited to configured origins. No wildcard outside tests.
- NFR-SEC-05: Error responses expose no stack traces, SQL, file paths, or internal class names.
- NFR-SEC-06: The H2 web console is disabled outside local development.
- NFR-SEC-07: Prompt-injection resistance is required (RAG-12).
- NFR-SEC-08: Authentication and authorization are out of scope for v1 (A-01). This is a known gap and MUST be listed in the architecture risks before any non-local deployment.
- NFR-SEC-09: Full ticket text and full prompts are not written to application logs at info level.

### Performance

Targets for up to about 1,000 tickets on a developer laptop (A-10):

- NFR-PRF-01: List, view, search, and filter respond within 500 ms at the 95th percentile.
- NFR-PRF-02: Create, update, comment, and status change respond within 500 ms at the 95th percentile. Ingestion does not block the request or run inside the database transaction (RAG-05).
- NFR-PRF-03: Re-ingestion of one ticket completes within 30 seconds under normal conditions.
- NFR-PRF-04: Retrieval (embed the question and search) completes within 3 seconds.
- NFR-PRF-05: `POST /api/ai/ask` has a configured end-to-end timeout (default 60 seconds), because local CPU-only model inference can be slow. On timeout the API returns a service error (FR-12 AC8).
- NFR-PRF-06: Startup reconciliation of the vector index (A-12) completes within 5 minutes for 1,000 tickets, and the application reports whether the index is ready.

### Reliability

- NFR-REL-01: Ticket writes are transactional and independent of the AI services. An embedding or chat-model outage does not block create, update, comment, or status change (RAG-05).
- NFR-REL-02: Failed ingestions are retried with a bounded policy and are not silently dropped (RAG-05).
- NFR-REL-03: Calls to the embedding and chat models have timeouts. Failures return a clear service error, never a fabricated answer (FR-12 AC8).
- NFR-REL-04: A restart preserves all ticket data (FR-09) and restores a consistent index (RAG-05a).
- NFR-REL-05: Concurrent transitions on one ticket are safe (FR-05 AC6).
- NFR-REL-06: While the index is not ready (startup in progress), the ask endpoint returns a service-unavailable error rather than a misleading no-match.

### Testability

- NFR-TST-01: The state machine has an exhaustive test over the 5x5 matrix (section 5), including the three named rejection examples.
- NFR-TST-02: Service logic is unit-testable without Spring, Docker, or a live model. Embedding and chat models can be replaced by test doubles.
- NFR-TST-03: Default automated tests need no Docker. Anything that needs Docker is optional and skipped with a clear reason when unavailable (see the `testing` rule).
- NFR-TST-04: Retrieval quality is tested against the labeled evaluation set (RAG-16). Citation validation and the no-match path are deterministic and fully tested without a live model.
- NFR-TST-05: Each requirement id in sections 4 to 6 maps to at least one test or one documented manual check (section 9).
- NFR-TST-06: Tests do not depend on execution order or on data left by other tests.

### Observability

- NFR-OBS-01: Structured logs for each request include a correlation id and outcome, following `.cursor/rules/java-springboot.mdc`.
- NFR-OBS-02: Each ask request records its outcome (`ANSWERED`, `NO_MATCH`, `ERROR`), the number of chunks retrieved, and the returned chunk scores (at debug level), without full prompts or ticket bodies at info level.
- NFR-OBS-03: Ingestion exposes counts of indexed, pending, and failed tickets and the age of the oldest pending ingestion, so staleness can be seen.
- NFR-OBS-04: A health endpoint reports application status, database status, embedding service reachability, and vector index readiness.
- NFR-OBS-05: Rejected state transitions and rejected citations are logged at warn level with the ticket id and statuses involved.
- NFR-OBS-06: Active values of top-K, similarity threshold, and embedding model name are logged at startup (never secrets).

---

## 8. Out of scope

- Authentication, authorization, roles, user accounts, and multi-tenancy (A-01).
- Deleting tickets, editing or deleting comments, and editing resolution notes.
- Editing category after creation.
- Attachments, rich text, images, and file uploads.
- Email, chat, push, or other notifications, and SLA timers.
- Ticket merging, linking, duplicate detection, and bulk operations.
- Reporting, dashboards, and analytics.
- Internationalization and non-English questions (A-14).
- An autonomous assistant: no tool use, no creating or modifying tickets, no notifications, no multi-step planning or chaining, and no multi-turn memory (A-13).
- Streaming answers, feedback buttons, and answer ranking.
- Fine-tuning or training models.
- Production-scale vector search, high availability, and horizontal scaling. `SimpleVectorStore` is a development and demonstration store (A-10).
- pgvector or Chroma in v1. They remain a possible later change behind the vector-store interface (NFR-MNT-05).
- Cloud deployment, CI/CD pipelines, and infrastructure as code.
- Mobile apps.

---

## 9. Traceability to assignment acceptance criteria

| Assignment criterion | Requirement ids |
|---|---|
| Ticket can be created from UI | FR-01 |
| Tickets can be listed | FR-02 |
| Ticket details can be viewed | FR-03 |
| Ticket fields can be updated | FR-04 |
| Assignee can be changed | FR-04 AC1, AC4 |
| Comments can be added | FR-06 |
| Search works | FR-07 |
| Status filter works | FR-08 |
| Valid status transitions work | FR-05, section 5 |
| Invalid status transitions are rejected | FR-05, section 5, NFR-TST-01 |
| Data survives application restart | FR-09, A-11 |
| Backend validation works | FR-10 |
| UI shows meaningful errors | FR-11 |
| State-machine integration tests pass | NFR-TST-01 |
| Ticket data is converted into embeddings and stored | RAG-01 to RAG-05b |
| `POST /api/ai/ask` returns a grounded, ticket-sourced answer | FR-12, RAG-09 |
| Response cites specific ticket id(s) | FR-13, RAG-10 |
| No-match returns an honest response | FR-14, RAG-11 |
| Chunking and embedding model choice documented | RAG-05b |
| Re-ingestion on ticket update | RAG-04 |
| Top-K and threshold configurable | RAG-06 to RAG-08 |
| No secrets committed | NFR-SEC-01 |
| At least one AI mistake caught and documented | Section 10 |

---

## 10. Process requirements (from the assignment)

- Work follows: requirement, specification, plan and tasks, implementation, testing, review, fix. The AI is not asked to build the whole application at once.
- Every prompt is saved. `.specstory/history/` records sessions, and `docs/prompt-history.md` summarizes them.
- At least one meaningful AI mistake, in code or in a RAG answer, is recorded in `docs/ai-mistakes.md` with the correction.
- Steering files exist and stay current: `.cursor/rules/`, `.cursor/commands/` (code review, spec review, test generation, and RAG-output hallucination review), and `.cursor/skills/documentation/`.
- Token use is managed. The assignment asks for a token-optimisation plugin or MCP (for example Graphify, Caveman, or Codebase-memory MCP) and prompt caching of the static parts of the assistant's system prompt. The choice and its use are recorded in `docs/architecture-decisions.md`. Neither is a product requirement of the application.

## 11. Downstream specifications

This file feeds `spec/architecture.md`, `spec/data-model.md`, `spec/state-machine.md`, `spec/api-contract.md`, `spec/rag-ingestion.md`, `spec/rag-api-contract.md`, `spec/evaluation-strategy.md`, `spec/ui-flow.md`, and `spec/test-strategy.md`.
