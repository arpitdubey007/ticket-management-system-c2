# RAG Ingestion

How ticket data becomes searchable vectors, and how the index is kept correct.

Status: draft for review. Derived from `spec/requirements.md` (RAG-01 to RAG-08), `spec/architecture.md` (sections 7 to 11), `spec/data-model.md` (sections 6 and 7), and `.cursor/rules/rag-vector-store.mdc`. This file describes design only. Nothing here is implemented, and no pipeline code exists.

Ids: `ING-nn` are decisions made here. `OD-nn` are open decisions carried from `spec/architecture.md`.

Pipeline:

```mermaid
flowchart LR
  T[Ticket data in H2] --> K[Knowledge documents]
  K --> C[Chunking]
  C --> E[Embeddings via Ollama]
  E --> V[SimpleVectorStore]
```

---

## 1. Principles

- The database is the source of truth. The vector index can always be rebuilt from it (A-12).
- Ingestion never blocks or fails a ticket write (RAG-05). It runs after the write commits.
- Ingestion is idempotent. Repeating it never creates duplicates (RAG-05a).
- A ticket is always ingested as a whole. Any change re-ingests all of its chunks, so metadata never disagrees with the database (RAG-04).
- Retrieval sees a ticket only through its last fully indexed version (section 10).
- Retrieved ticket text is untrusted data. Ingestion stores it as written and does not interpret it (RAG-12).

---

## 2. Stage 1: ticket data to knowledge documents

A knowledge document is one searchable unit of a ticket. A ticket yields up to three kinds (RAG-01).

The worker reads the ticket row and its comments in one read transaction and records `tickets.version` as the snapshot version N (data model 7.4). No model call is made inside that transaction.

### 2.1 Description extraction

| Item | Rule |
|---|---|
| Source | `tickets.description` |
| Kind | `DESCRIPTION` |
| `sourceRef` | `description` |
| Always produced | Yes. Description is required (A-07). |

### 2.2 Comment extraction

| Item | Rule |
|---|---|
| Source | One document per row in `comments`, ordered by `created_at`, then `id`. |
| Kind | `COMMENT` |
| `sourceRef` | `comments.id` |
| Author | `comments.author` is shown in the chunk header, not in the body. |
| Empty ticket | A ticket with no comments produces no comment documents. |

Comments are append-only (A-06, FR-06 AC2), so a comment's text never changes. Its chunks are still rebuilt when the ticket changes, because the ticket metadata on them changes.

### 2.3 Resolution note extraction

| Item | Rule |
|---|---|
| Source | `tickets.resolution_notes` |
| Kind | `RESOLUTION_NOTES` |
| `sourceRef` | `resolution` |
| Produced when | The column is not `NULL`. By the data model constraint this is the case exactly when status is `RESOLVED` or `CLOSED`. |

### 2.4 Text normalization

Applied to every body before chunking:

1. Convert line endings to `\n`.
2. Trim leading and trailing whitespace.
3. Collapse three or more consecutive newlines to two.
4. Do not strip, rewrite, or "clean" content otherwise. Do not remove text that looks like instructions (RAG-12).
5. Skip a document whose body is empty after trimming. This cannot happen for valid data and is logged at warn level if it does.

The title is not a separate document. It is repeated in the header of every chunk (section 3).

---

## 3. Stage 2: chunking

Chunking follows the strategy in `spec/architecture.md` section 9: structure first, paragraph fallback.

### 3.1 Rules

| Rule | Value |
|---|---|
| Unit | Each knowledge document (section 2) is chunked on its own. Chunks never span two documents or two tickets. |
| Size cap | `app.rag.chunk.max-chars`, initial value 1500, applied to the body only. The header is added after splitting. |
| Short documents | A body at or under the cap is one chunk, `chunkIndex` 0. |
| Long documents | Split on blank lines (paragraph boundaries). Pack whole paragraphs into a chunk while it stays under the cap. |
| Long paragraph | A single paragraph over the cap is split on sentence ends. |
| Long sentence | A single sentence over the cap is split by length with `app.rag.chunk.overlap-chars` (initial 150) of overlap. This is the last resort. |
| Tiny tail | A final chunk under 100 characters is merged into the previous chunk if the result stays within 1.2 times the cap. |
| Order | Chunks keep source order. `chunkIndex` starts at 0 and increases by 1 within one document. |
| Determinism | The same input and the same configuration always give the same chunks. No randomness, no model calls in chunking. |

