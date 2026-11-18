# Test Strategy

How the system is tested, and which scenario covers each acceptance criterion.

Status: draft for review. Derived from `spec/requirements.md`, `spec/state-machine.md`, `spec/rag-ingestion.md` section 16, and `spec/evaluation-strategy.md`. This file assigns tests to levels. It contains no test code.

Detailed retrieval metrics, thresholds, and the golden dataset live in `spec/evaluation-strategy.md`. State-machine cases SM-01 to SM-19 live in `spec/state-machine.md`. Ingestion cases ING-01 to ING-20 live in `spec/rag-ingestion.md`. This file points at them instead of copying them.

## 1. Levels

| Level | Depends on | Runs | Failure means |
|---|---|---|---|
| Unit | No Spring context, no database, no Docker, no Ollama. Fakes for repositories and models | Every build | Build fails |
| Integration | Spring context, in-memory H2, the real HTTP API, fake embedding and chat models | Every build | Build fails |
| RAG evaluation | The golden dataset. Retrieval cases use the real embedding model. Answer cases use the real chat model | On demand, and before a retrieval, prompt, or model change | A metric misses its target, or a deterministic safety check fails. Does not run in the default build |

UI behavior in `spec/ui-flow.md` is checked with component tests and a manual pass of the journeys. Those checks are listed as `UI-*` in the map. They are not a fourth level.

Default automated tests need no Docker (NFR-TST-03). Anything that needs Ollama is RAG evaluation and is skipped with a stated reason when Ollama is down. It must not pass silently.

A property that can be checked with a fake model is a unit or integration test. Only behavior that depends on the real model is an evaluation (see `spec/evaluation-strategy.md` section 1).

## 2. Unit tests

JUnit 5 and Mockito, or plain constructor calls where the class has no collaborators. AssertJ for assertions. One behavior per test. No shared mutable fixtures.

### 2.1 TicketService

Use case orchestration with a fake repository and a fake ingestion writer.

| Id | Scenario | Expectation |
|---|---|---|
| UT-TS-01 | Create with title, description, and category | Status `OPEN`, a new id, timestamps set, ingestion requested |
| UT-TS-02 | Create without priority | Priority `MEDIUM` |
| UT-TS-03 | Create with an explicit priority | That priority is kept |
| UT-TS-04 | Create with a client id, status, or timestamp | Those values are ignored |
| UT-TS-05 | Update one field | Other fields unchanged, `updatedAt` moves, ingestion requested |
| UT-TS-06 | Clear assignee | Assignee becomes unset |
| UT-TS-07 | Update a `CLOSED` or `CANCELLED` ticket | Rejected, repository not written, no ingestion |
| UT-TS-08 | Update an unknown id | Not-found, no write |
| UT-TS-09 | List order and an empty list | Newest update first. Empty input returns an empty page |

### 2.2 Ticket validation

The shared rules from A-07, used by every write.

| Id | Scenario | Expectation |
|---|---|---|
| UT-VAL-01 | Blank or whitespace title, description, category, author, comment, question, or resolution notes | Rejected, field named |
| UT-VAL-02 | Each field one over its maximum length | Rejected, field named |
| UT-VAL-03 | Each field at its maximum length | Accepted |
| UT-VAL-04 | Unknown category, priority, or status | Rejected, allowed values named |
| UT-VAL-05 | Page below 0, size below 1, size above 100 | Rejected |
| UT-VAL-06 | Search term over 100 characters, and a term containing `%` | Over-long rejected. `%` kept as literal text |
| UT-VAL-07 | Malformed values the service can see (wrong enum) | Client error, not an uncaught exception |

### 2.3 State transition service

The pure transition table plus the service that applies it. The full matrix is UT-ST-01. HTTP mapping is an integration test.