With the limits in A-07, a comment (2,000) or resolution note (2,000) yields at most two chunks. A description (5,000) yields at most about four.

### 3.2 Chunk text format

The text that is embedded is a header block, one blank line, then the body.

```text
Ticket: <title>
Category: <category>
Type: <Description | Comment by <author> | Resolution notes>

<body>
```

Rules:

- The header ends at the first blank line. Everything after it is the body. The body may itself contain blank lines.
- `ticketId`, `status`, `priority`, and `assignee` are not in the embedded text. They are metadata only (architecture section 9.2, item 4).
- The citation snippet in `spec/rag-api-contract.md` is taken from the body part only, never from the header.
- The header is built from the snapshot, so a title edit changes the text of every chunk of that ticket and is picked up by the whole-ticket re-ingestion.

---

## 4. Metadata requirements

Every chunk carries all of these keys (RAG-02, RAG-03). A key is never missing. Values are strings except where noted.

| Key | Value | Source |
|---|---|---|
| `ticketId` | Public id, for example `TKT-1001` | `tickets.public_id` |
| `status` | `OPEN`, `IN_PROGRESS`, `RESOLVED`, `CLOSED`, or `CANCELLED` | `tickets.status` |
| `priority` | `LOW`, `MEDIUM`, `HIGH`, or `CRITICAL` | `tickets.priority` |
| `assignee` | The name, or the fixed value `UNASSIGNED` when `NULL` | `tickets.assignee` |
| `category` | Category | `tickets.category` |
| `kind` | `DESCRIPTION`, `COMMENT`, or `RESOLUTION_NOTES` | Section 2 |
| `sourceRef` | `description`, `resolution`, or the comment id as text | Section 2 |
| `chunkIndex` | Integer position inside its document | Section 3 |
| `ingestVersion` | Integer, the snapshot `tickets.version` N | Section 2 |
| `contentHash` | SHA-256 of the body text, hex | Section 3.2 |
| `title` | Ticket title | `tickets.title` |

Constraints:

- No secrets or credentials are written to chunk text or metadata (NFR-SEC-01).
- `assignee` is never an empty string. A literal assignee named `UNASSIGNED` would be indistinguishable from none, so that name is rejected as reserved by ticket validation (see DQ-ING-01).
- Metadata values are simple strings and numbers so any vector store can filter on them.

---

## 5. Document ID strategy

Each chunk id is:

```text
<ticketId>:<ingestVersion>:<kind>:<sourceRef>:<chunkIndex>
```

Examples:

```text
TKT-1001:3:DESCRIPTION:description:0
TKT-1001:3:COMMENT:55:0
TKT-1001:3:RESOLUTION_NOTES:resolution:1
```

Properties:

- Deterministic. The same ticket version and settings always produce the same ids. Adding a chunk with an existing id replaces it, so a repeated run cannot duplicate chunks (ING-01).
- Versioned. A new ticket version produces a new set of ids, so new chunks can be added next to the old ones before the old ones are removed (section 9).
- Readable in logs and tests without exposing ticket text.
- If the store is ever replaced by one that needs UUID ids (pgvector uses UUIDs), the id becomes a name-based UUID computed from this string. The string stays the logical id.

---

## 6. Stage 3: embeddings

| Item | Decision |
|---|---|
| Model | `nomic-embed-text` on Ollama, set with `spring.ai.ollama.embedding.model` (architecture AD-04, section 8). |
| Dimensions | Determined by the model (768 for `nomic-embed-text`). One store holds vectors from one model only. |
| What is embedded | The full chunk text, header and body (section 3.2). |
| Queries | A question is embedded with the same model. The ask flow is in `spec/architecture.md` section 11. |
| Task prefixes | Open (OD-03). If the model needs a prefix for documents and a different one for queries, the values are configuration (`app.rag.embedding.document-prefix`, `app.rag.embedding.query-prefix`), empty until the evaluation shows they help. The same values must be used for every ingestion and every query in a run. |
| Context limit | Ollama truncates inputs over the model's context by default (`truncate: true`). With a 1500-character cap plus a header, chunks are far below the 2K-token context. Truncation is treated as a warning, not as silent normal behavior (section 11). |
| Model changes | Changing the model or prefixes needs a full rebuild. A restart does this automatically (section 12). |
| Calls | The store embeds each chunk through the Spring AI `EmbeddingModel`. In the Spring AI source reviewed, `SimpleVectorStore.add` embeds each document with its own call, so the worker should expect one call per chunk. If measured rebuild time misses NFR-PRF-06 (OD-05), options are a smaller model, or computing embeddings in batches and supplying them to the store through the index adapter. |

---

## 7. Stage 4: vector-store configuration

| Item | Decision |
|---|---|
| Store | Spring AI `SimpleVectorStore`, one instance, created as an explicit bean from the `EmbeddingModel` (architecture AD-03). |
| Similarity | Cosine. Scores range 0 to 1 for the threshold. A chunk qualifies when its score is greater than or equal to the threshold. |
| Persistence | None. The store is in memory and is rebuilt at startup. |
| Concurrency | One writer (the ingestion worker thread). Many readers (ask requests). The store uses a concurrent map. |
| Metadata filtering | Equality on `ticketId`, used for direct ticket lookup and for removing a ticket's old chunks. Whether delete-by-filter is supported in the pinned version is open (OD-04). If not, the index adapter keeps its own map from `ticketId` to chunk ids and deletes by id. |
| Scope | The ticket domain never touches the store. Only `KnowledgeIndex` does (architecture section 3.1). |
| Not production grade | Retrieval scans every vector. This is accepted for about 1,000 tickets (A-10). |

---

## 8. Update-triggered re-ingestion

| Trigger | Requirement | Where the request is written |
|---|---|---|
| Ticket created | FR-01 AC6 | Same transaction as the insert |
| Fields updated (title, description, priority, assignee) | FR-04 AC6 | Same transaction as the update |
| Comment added | FR-06 AC5 | Same transaction. Also increments `tickets.version`. |
| Status changed, including resolve, close, and cancel | FR-05 AC1 | Same transaction as the transition |
| Application start | A-12 | Startup reconciliation (section 12) |

Not triggers: reads, searches, list requests, and rejected writes. A rejected write changes nothing, so it adds no request (state-machine scenario SM-19).

Mechanism (data model section 6.4):

1. In the same transaction as the ticket change, the service increments `tickets.version` and upserts the ticket's `ticket_ingestion` row: `requested_version` set to the new version, status `PENDING`, attempts reset, `next_attempt_at` set to now. If the row was `INDEXED`, `requested_at` is set to now. If it was already pending or failed, the earlier `requested_at` is kept so the staleness age stays honest.
2. After commit the worker is woken. It also polls at `app.rag.ingestion.poll-interval` as a safety net.
3. If several changes arrive before the worker runs, only the latest version is ingested. Intermediate versions are skipped, because each ingestion reads current data.

---

## 9. Delete-and-replace strategy

Goal: at no moment is a ticket missing from search because of an update, and at no moment are two versions of it visible.

For snapshot version N of ticket T:

| Step | Action | If it fails |
|---|---|---|
| 1 | Read the snapshot and build chunks with `ingestVersion` N. | Retry the whole job (section 11). |
| 2 | Add every chunk to the store. Ids include N, so they do not collide with chunks of earlier versions. | Delete any chunks of T with `ingestVersion` N that were added, record the failure, retry later. Earlier versions stay visible. |
| 3 | In one database transaction, set `ticket_ingestion.indexed_version` to N and `indexed_at` to now. Set status `INDEXED` if `requested_version` is still N. Otherwise leave it `PENDING` for the newer version. Clear `last_error`. | Treat as a failed job. Chunks of version N are not yet visible (section 10), so nothing is exposed. Retry. |
| 4 | Delete all chunks of T whose `ingestVersion` is not N. | The old chunks are already invisible. Log at warn level. The next ingestion of T or a restart removes them. |