| Id | Scenario | Expectation |
|---|---|---|
| UT-ST-01 | All 25 cells of the matrix in `spec/state-machine.md` | The five allowed transitions pass. The other twenty, including same-status, are rejected. The three named rejections (`CLOSED → OPEN`, `RESOLVED → OPEN`, `CANCELLED → OPEN`) are in this set |
| UT-ST-02 | `IN_PROGRESS → RESOLVED` with notes | Status `RESOLVED`, notes stored |
| UT-ST-03 | `IN_PROGRESS → RESOLVED` with missing, blank, or whitespace notes | Rejected, status and notes unchanged |
| UT-ST-04 | A rejected transition | No write and no ingestion request |
| UT-ST-05 | A successful transition | Version increments and ingestion is requested |
| UT-ST-06 | Two applies with the same version | The second is a conflict. The first result stands |

### 2.4 Comment service

| Id | Scenario | Expectation |
|---|---|---|
| UT-CM-01 | Valid author and content | Comment appended with a timestamp |
| UT-CM-02 | Blank author or content, or either over its limit | Rejected, nothing appended |
| UT-CM-03 | Comment on `CLOSED` and on `CANCELLED` | Rejected, no write |
| UT-CM-04 | Unknown ticket | Not-found |
| UT-CM-05 | Successful comment | Ticket `updatedAt` and version change, ingestion requested |
| UT-CM-06 | No update or delete operation exists | The service exposes add only |

### 2.5 RAG ingestion

Covers the chunker, id builder, and worker decisions. Embedding is a fake that records calls. These are the cases ING-01 to ING-20 in `spec/rag-ingestion.md` section 16. The ones that need a database and HTTP are repeated at integration level. The pure ones below must not need either.

| Id | Scenario | Expectation |
|---|---|---|
| UT-ING-01 | Snapshot with description, two comments, and notes | One document per source, kinds and `sourceRef` set (ING-02) |
| UT-ING-02 | Unassigned ticket | Metadata `assignee` is `UNASSIGNED`, and every required key is present (ING-03) |
| UT-ING-03 | Description under, just over, and far over the cap | Chunk counts and indexes match the rules (ING-04) |
| UT-ING-04 | Chunk text | Header, blank line, then body (ING-05) |
| UT-ING-05 | Same snapshot chunked twice | Identical ids and texts (ING-01) |
| UT-ING-06 | Priority change | New chunks carry the new priority and a new version (ING-06) |
| UT-ING-07 | Text that says "ignore previous instructions" | Stored unchanged (ING-20) |
| UT-ING-08 | Embedding failure after some chunks | Those chunks are removed and the attempt is recorded (ING-11) |

### 2.6 Retrieval service

A fake store returns scored chunks. No model.

| Id | Scenario | Expectation |
|---|---|---|
| UT-RET-01 | Scores above and below the configured threshold | Only scores at or above it are returned, at most top-K |
| UT-RET-02 | Best score below the threshold | No context, so the caller takes the no-match path and the chat model is not called |
| UT-RET-03 | Changing top-K and the threshold in configuration | The service uses the new values. No literal in the service |
| UT-RET-04 | A question containing `TKT-1001` | That ticket's chunks are included even with a low score. `TKT-9999`, which does not exist, adds nothing |
| UT-RET-05 | Chunks from an old `ingestVersion` mixed with the current one | Only the current version is returned |
| UT-RET-06 | More visible chunks than top-K after the visibility filter | The result is capped at top-K. Hidden chunks do not consume the cap |
| UT-RET-07 | Startup values of top-K or threshold outside the allowed range | Application startup fails |

## 3. Integration tests

`@SpringBootTest` with in-memory H2 and the real HTTP stack. Embedding and chat are test beans. Each test starts from an empty database (NFR-TST-06).

### 3.1 REST APIs

One success and one failure per endpoint in `spec/api-contract.md` and `spec/rag-api-contract.md`.

| Id | Scenario | Expectation |
|---|---|---|
| IT-API-01 | `POST /api/tickets` with valid and with invalid bodies | 201 with `Location`, or 400 `VALIDATION_ERROR` and no row |
| IT-API-02 | `GET /api/tickets` and `GET /api/tickets/{publicId}` | 200 list page, 200 detail, 404 for an unknown id |
| IT-API-03 | `PUT` partial update, including clearing assignee | 200, omitted fields unchanged, 400 changes nothing, 409 on a terminal ticket, 404 for an unknown id |
| IT-API-04 | `POST` comment | 201, 400, 409 on a terminal ticket, 404 |
| IT-API-05 | `GET` with `q`, `status`, `page`, and `size` | Matches are case-insensitive across title, description, comments, and notes. Combined filters use AND. No match is an empty page. Bad paging, bad status, or an over-long term is 400 |
| IT-API-06 | A search term containing `%` and `_` | Matched literally |
| IT-API-07 | `POST /api/ai/ask` with a blank question and with a question over 500 characters | 400, and the chat bean is not called |
| IT-API-08 | Ask when the fake model is set to fail, and when the index is not ready | 503 `SERVICE_UNAVAILABLE`, body is not the no-match body |
| IT-API-09 | Error JSON for 400, 404, 409, and 500 | Has `status`, `error`, `code`, `message`, `path`, and `timestamp`. No stack trace, SQL, or class name |

### 3.2 Database persistence

| Id | Scenario | Expectation |
|---|---|---|
| IT-DB-01 | Create, update, comment, and resolve | Rows match the data model: types, defaults, unique public id, comment foreign key |
| IT-DB-02 | A second ticket | The next public id, no reuse |
| IT-DB-03 | Schema | Built by the same migrations the application uses, not by Hibernate generating it (FR-09 AC3) |

### 3.3 Valid state transitions

SM-01 to SM-05 and SM-18 from `spec/state-machine.md`, through HTTP: each allowed transition returns 200, the stored status matches, and `updatedAt` moves.

### 3.4 Invalid state transitions

SM-06 to SM-16, through HTTP.

| Id | Scenario | Expectation |
|---|---|---|
| IT-ST-01 | `CLOSED → OPEN`, `RESOLVED → OPEN`, `CANCELLED → OPEN` | 400 `INVALID_TRANSITION`, stored status unchanged |
| IT-ST-02 | Every other rejected cell, including same-status | 400, row unchanged, no new ingestion request |
| IT-ST-03 | Resolve without notes, and a status of `DONE` | 400 `VALIDATION_ERROR`, status unchanged |
| IT-ST-04 | Two concurrent valid transitions | One 200 and one 409, stored status equals the winner (SM-17) |

### 3.5 Automatic re-ingestion

No separate ingest call. The test performs the ticket action and waits until the worker settles.

| Id | Scenario | Expectation |
|---|---|---|
| IT-RE-01 | Create, update, comment, and a valid transition | Each writes an ingestion row in the same transaction and ends with chunks whose metadata matches the ticket |
| IT-RE-02 | A rejected transition or a rejected update | No new ingestion request |
| IT-RE-03 | The fake embedding throws | The ticket write still commits. The ingestion row is pending or failed and is retried |
| IT-RE-04 | Two updates before the worker runs | Only the latest version is visible |

### 3.6 Restart persistence

| Id | Scenario | Expectation |
|---|---|---|
| IT-RS-01 | Create and update tickets, stop the context, start a new one on the same database file | Tickets, comments, statuses, and resolution notes are unchanged (FR-09 AC1, AC2) |
| IT-RS-02 | Ask immediately after the new start | 503 until backfill finishes, then retrieval sees the same tickets as before the restart |

IT-RS-01 uses a file-backed H2 database. The default remaining tests stay on in-memory H2.

## 4. RAG evaluation tests

These follow `spec/evaluation-strategy.md`. The six behaviors required here map onto that dataset as follows.

| Behavior | Cases | Level | Pass rule |
|---|---|---|---|
| Relevant ticket retrieval | G-01, G-03, G-05, G-06, G-07, plus recall@K | Evaluation, retrieval only | Mean recall@5 at least 0.80 and hit@5 at least 0.90, initial targets |
| Similar ticket retrieval | G-04, and the score-separation check | Evaluation | Every cited ticket is `RESOLVED` or `CLOSED`. No exact ticket list is required |
| Correct citations | G-02, G-06, citation precision, and the deterministic citation checks | Both | Returned answers never contain an id outside the retrieved set. Mean citation precision at least 0.90 |
| No-match fallback | N-01 to N-07 | Both | Exact body: `answer` is `No relevant tickets found.`, `grounded` is false, `citations` is empty. Chat model not called when retrieval is empty. True no-match rate at least 0.95 on the real model |
| Unsupported-answer rejection | G-08, PI-D1 to PI-D4, I-01 to I-05 | Both | A scripted invented id, a real but unretrieved id, or an answer with no citation is discarded and the no-match body is returned. The real model must not state a resolution for a ticket that has none |
| Stale embedding detection | S-01, S-02, IT-RE-01, UT-RET-05 | Integration is the gate. Evaluation repeats S-01 with the real embedding model | After a status or text change, retrieval returns only the new version |