Because visibility is decided in step 3 (the database commit) and not by the store contents, the switch from the old version to the new version is one atomic change.

Add-before-delete is deliberate. Delete-before-add would leave T with no chunks while the new embeddings are computed, which on CPU-only Ollama could be several seconds.

---

## 10. Visibility rule

A chunk is returned from retrieval only if both are true:

1. Its ticket exists.
2. Its `ingestVersion` equals `ticket_ingestion.indexed_version` for that ticket.

Retrieval applies this after the vector search and before the threshold and top-K cut, using one database query for the candidate ticket ids. To keep invisible chunks from taking slots, the search fetches `top-K × app.rag.retrieval.candidate-multiplier` candidates (initial 3), then filters, then keeps up to top-K (section 14).

This one rule prevents three problems:

- A stale version cannot be returned, even before step 4 of section 9 has run.
- A half-written new version (an embedding failure partway through step 2) cannot be returned, because step 3 never ran.
- Leftover chunks after a crash cannot be returned.

It also means a restart is safe: after reconciliation `indexed_version` is `NULL` until the ticket is indexed in this run, so a ticket has no visible chunks until then (section 12).

---

## 11. Failed ingestion handling

Ticket writes are never affected by any failure in this section (RAG-05, NFR-REL-01).

| Failure | Handling |
|---|---|
| Ollama unreachable or the call times out | Transient. Record the failure and back off. |
| Model not pulled yet | Transient. The startup pull strategy (`when_missing`) normally prevents it. Same handling as above. |
| Embedding call fails for one chunk | The whole job fails. Remove the partial chunks of version N (section 9, step 2) and back off. |
| Input was truncated by the model | Log at warn level with ticket id and chunk id. The chunk is kept. If the evaluation set ever shows truncation, lower the chunk cap. |
| Chunker or data error for one ticket | Counted as a failed attempt. It does not stop other tickets. |
| Database error recording success | Treated as a failed job (section 9, step 3). |

Retry policy (all keys under `app.rag.ingestion`):

- Each failure increments `attempt_count`, stores a short sanitized `last_error` (no ticket text, no secrets), and sets `next_attempt_at` using exponential backoff: initial 5 seconds, multiplier 2, maximum 5 minutes.
- After `max-attempts` (initial 5) the row is set to `FAILED` and `next_attempt_at` is cleared. It is not dropped (NFR-REL-02).
- One failing ticket never blocks others. The worker takes the next due row.
- A `FAILED` ticket is retried when it is next changed, which resets its row, or at the next restart, which resets all rows (section 12). There is no admin retry endpoint in v1.
- A failed ticket has no visible new version. If it was indexed earlier in this run, its previous version stays visible. If it was never indexed in this run, it is not searchable, and the health check reports this as degraded.
- Failures are logged at warn level with ticket id, attempt, and error class. Metrics report counts of indexed, pending, and failed tickets and the age of the oldest pending request (NFR-OBS-03).

---

## 12. Existing-ticket backfill

The same mechanism covers three situations.

| Situation | Handling |
|---|---|
| First run after this feature exists, and tickets are already in the database | No `ticket_ingestion` rows exist. Startup reconciliation creates one for every ticket and queues it. |
| Every restart | The in-memory index is empty. Reconciliation resets every row (including `FAILED`): `requested_version` set to the current version, `indexed_version` to `NULL`, status `PENDING`, attempts to 0, `next_attempt_at` to now. |
| Model, prefix, or chunking configuration changed | Take effect on the next start, because every start rebuilds. No separate migration is needed. |

Order and progress:

- Rows are processed oldest `requested_at` first, then by ticket id. The order does not affect correctness.
- The ask endpoint returns 503 until the backfill finishes (NFR-REL-06). The backfill is finished when no row from the startup run is still `PENDING`. `FAILED` rows do not hold readiness back, because one bad ticket must not disable question answering. They are reported as degraded.
- New tickets and changes made during the backfill queue behind it and are handled by the same worker.
- Progress is logged at info level as counts only: indexed, pending, failed, and elapsed time.
- Expected duration is unknown until measured (OD-05). The target is 5 minutes for about 1,000 tickets (NFR-PRF-06).

There is no live "re-index everything" operation in v1. A restart is the rebuild.

---

## 13. How duplicate and stale vector documents are prevented

### 13.1 Duplicates

| Mechanism | What it stops |
|---|---|
| Deterministic chunk ids (section 5) | A repeated job writes the same ids, which replaces rather than adds. |
| Single writer thread | Two jobs for one ticket cannot interleave. |
| One row per ticket in `ticket_ingestion` | One current request per ticket. |
| Cleanup of other versions (section 9, step 4) | Old versions do not accumulate. |
| Visibility rule (section 10) | Even if a duplicate or old-version chunk exists, it is not returned. |
| Rebuild at start | Every run starts from an empty store. |
| Single application instance | The in-memory store belongs to one process. Running two instances is not supported (A-10). |

### 13.2 Stale documents

| Cause | Prevention |
|---|---|
| Ticket text or metadata changed | Every change increments `version` and queues re-ingestion in the same transaction (RAG-04). |
| Ingestion lags behind | Old text stays visible only until step 3 of section 9, bounded by NFR-PRF-03 (30 seconds under normal conditions). The staleness is observable (NFR-OBS-03), and every cited ticket links to the ticket page, which is the source of truth (FR-13 AC4). |
| Update during ingestion | The success step marks the row `INDEXED` only if the requested version is still the version just indexed. Otherwise it stays `PENDING` and runs again. |
| Failed or partial ingestion | New chunks become visible only after the commit in step 3. A failure leaves the previous version visible. |
| Crash after add, before commit | The store is in memory and rebuilt on restart. Even without a restart, the new chunks are invisible. |
| Removed ticket | Not possible in v1 (no deletion). If deletion is added, its chunks must be removed with it and the visibility rule already hides them (data model DQ-02). |
| Embedding model change | Rebuild at start. Mixed-model stores cannot occur. |

---

## 14. Retrieval parameters

Retrieval is specified in `spec/architecture.md` section 11. These parameters are ingestion-related because they define which chunks reach the model. All are configuration, loaded at startup, and never hardcoded (RAG-06, RAG-08).

| Key | Initial value | Rule |
|---|---|---|
| `app.rag.top-k` | 5 | Integer from 1 to 50. The maximum number of chunks used as context from similarity search. |
| `app.rag.similarity-threshold` | 0.5 | Number from 0 to 1 inclusive. Chunks scoring below it are not context, even when they are the best hit (RAG-07). Tuned with the evaluation set (OD-02). |
| `app.rag.retrieval.candidate-multiplier` | 3 | Integer from 1 to 10. Over-fetches before the visibility filter (section 10). |

An invalid value stops startup with a clear message (RAG-08). The active top-K, threshold, and embedding model name are logged at startup (NFR-OBS-06). A change needs a restart and no code change.

Tickets named in the question are fetched by `ticketId` and count as context regardless of the threshold (A-15, RAG-15a). Their chunks still pass the visibility rule.

---

## 15. Configuration keys introduced or used here

| Key | Initial value | Notes |
|---|---|---|
| `app.rag.chunk.max-chars` | 1500 | Body cap (section 3). |
| `app.rag.chunk.overlap-chars` | 150 | Last-resort length split only. |
| `app.rag.embedding.document-prefix` | empty | Open (OD-03). |
| `app.rag.embedding.query-prefix` | empty | Open (OD-03). |
| `app.rag.retrieval.candidate-multiplier` | 3 | Section 10. |
| `app.rag.ingestion.poll-interval` | 2s | |
| `app.rag.ingestion.max-attempts` | 5 | |
| `app.rag.ingestion.backoff` | initial 5s, multiplier 2, max 5m | |
| `spring.ai.ollama.embedding.model` | `nomic-embed-text` | |
| `spring.ai.ollama.init.pull-model-strategy` | `when_missing` | |

---

## 16. Deterministic test scenarios

These use a fake embedding model and in-memory H2, with no Ollama and no Docker (NFR-TST-02, NFR-TST-03). Retrieval quality is covered in `spec/evaluation-strategy.md`.

| Id | Scenario | Expected |
|---|---|---|
| ING-01 | Ingest the same ticket version twice | The index has the same chunk ids and the same count. No duplicates. |
| ING-02 | Ticket with a description, two comments, and resolution notes | One chunk set per source, each with `kind` and `sourceRef` as specified. |
| ING-03 | Every chunk of every test ticket | All keys in section 4 are present. An unassigned ticket has `assignee` `UNASSIGNED`. |
| ING-04 | Description at, just over, and far over the cap | One chunk, two chunks, and several chunks, with sequential `chunkIndex` and no chunk over the cap. |
| ING-05 | Chunk text | Starts with the header, then a blank line, then the body. The snippet source excludes the header. |
| ING-06 | Update priority, then ingest | New chunks carry the new priority. After step 3 the old version is not returned. |
| ING-07 | Add a comment, then ingest | A new comment chunk exists and `tickets.version` increased. |
| ING-08 | Change status to `RESOLVED` with notes, then ingest | Resolution chunks appear and every chunk shows the new status. |
| ING-09 | Two changes before the worker runs | Only the latest version is ingested. |
| ING-10 | A change arrives during ingestion | The row stays `PENDING`, then a second run indexes the newer version. |
| ING-11 | Embedding fails midway | Partial chunks are removed. The previous version stays visible. Attempts and `last_error` are recorded. The ticket write is unaffected. |
| ING-12 | Failures reach the maximum attempts | The row is `FAILED`. Other tickets still ingest. |
| ING-13 | Crash after add and before commit (simulated) | The new chunks are not returned by retrieval. |
| ING-14 | Delete of old chunks fails | The old chunks are not returned. Warn is logged. |
| ING-15 | Startup reconciliation with existing tickets and no rows | Every ticket gets a row and is queued. The ask path reports not ready until done. |
| ING-16 | Reconciliation with `FAILED` rows | They are reset and retried. A permanently failing ticket does not block readiness. |
| ING-17 | Stale and current chunks of one ticket both present | Retrieval returns only the chunks matching `indexed_version`. |
| ING-18 | Invalid top-K or threshold in configuration | Startup fails with a clear message. |
| ING-19 | Ollama down while a ticket is created | The create succeeds. Ingestion is pending and retried. |
| ING-20 | A ticket whose text contains an instruction such as "ignore previous instructions" | The text is stored and chunked unchanged. |

---

## 17. Open items

| Id | Item |
|---|---|
| OD-03 | Whether `nomic-embed-text` needs document and query prefixes (section 6). |
| OD-04 | Whether `SimpleVectorStore` supports delete by metadata filter (section 7). |
| OD-05 | Measured rebuild time on CPU-only Ollama in Docker (section 12). |
| DQ-ING-01 | Whether ticket validation should reserve the assignee value `UNASSIGNED`. Default: yes. Settle in `spec/api-contract.md`. |

---

## 18. Traceability

| Requirement | Where addressed |
|---|---|
| RAG-01 | Section 2 |
| RAG-02, RAG-03 | Section 4 |
| RAG-04 | Sections 8, 9 |
| RAG-05 | Sections 1, 8, 11 |
| RAG-05a | Sections 5, 12, 13 |
| RAG-05b | `spec/architecture.md` sections 8, 9, and sections 3, 6 here |
| RAG-06, RAG-07, RAG-08 | Section 14 |
| RAG-12 | Sections 1, 2.4 |
| NFR-REL-01, NFR-REL-02 | Section 11 |
| NFR-REL-04, NFR-REL-06 | Section 12 |
| NFR-OBS-03, NFR-OBS-06 | Sections 11, 14 |