A deterministic failure in this table fails the build. A missed numeric target fails the evaluation run and blocks the change that caused it (`spec/evaluation-strategy.md` section 11).

## 5. Acceptance-criterion map

Every criterion in `spec/requirements.md` sections 4 and 6 has at least one scenario. `UT` is unit, `IT` is integration, `EV` is `spec/evaluation-strategy.md`, `UI` is a UI check from `spec/ui-flow.md`.

| Criterion | Scenarios |
|---|---|
| FR-01 AC1 | UT-TS-01, IT-API-01 |
| FR-01 AC2 | UT-TS-02 |
| FR-01 AC3 | UT-TS-04, IT-API-01 |
| FR-01 AC4 | UT-VAL-01, UT-VAL-02, UT-VAL-04, IT-API-01 |
| FR-01 AC5 | UI-01 create journey |
| FR-01 AC6 | UT-TS-01, IT-RE-01 |
| FR-02 AC1 | UT-TS-09, IT-API-02 |
| FR-02 AC2 | IT-API-02, UI-02 list columns |
| FR-02 AC3 | UT-TS-09, UI-03 empty list |
| FR-02 AC4 | UT-VAL-05, IT-API-05 |
| FR-03 AC1 | IT-API-02, UI-04 details |
| FR-03 AC2 | IT-API-02, UI-05 not-found page |
| FR-03 AC3 | UI-06 only legal actions shown |
| FR-04 AC1 | UT-TS-05, IT-API-03 |
| FR-04 AC2 | UT-VAL-01, IT-API-03 |
| FR-04 AC3 | IT-API-03 |
| FR-04 AC4 | UT-TS-06, IT-API-03 |
| FR-04 AC5 | UT-TS-07, IT-API-03, UI-07 terminal banner |
| FR-04 AC6 | UT-TS-05, IT-RE-01 |
| FR-04 AC7 | UT-TS-08, IT-API-03 |
| FR-05 AC1 | UT-ST-05, IT-ST valid set, IT-RE-01 |
| FR-05 AC2 | UT-ST-01, IT-ST-01, IT-ST-02, UI-08 transition banner |
| FR-05 AC3 | UT-ST-01, IT-ST-02 |
| FR-05 AC4 | UT-ST-03, IT-ST-03, UI-09 notes required |
| FR-05 AC5 | UT-ST-01, IT-ST-01 |
| FR-05 AC6 | UT-ST-06, IT-ST-04 |
| FR-05 AC7 | UT-VAL-04, IT-ST-03, IT-API-02 |
| FR-06 AC1 | UT-CM-01, IT-API-04 |
| FR-06 AC2 | UT-CM-06, UI-10 no edit or delete |
| FR-06 AC3 | UT-CM-03, IT-API-04 |
| FR-06 AC4 | UT-CM-02, UT-CM-04, IT-API-04 |
| FR-06 AC5 | UT-CM-05, IT-RE-01 |
| FR-07 AC1 | IT-API-05 |
| FR-07 AC2 | IT-API-05 |
| FR-07 AC3 | IT-API-05, UI-11 no-results state |
| FR-07 AC4 | UT-VAL-06, IT-API-05 |
| FR-07 AC5 | IT-API-05, UI-12 combined search and filter |
| FR-07 AC6 | UT-VAL-06, IT-API-06 |
| FR-07 AC7 | IT-API-05. The test asserts the search endpoint does not call the embedding bean |
| FR-08 AC1 | IT-API-05 |
| FR-08 AC2 | UT-VAL-04, IT-API-05 |
| FR-08 AC3 | IT-API-05 |
| FR-08 AC4 | IT-API-05 |
| FR-09 AC1 | IT-RS-01 |
| FR-09 AC2 | IT-RS-01 |
| FR-09 AC3 | IT-DB-03 |
| FR-10 AC1 | IT-API-01, IT-API-03, IT-API-04, IT-API-07 |
| FR-10 AC2 | IT-API-09, UT-VAL-01 |
| FR-10 AC3 | IT-API-01, IT-API-09 |
| FR-10 AC4 | IT-API-09 |
| FR-10 AC5 | UT-VAL-02, UT-VAL-03. Limits are read from the same configuration the API uses |
| FR-11 AC1 | UI-13 field messages use the backend text |
| FR-11 AC2 | UI-08 |
| FR-11 AC3 | UI-14 distinct messages for not-found, conflict, network, and server failure |
| FR-11 AC4 | UI-15 assistant 503 looks different from no-match |
| FR-11 AC5 | UI-16 typed values remain after a 400 |
| FR-12 AC1 | EV G-01, G-06, plus UT-RET-01 |
| FR-12 AC2 | IT-API-07 |
| FR-12 AC3 | EV dataset section 3 of `spec/evaluation-strategy.md` |
| FR-12 AC4 | UT-RET-04, EV G-02, N-05, N-06 |
| FR-12 AC5 | EV N-01, N-02, N-03 |
| FR-12 AC6 | IT-API-08 plus a test that ask writes no ticket row. UI-17 drawer has no create or edit action |
| FR-12 AC7 | UI-18 question box, answer, citations |
| FR-12 AC8 | IT-API-08, UI-15 |
| FR-13 AC1 | UT citation builder, EV citation precision |
| FR-13 AC2 | UT with a scripted invented id (PI-D1, PI-D2) |
| FR-13 AC3 | PI-D1, PI-D3. The response is the no-match body |
| FR-13 AC4 | UI-19 citation links to `/tickets/[ticketId]` |
| FR-13 AC5 | EV section 8 of `spec/evaluation-strategy.md` |
| FR-14 AC1 | IT with an empty retrieval, EV N-01 |
| FR-14 AC2 | UT-RET-02 |
| FR-14 AC3 | IT asserts HTTP 200 for no-match and 4xx or 503 for errors |
| FR-14 AC4 | UI-20 informational style, not an error |
| FR-14 AC5 | EV N-01, IT with a scripted empty retrieval |
| RAG-01 | UT-ING-01 |
| RAG-02 | UT-ING-02 |
| RAG-03 | UT-ING-01, UT-ING-02 |
| RAG-04 | IT-RE-01, UT-RET-05, EV S-01 |
| RAG-05 | IT-RE-03 |
| RAG-05a | ING-15, ING-16, IT-RS-02, UT-ING-05 |
| RAG-05b | Review check that `spec/architecture.md` sections 8 and 9 stay present. Not a runtime test |
| RAG-06 | UT-RET-01, UT-RET-03 |
| RAG-07 | UT-RET-02 |
| RAG-08 | UT-RET-03, UT-RET-07 |
| RAG-09 | EV section 8 |
| RAG-10 | FR-13 AC2 scenarios |
| RAG-11 | FR-14 AC1 scenarios |
| RAG-12 | UT-ING-07, EV I-01 to I-05 |
| RAG-13 | EV section 8.1 |
| RAG-14 | PI-D4. `grounded` is asserted from the server, not from the scripted reply |
| RAG-15 | EV G-04, G-05. The answer must not claim the list is complete |
| RAG-15a | UT-RET-04 |
| RAG-16 | EV dataset coverage check: five example questions, one no-match, one below-threshold near-miss |
| RAG-17 | EV G-02, N-06 |

NFR-TST-01 is UT-ST-01 and IT-ST-01. NFR-TST-02 is the unit section. NFR-TST-04 is section 4. NFR-TST-05 is this table. NFR-TST-06 is the empty-database rule in section 3.

## 6. What is not tested in v1

- Load or performance numbers beyond a single documented check of the list endpoint against NFR-PRF-01 when the corpus exists.
- Browser automation across desktop and narrow widths (UI-A3).
- A live cloud embedding provider.
- Authentication, which is out of scope (A-01).
