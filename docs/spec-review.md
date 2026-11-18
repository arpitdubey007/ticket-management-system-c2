# Specification Review

Status: findings and proposals only. **No specification, rule, or steering file has been changed by this review.** Corrections are applied only after approval (section 11).

Scope: every file under `spec/` (ten files), compared with the assignment in `Requirement.pdf`. Because they guide implementation, the five `.cursor/rules/*.mdc` files and the placeholder artefacts under `.cursor/commands/`, `.cursor/skills/`, and `docs/` were also checked.

Reading guide: ids such as `FR-05 AC6`, `RAG-15a`, `A-15`, `AD-08`, `OD-04`, `DM-04`, `SM-17`, `ING-13`, and `UT-ST-06` refer to the specifications. Each issue has the same four parts: affected specification, problem, impact, proposed correction.

---

## 1. Summary

| Severity | Meaning | Count |
|---|---|---|
| High | Two approved specs or a rule contradict each other, or a requirement cannot be implemented or verified as written. Resolve before coding the affected area. | 5 |
| Medium | A gap or ambiguity that two developers would resolve differently, or a missing test. Resolve before the affected area is built. | 24 |
| Low | Editorial, cross-reference, or minor rule drift. Safe to batch. | 13 |

Highest-priority items:

1. **R-01** A rejected or "I don't know" model answer is reported as "No relevant tickets found."
2. **R-02** Questions that name a ticket id: three specs disagree about what happens.
3. **R-03** Citation validation does not enforce what the evaluation spec says it enforces.
4. **R-19** The optimistic-locking design has no client input, and comments can collide with each other.
5. **R-39** `.cursor/rules/rag-vector-store.mdc` would make the implementation violate the approved RAG specs.

Items that need a decision from you rather than a mechanical fix are collected in section 10.

---

## 2. What was checked and found consistent

These cross-checks passed, so they do not need correction:

- **Assignment coverage.** Every functional, RAG, and acceptance item in the assignment maps to a requirement id in `spec/requirements.md` section 9 (traceability), and every `FR-*` and `RAG-*` acceptance criterion appears in the `spec/test-strategy.md` section 5 map.
- **State machine.** The five allowed transitions, the 20 rejected cells, HTTP 400 `INVALID_TRANSITION`, and the 409 cases agree across `requirements.md`, `state-machine.md`, `api-contract.md`, `architecture.md`, `test-strategy.md`, `ui-flow.md`, and `api-standards.mdc`.
- **Entity fields.** Ticket field names, types, and length limits agree between `api-contract.md` and `data-model.md` (except the items listed below).
- **RAG metadata.** The required keys (`ticketId`, `status`, `priority`, `assignee`, `category`, plus `kind`, `sourceRef`, `chunkIndex`, `ingestVersion`, `contentHash`, `title`) agree across `architecture.md` section 7, `data-model.md` section 7, and `rag-ingestion.md` section 4. Only the steering rule differs (R-39).
- **No-match body.** The exact text `No relevant tickets found.`, HTTP 200, `grounded:false`, and empty `citations` agree across `requirements.md`, `rag-api-contract.md`, `architecture.md`, `ui-flow.md`, `test-strategy.md`, and `ai-grounding.mdc`. The issue is when the body is used (R-01), not its shape.
- **Ingestion visibility rule.** The order "add new chunks, commit `indexed_version`, delete old chunks" and the visibility rule agree across `architecture.md` section 10, `rag-ingestion.md` sections 9 and 10, and `data-model.md` section 6.

---

## 3. Findings: API, architecture, and RAG agreement

### R-01 [High] A rejected or "insufficient context" answer is reported as "No relevant tickets found."

- **Affected specs:** `rag-api-contract.md` (citation failure rules), `architecture.md` sections 11.2 and 12, `requirements.md` FR-14 and RAG-14, `evaluation-strategy.md` sections 9 and 10 (PI-D1 to PI-D4), `test-strategy.md` section 4, `.cursor/rules/ai-grounding.mdc`.
- **Problem:** FR-14 defines the no-match response as the answer to "no qualifying context". The specs also return the same body when retrieval found qualifying tickets but the model's answer failed citation validation (log outcome `CITATION_REJECTED`). The same happens when the model says the context does not answer the question, because that reply carries no valid citation. In those cases the client is told no relevant tickets exist when relevant tickets were retrieved. `ai-grounding.mdc` check 4 limits the fallback to an empty or below-threshold retrieval, so the rule and the specs disagree.
- **Impact:** Users get a false statement and cannot tell a model failure from a true no-match. Evaluation metrics are skewed: the true no-match rate and the false no-match rate on positive questions mix model failures into retrieval results. At API level the two cases can only be told apart in logs, so a deterministic test cannot assert the difference.
- **Proposed correction:**
  1. Have the model return either a cited answer or a fixed sentinel (for example `NO_ANSWER`). `NO_ANSWER` maps to the exact no-match body, since the model judged the context irrelevant. This keeps the no-match body honest.
  2. A cited-answer attempt that fails validation (no valid citation, unknown id, bare invented id) is not reported as no-match. Return HTTP 502 with `code: ANSWER_NOT_VERIFIED` and the standard error body. One automatic retry may be allowed before returning it.
  3. Add `ANSWER_NOT_VERIFIED` to the error table in `api-contract.md`, `rag-api-contract.md`, and `ui-flow.md` (a distinct message, not the informational no-match style), and add it as an ask outcome in NFR-OBS-02.
  4. Update PI-D1 to PI-D4 and the "Unsupported-answer rejection" row of `test-strategy.md` section 4 to expect the new error.
  5. Update `ai-grounding.mdc` to describe both paths.
- **Decision needed:** D-1.

### R-02 [High] Questions that name a ticket id: three specs disagree

- **Affected specs:** `requirements.md` A-15, FR-12 AC4, RAG-15a; `rag-api-contract.md` (direct lookup rule); `evaluation-strategy.md` G-02, N-05, N-06; `test-strategy.md` UT-RET-04.
- **Problem:**
  - FR-12 AC4 says the question is "answered from that ticket's chunks", and that a nonexistent ticket gives the no-match response.
  - RAG-15a says direct lookup happens "in addition to similarity search". Similarity results can therefore also reach the model and be cited.
  - G-02 expects a citation of the named ticket only. N-05 (`TKT-9999`) accepts either `NO_MATCH` or an answer citing `TKT-1011`, which contradicts the AC4 requirement of the no-match response for a nonexistent ticket.
- **Impact:** The requirement cannot be implemented in a way that satisfies both AC4 and N-05. A tester following AC4 and one following N-05 would accept different behavior.
- **Proposed correction:** Keep "named chunks plus similarity" (it supports "tickets similar to TKT-1001"), and state the rule precisely:
  - Named existing tickets: their chunks come first in the context, followed by similarity results.
  - If the question names ids and none of them exist, return the no-match body regardless of similarity results. This makes N-05 deterministic (`NO_MATCH` only).
  - Reword FR-12 AC4 to match.
  - Relax G-02 to "must cite `TKT-1001`; other citations are allowed only when their text supports the statement".
  - Add a unit case for a mix of existing and nonexistent ids.
- **Decision needed:** D-2.

### R-03 [High] Citation validation rules are incomplete

- **Affected specs:** `architecture.md` section 11.2, `rag-api-contract.md`, `evaluation-strategy.md` section 8.2, `requirements.md` RAG-14, `ai-grounding.mdc`.
- **Problem:**
  - `evaluation-strategy.md` 8.2 says any `TKT-<number>` in the answer that is not in the retrieved set is an invented id and "must be zero in returned answers because the validator rejects it". The validator in `architecture.md` 11.2 only parses bracketed `[ticket:<id>]` markers. A bare invented id in prose passes it.
  - The marker grammar is undefined: case, whitespace, several ids in one bracket (`[ticket:TKT-1001, TKT-1002]`), and nested or malformed markers.
  - "The retrieved set" is not defined. It should be the tickets whose chunks were actually placed in the prompt. A ticket retrieved but trimmed by the context cap was never seen by the model.
  - `grounded:true` only means every citation id is in the retrieved set. It does not mean each statement is supported. The contract says this in one place only.
- **Impact:** The evaluation spec claims a code guarantee that does not exist. An answer with an invented bare id and one valid citation returns `grounded:true`.
- **Proposed correction:**
  - The validator scans for every token matching `TKT-\d+` in the answer, case-insensitively, and requires each to be in the prompt set. It also requires at least one valid bracketed citation.
  - Define the marker grammar: exactly `[ticket:TKT-<n>]`, one id per bracket. Anything else is treated as an uncited statement.
  - Define the retrieved set as "tickets with at least one chunk in the prompt".
  - State in `rag-api-contract.md` and `ui-flow.md` that `grounded` means "citations verified", not "claims verified".
  - Add unit tests for each rule (see R-30).

### R-04 [Medium] Prompt size is not tied to the chat model's context window

- **Affected specs:** `architecture.md` sections 11 and 14, `rag-ingestion.md` section 11 and configuration table, OD-01 (chat model undecided).
- **Problem:**
  - The defaults are top-K 5, a 1500-character chunk body cap, and a 6000-character context cap. Five chunks with headers can already exceed 6000 characters, so the context cap would silently reduce the effective top-K.
  - Spring AI's Ollama option reference lists a small default for `num-ctx` (2048 for the embedding options page that was reviewed; the chat option default must be checked). If the prompt (system prompt, context, question, and answer allowance) exceeds the model's configured window, Ollama truncates it, and the grounding instructions at the start of the prompt could be dropped without any error.
- **Impact:** Silent loss of grounding instructions or context, which is the failure the grounding rules exist to prevent.
- **Proposed correction:**
  - Require `num-ctx` to be set explicitly for the chat model, with a startup check.
  - Define a prompt budget: system prompt plus context plus question plus maximum answer tokens must fit in `num-ctx`.
  - Define the trimming order: lowest-ranked chunks first; named-ticket chunks are kept first.
  - Reconcile the 6000-character cap with top-K and chunk size, or document that the cap, not top-K, is the real limit.
  - Add a test that a context over the budget drops chunks from the end and never changes the system prompt.

### R-05 [Medium] Delimiter collision in the prompt is not specified

- **Affected specs:** `architecture.md` sections 3.2 (`PromptBuilder`), 11, 15; `requirements.md` RAG-12; `evaluation-strategy.md` PI-D6.
- **Problem:** Ticket text is placed in "a delimited context block" and PI-D6 requires the text to be passed "unchanged". Nothing says what happens when ticket text contains the delimiter itself (for example a closing tag), which would let stored text end the data block and be read as instructions.
- **Impact:** A known prompt-injection route is left open by the spec. The code-side citation check limits the damage but does not stop other manipulation.
- **Proposed correction:** Use a per-request random delimiter, or escape the delimiter sequence in ticket text, and state which. Replace "unchanged" in PI-D6 with "unchanged except delimiter escaping". Add a unit test with ticket text containing the delimiter.

### R-06 [Medium] Questions that filter on status or priority are not supported by retrieval

- **Affected specs:** `architecture.md` sections 9 and 11, `rag-ingestion.md` sections 3 and 4, `requirements.md` RAG-15, `evaluation-strategy.md` G-04 and G-05.
- **Problem:** Status and priority exist as metadata and appear in the prompt, but the embedded chunk text (header plus body) contains only ticket id, category, and type. The top-K set is chosen without regard to priority or status. For "high-priority tickets related to payment" the K payment-related chunks may be mostly low priority, while high-priority ones are missed. RAG-15 only says the answer must not claim completeness.
- **Impact:** Two of the five assignment example questions depend on this. G-05 and G-04 pass only if enough qualifying tickets happen to be in the top K.
- **Proposed correction (options):**
  - Add `Status` and `Priority` to the chunk header (cheap; they re-embed on change anyway because those changes already trigger re-ingestion).
  - Optionally add deterministic metadata filters when the question contains status or priority keywords.
  - Or accept the limitation and record it as a known limit with a measured recall for these questions.
- **Decision needed:** D-5.

### R-07 [Medium] Assumption labelling and per-statement citations are not testable

- **Affected specs:** `requirements.md` RAG-13 and FR-13 AC5, `rag-api-contract.md`, `ai-grounding.mdc`, `evaluation-strategy.md` section 8.1.
- **Problem:** RAG-13 requires facts and assumptions to be distinguishable, but the response contract has no marker or field for assumptions. FR-13 AC5 requires a citation for every factual statement, but code only enforces "at least one valid citation" (R-03). AC5 is therefore only measured statistically in evaluation.
- **Impact:** Neither requirement can be checked by a deterministic test, and the contract does not tell the UI how to show assumptions.
- **Proposed correction:** Define a textual convention (for example a sentence that begins `Assumption:` and carries no citation). Mark FR-13 AC5 as "evaluated, not enforced" in the spec and in the map in `test-strategy.md`. Add a deterministic check that no uncited sentence contains a ticket id.

### R-08 [Low] Snippet selection for chunks without a score

- **Affected specs:** `rag-api-contract.md` (citation `snippet`).
- **Problem:** The snippet comes from "the highest-scoring chunk" of the cited ticket. Chunks fetched by direct lookup (A-15) have no similarity score. The spec also says the snippet is omitted "only when no chunk text is available", which cannot occur because every cited ticket has a chunk in the prompt.
- **Impact:** Two implementations choose different snippets, and the omit rule is dead text.
- **Proposed correction:** Pick the chunk with the highest score; if none has a score, the first chunk in source order (description, then comments, then resolution notes). Remove the omit branch or define when it applies.

### R-09 [Medium] Missing operational limits on the ask endpoint

- **Affected specs:** `requirements.md` NFR-SEC and NFR-PRF, `architecture.md` sections 14 and 15, `rag-api-contract.md`.
- **Problem:**
  - No limit on concurrent `/api/ai/ask` requests. On CPU-only Ollama one request can take many seconds, so a few parallel clicks queue behind one another or time out.
  - No maximum request body size is stated for any endpoint.
  - NFR-SEC-09 forbids logging full ticket text and prompts, but does not mention the user's question, which can contain customer data.
- **Impact:** Easy self-inflicted outages, and a gap in the logging rule.
- **Proposed correction:** Add `app.rag.ask.max-concurrent` (extra requests get 503 `SERVICE_UNAVAILABLE` with a clear message), a request body size limit, and "question text is not logged at info" to NFR-SEC-09. Add the tests.

---

## 4. Findings: API contract, data model, and requirements

### R-10 [Medium] Comment field is `text` in one place and `content` in the rest

- **Affected specs:** `requirements.md` section 3 table and FR-06 AC1 (`text`), versus `api-contract.md`, `data-model.md`, `ui-flow.md` (`content`).
- **Problem:** The same field has two names.
- **Impact:** A developer implementing from the requirements writes a different JSON field than the contract and the database column.
- **Proposed correction:** Change the requirements to `content` (A-08 and FR-07 use "comment text", which is fine as prose).

### R-11 [Low] List sort tie-breaker is a string order in the API and a numeric order in the index

- **Affected specs:** `api-contract.md` (sort `updatedAt` desc, then `ticketId` desc), `data-model.md` index (`updated_at DESC, id DESC`).
- **Problem:** `ticketId` is a string. String descending order puts `TKT-999` above `TKT-1000`. The index orders by the numeric `id`.
- **Impact:** Page contents differ from what the index supports, and ordering can look wrong once ids pass three digits.
- **Proposed correction:** Say "then newest created first (internal id descending)" in the contract.

### R-12 [Medium] Reserved assignee `UNASSIGNED`, and empty assignee from the UI

- **Affected specs:** `rag-ingestion.md` section 4 and DQ-ING-01, `api-contract.md` (assignee rules), `ui-flow.md` section 4, `test-strategy.md` UT-ING-02.
- **Problem:**
  - DQ-ING-01 ("reserve the value `UNASSIGNED`", default yes) says "Settle in `spec/api-contract.md`", but the contract does not mention it and no test covers it. A real assignee named `UNASSIGNED` would be indistinguishable from none in metadata.
  - `ui-flow.md` says an empty assignee box means unassigned. The contract says `""` is 400, so the UI must send `null` or omit the field. This is not stated in the UI spec.
  - DQ-01 (comment public id) is still marked open in `data-model.md`, though the contract implicitly settled it with no comment id.
- **Impact:** A collision in metadata, an avoidable 400 from the UI, and open questions that look unresolved.
- **Proposed correction:** Add to the contract: `UNASSIGNED` (case-insensitive) is rejected with 400 `VALIDATION_ERROR` on create and update. State in `ui-flow.md` that an empty assignee is omitted (create) or sent as `null` (update). Mark DQ-01 and DQ-ING-01 closed. Add tests.

### R-13 [Medium] Input rules missing for ids and enum values

- **Affected specs:** `api-contract.md`, `api-standards.mdc`, `test-strategy.md`.
- **Problem:** The contract does not say what happens for a malformed `{publicId}` (`abc`, `tkt-1001`, `TKT-`) or for enum values in the wrong case (`status=open`, `"priority":"high"`). Both 400 and 404 are defensible.
- **Impact:** Unspecified behavior, so no negative test can be written.
- **Proposed correction:** `{publicId}` must match `TKT-<digits>` exactly and is case-sensitive. A non-matching id returns 400 `VALIDATION_ERROR`; a well-formed id that does not exist returns 404. Enum values are case-sensitive uppercase; otherwise 400. Add to the contract and the tests.

### R-14 [Low] `PUT` is a partial update, and the steering rule says `PUT` replaces

- **Affected specs:** `api-contract.md` (PUT), `.cursor/rules/api-standards.mdc`, `requirements.md` FR-04.
- **Problem:** The contract's PUT changes only the fields present. `api-standards.mdc` says "`PUT` replaces. `PATCH` partially updates." The contract also requires at least one editable field (otherwise 400), which FR-04 does not state, and ignores `resolutionNotes` on non-resolving requests without saying so in the requirements.
- **Impact:** Rule and contract conflict. A future reviewer or AI tool following the rule would flag the contract as wrong.
- **Proposed correction:** Keep `PUT` (it was requested) and add one sentence to `api-standards.mdc` that the contract's `PUT` is an explicit partial update. Add the "at least one editable field" rule to FR-04 and a test.

### R-15 [Low] Order of checks for resolving without notes

- **Affected specs:** `state-machine.md`, `api-contract.md` (status endpoint), `test-strategy.md` IT-ST-03.
- **Problem:** `OPEN → RESOLVED` without notes is both an invalid transition and a missing-notes validation error. The order of checks is not stated.
- **Impact:** 400 `INVALID_TRANSITION` or 400 `VALIDATION_ERROR` both pass today. Clients and the UI map the two codes differently.
- **Proposed correction:** Check the transition first. A request that is an invalid transition returns `INVALID_TRANSITION` regardless of notes. Add an SM case.

### R-16 [Medium] The UI duplicates the transition table, and the architecture says it holds no business rules

- **Affected specs:** `architecture.md` section 4, `ui-flow.md` section 7, `api-contract.md`.
- **Problem:** `architecture.md` says the frontend holds no business rules. `ui-flow.md` section 7 hard-codes which transitions are allowed from each status to decide which buttons to show (FR-03 AC3).
- **Impact:** Two copies of the state machine that can drift. A future change to the machine needs a UI change as well.
- **Proposed correction:** Add `allowedTransitions` (array of statuses) to the ticket detail response and use it in the UI. If you prefer to keep the table in the UI, record that exception in `architecture.md`.
- **Decision needed:** D-4.

### R-17 [Medium] Health and metrics endpoints are required but not defined

- **Affected specs:** `requirements.md` NFR-OBS-03 and NFR-OBS-04, `architecture.md` section 13, `api-contract.md`, `test-strategy.md`.
- **Problem:** No spec gives the path, response shape, or degraded rules of the health endpoint, nor how ingestion counts are exposed. No test covers either.
- **Impact:** Requirements with no contract and no test. `architecture.md` already says a failed ingestion is "degraded" in health, with no definition of degraded.
- **Proposed correction:** Define health (for example the Spring Boot Actuator health endpoint with custom indicators for database, Ollama, and index readiness, and states `UP`, `DEGRADED`, `DOWN`) and an ingestion counts section. Say which endpoints are exposed. Add the new dependency (Actuator) to the dependency list if used, with justification. Add one integration test each.

### R-18 [Low] Smaller field and catalogue inconsistencies

- **Affected specs:** `requirements.md`, `api-contract.md`, `rag-api-contract.md`, `rag-ingestion.md`, `architecture.md`.
- **Problem:**
  1. `requirements.md` section 3 says `resolutionNotes` exists "when RESOLVED". `data-model.md` requires it when RESOLVED or CLOSED.
  2. The `code` catalogue is split: `SERVICE_UNAVAILABLE` is defined only in `rag-api-contract.md`.
  3. `architecture.md` section 14 describes top-K as a "positive integer"; `rag-ingestion.md` allows 1 to 50.
  4. NFR-OBS-02 lists ask outcomes `ANSWERED`, `NO_MATCH`, `ERROR`. `rag-api-contract.md` adds `CITATION_REJECTED` (see R-01).
- **Impact:** Minor, but each one is a place where implementation and test would differ.
- **Proposed correction:** Fix each wording; keep one error-code table in `api-contract.md` that `rag-api-contract.md` links to.

---

## 5. Findings: state machine, concurrency, and persistence

### R-19 [High] Optimistic locking has no client input, and comments can collide with each other

- **Affected specs:** `state-machine.md` (SM-17, section on concurrency), `api-contract.md`, `data-model.md` DM-04, `architecture.md` section 3.3, `test-strategy.md` UT-ST-06 and IT-ST-04, `ui-flow.md` (409 handling).
- **Problem:**
  1. SM-17 says "both sent with the same version", but no request or response in `api-contract.md` carries a `version`. The ticket object does not expose it. The lock therefore protects only truly simultaneous server-side transactions. A stale page in a browser is not protected: a later request just sees the new state and gets 400 `INVALID_TRANSITION` or applies on top of newer data.
  2. `data-model.md` DM-04 increments `tickets.version` on comment adds so ingestion sees a new version. If that is done with the JPA optimistic check, two simultaneous comments on one ticket make one of them fail with 409, although both are valid and independent.
  3. The `PUT` and comment responses in `api-contract.md` list 409 only for terminal tickets, not for a lost lock.
- **Impact:** The concurrency requirement (FR-05 AC6) is only partially meaningful, a benign concurrent action can return an error, and the concurrency tests cannot be written deterministically (R-34).
- **Proposed correction:**
  - Decide whether clients send the version (an `If-Match`/`ETag` header, or a `version` field) so a stale UI gets 409.
  - Separate the two uses of `version`: an optimistic check for status changes and field edits, and an unconditional atomic increment for comment adds.
  - Add the lost-lock 409 to the PUT and status responses.
  - Rewrite SM-17 and UT-ST-06 accordingly.
- **Decision needed:** D-3.

### R-20 [Low] "Retryable FAILED" does not exist

- **Affected specs:** `architecture.md` section 10.2 (diagram: "PENDING or retryable FAILED"), `rag-ingestion.md` section 9, `data-model.md` section 6.
- **Problem:** `rag-ingestion.md` and `data-model.md` define `FAILED` as retries exhausted, with `next_attempt_at` cleared, and reset only on the next change or restart (ING-16). The diagram suggests the worker also picks up `FAILED` rows.
- **Impact:** A developer could implement endless retries of permanently failing tickets.
- **Proposed correction:** Change the diagram to "due PENDING row".

### R-21 [Medium] Reconciliation does not reset `requested_at`, and "startup run" has no marker

- **Affected specs:** `data-model.md` section 6.4, `architecture.md` section 10.4, `rag-ingestion.md` section 12, NFR-OBS-03.
- **Problem:**
  - Reconciliation marks every row `PENDING` but does not say `requested_at` is reset. NFR-OBS-03's "age of the oldest pending ingestion" would then show the age of the oldest unsatisfied original request, which can be days after a restart.
  - Readiness is "no row from the startup run is still pending". No column or in-memory rule identifies startup-run rows, and a ticket edited during the backfill re-enters `PENDING`.
- **Impact:** A misleading staleness metric, and a readiness rule that can wait on rows that arrived after the restart.
- **Proposed correction:** Set `requested_at` to now during reconciliation. Define readiness as "the set of ticket ids captured at reconciliation has each reached INDEXED or FAILED" and keep the set in memory.

### R-28 [Medium] Database location, ignore rules, locking, and recovery are not specified

- **Affected specs:** `architecture.md` sections 5 and 15, `data-model.md` section 8 (migration notes), `requirements.md` FR-09.
- **Problem:**
  - No default database file path is defined. The repository has a `.git` folder but no `.gitignore`, so a data file or a saved index could be committed.
  - File-backed H2 allows one process at a time by default. Running the app while a test uses the same file would fail. The spec says only IT-RS-01 uses a file.
  - No statement on backup or restore, on what happens when a migration fails at startup, or on the public id sequence across restarts.
- **Impact:** Data loss or accidental commits, and unclear start-up behavior.
- **Proposed correction:** Define a default path (for example `./data/tickets`), require a `.gitignore` entry before the first run, require the application to fail fast with a clear message when a migration fails or the file is locked, state that backup is a copy of the file while the app is stopped, and add the restart check of the public id sequence to IT-RS-01 (R-33).

### R-29 [Medium] How Ollama is started and how models are pulled is not specified

- **Affected specs:** `architecture.md` sections 6 and 14, `requirements.md` NFR-REL-06, `docs/project-assessment.md`.
- **Problem:** Ollama runs in Docker (a decision recorded in the assessment), but no spec defines the container command or compose file, the model volume, how the first model pull happens, or what happens when the machine is offline at first start. `architecture.md` says a startup check pulls models but does not define behavior while a pull is in progress.
- **Impact:** First-run behavior is undefined, and an implementation could block or fail without a clear message. The environment is not reproducible from the repository.
- **Proposed correction:** Add a short "local runtime" section: a compose file at the repository root with pinned image tag and volume, documented model pull step, and the application behavior (index not ready, ask returns 503, clear log line) until models are available.
- **Decision needed:** D-8.

---

## 6. Findings: RAG ingestion, chunking, and corpus

### R-22 [Medium] Chunking rules contradict each other and leave cases undefined

- **Affected specs:** `rag-ingestion.md` section 3 (tiny tail rule, ING-04), `architecture.md` section 9.2, `test-strategy.md` UT-ING-04.
- **Problem:**
  - The tiny-tail rule merges a last chunk under 100 characters into the previous chunk "if the result stays within 1.2 times the cap". ING-04 and the section text say "no chunk over the cap". The two cannot both hold.
  - "Sentence end" is not defined, so two implementations split differently.
  - Paragraph splitting uses blank lines. Stack traces and logs usually have single newlines and no blank lines, so they fall straight to sentence or length splitting.
  - The header is added after the body cap (header size is not bounded in the spec text, although title and category are limited).
- **Impact:** The same ticket yields different chunks across implementations, and ING-04 cannot be written to match both rules.
- **Proposed correction:** Make the cap strict (drop the 1.2 factor, or state ING-04 as "within 1.2 times the cap" and test it). Define a sentence end as `.`, `!`, or `?` followed by whitespace. Add a single-newline (line) level between paragraph and sentence. State that the header is excluded from the cap and give its maximum length. Add test rows for the tail merge and line splitting.

### R-23 [Medium] Comments per ticket are unbounded

- **Affected specs:** `requirements.md` A-07 and A-10, `data-model.md`, `rag-ingestion.md` section 12, NFR-PRF-03 and NFR-PRF-06.
- **Problem:** Each comment is up to 2000 characters and there is no limit on the number of comments. A busy ticket produces unbounded chunks, so one edit re-embeds an unbounded number of chunks, and a single ticket can exceed the 30-second freshness target (NFR-PRF-03).
- **Impact:** Ingestion time and index size per ticket are not predictable, and rebuild time (OD-05) is not bounded.
- **Proposed correction (options):** Add a maximum number of comments per ticket (for example 200) enforced as 409 or 400; or cap the number of embedded comment chunks with a documented rule; or accept and measure it in OD-05. Whichever is chosen, add the limit to `requirements.md` A-07 and a test.
- **Decision needed:** D-6.

### R-24 [Low] The cosine score range is stated as 0 to 1

- **Affected specs:** `rag-ingestion.md` section 11, `architecture.md` section 14, OD-02.
- **Problem:** The text says cosine scores "range 0 to 1". Cosine similarity ranges from -1 to 1, and the Spring AI store source reviewed returns the raw cosine value. For text embeddings negative values are rare, but the claim is unsupported. The starting threshold 0.5 is also unvalidated: some embedding models give unrelated texts a fairly high baseline similarity, so 0.5 may not separate relevant from irrelevant text.
- **Impact:** A misleading claim, and a risk of a threshold that lets everything through.
- **Proposed correction:** Reword to "the configured threshold is between 0 and 1; scores below 0 never qualify". Keep OD-02 open and require the score-separation check in the evaluation to pass before the threshold is accepted.

### R-25 [Low] `contentHash` is defined three ways

- **Affected specs:** `architecture.md` section 7.2 ("hash of the chunk's source text"), `data-model.md` section 7.2, `rag-ingestion.md` section 4 (SHA-256 of the body text).
- **Problem:** "Source text", "chunk text", and "body text" are different strings (the header is added after).
- **Impact:** Tests comparing hashes may disagree with the code.
- **Proposed correction:** One definition: SHA-256 of the chunk body, without the header, UTF-8, hex.

### R-26 [Medium] Re-ingestion scenarios missing from ING-01 to ING-20 and IT-RE

- **Affected specs:** `rag-ingestion.md` section 16, `test-strategy.md` section 3.5.
- **Problem:** No scenario covers:
  1. The ticket write and the ingestion request are atomic: a fault between them rolls back both. IT-RE-02 covers only rejected requests.
  2. A title or category edit re-embeds every chunk of the ticket, because the header changes.
  3. Assignee cleared, so that `assignee` becomes `UNASSIGNED` after re-ingestion.
  4. A `CANCELLED` and a `CLOSED` ticket (status metadata and no resolution chunk for `CANCELLED`).
  5. A ticket with no comments and no notes (the text exists in section 3, but no scenario).
  6. A comment added to a `RESOLVED` ticket.
  7. An edit made during the startup backfill (the text exists in section 12 but no scenario).
  8. A worker stopped between the commit of `indexed_version` and the deletion of old chunks.
  9. A changed embedding dimension or model after restart.
- **Impact:** The visibility rule and the outbox design are the most complex part of the system, and several of their edge cases are untested.
- **Proposed correction:** Add ING-21 to ING-29 for the cases above, and map them in `test-strategy.md`.

### R-27 [Medium] How the golden corpus is loaded is not specified

- **Affected specs:** `evaluation-strategy.md` section 3, `data-model.md` (sequence starts at 1001), `test-strategy.md` section 4.
- **Problem:** The dataset refers to fixed ids and states (for example `TKT-1002` is `CLOSED` with notes). Ids come from a sequence, and reaching a status needs valid transitions. The spec does not say how the corpus is created, in what order, or how the sequence is controlled.
- **Impact:** Evaluation results depend on ids that tests cannot guarantee.
- **Proposed correction:** Specify a fixture loader that creates tickets through the service layer in a fixed order on an empty database, applies transitions through the state machine, and asserts the expected ids. Load the same fixture for the unit-level fake evaluation.

---

## 7. Findings: tests and acceptance criteria

### R-30 [Medium] Missing unit tests for the grounding components

- **Affected specs:** `test-strategy.md` section 2 and 5, `architecture.md` section 3.2.
- **Problem:** The unit section covers the ticket service, validation, the state machine, comments, ingestion, and retrieval. It has no section for `CitationValidator`, `PromptBuilder`, `AskService`, or `IndexReadiness`. The map refers to "UT citation builder" and "UT with a scripted invented id", which have no id and no definition.
- **Impact:** The grounding guarantee (RAG-14) has no named unit tests.
- **Proposed correction:** Add UT-CIT-01..n (valid, unknown id, bare invented id, no citation, malformed marker, mixed case, sentinel), UT-PRM-01..n (fixed system prompt, delimiter escaping, budget), UT-ASK-01..n (empty retrieval does not call chat, readiness, failure mapping, concurrency limit).

### R-31 [Medium] UI check ids are referenced but never defined

- **Affected specs:** `test-strategy.md` section 5, `ui-flow.md` section 15.
- **Problem:** `UI-01` to `UI-20` appear only as short phrases in the map ("UI-01 create journey"). There is no table with steps and expectations. The map also contains unnamed scenarios: "IT-ST valid set", "IT with an empty retrieval", "IT asserts HTTP 200 for no-match and 4xx or 503 for errors", "a test that ask writes no ticket row".
- **Impact:** NFR-TST-05 says every criterion maps to a scenario, but these scenarios cannot be traced to a defined test.
- **Proposed correction:** Add a UI scenario table (id, steps, expectation) to `ui-flow.md` or `test-strategy.md`, and give each unnamed integration scenario an id.

### R-32 [Medium] Some map entries claim coverage that the scenario does not state

- **Affected specs:** `test-strategy.md` sections 3.1 and 5.
- **Problem:** Examples:
  - FR-04 AC3 (forbidden fields ignored on update) maps to IT-API-03, which does not mention sending `status`, `category`, or `ticketId` in a PUT.
  - FR-01 AC3 (client-supplied id, status, and timestamps ignored) maps to IT-API-01, which lists only valid and invalid bodies.
  - FR-05 AC2 requires the rejection message to name the allowed statuses. IT-ST-01 asserts the code and the unchanged status only.
  - FR-10 AC3 (malformed JSON) maps to IT-API-01 and IT-API-09, neither of which sends malformed JSON.
- **Impact:** A green map with criteria that no test would actually fail on.
- **Proposed correction:** State each expectation in the scenario text.

### R-33 [Medium] Missing negative and security test cases

- **Affected specs:** `test-strategy.md`, `requirements.md` NFR-SEC and NFR-OBS.
- **Problem:** No scenario for:
  1. A malformed `{publicId}` or wrong-case enum (R-13).
  2. `PUT` with no editable field, and `PUT` that sends only forbidden fields.
  3. A status request with a missing `status`, and `resolutionNotes` sent with a non-resolving transition.
  4. A search string such as `' OR 1=1 --` (NFR-SEC-02). IT-API-06 covers `%` and `_` only.
  5. Ticket text containing HTML or script, rendered as text (NFR-SEC-03).
  6. A request from a non-allowed origin (CORS, NFR-SEC-04).
  7. The H2 console not reachable outside the local profile (NFR-SEC-06).
  8. Logs not containing ticket text or the question (NFR-SEC-09).
  9. The chat model failing while embeddings work (and the reverse).
  10. The public id sequence after a restart (no reuse).
  11. The health and metrics endpoints (R-17).
  12. Assignee `UNASSIGNED` (R-12) and the comment-count limit (R-23), once decided.
- **Impact:** The negative paths most likely to hide defects are not covered.
- **Proposed correction:** Add scenarios IT-API-10 onward, IT-SEC-01..n, and UI checks for each item.

### R-34 [Low] Two scenarios are not deterministic as written

- **Affected specs:** `state-machine.md` SM-17, `test-strategy.md` IT-ST-04 and IT-RS-02.
- **Problem:**
  - SM-17 and IT-ST-04 need two truly simultaneous requests. Without a latch or an injected hook the outcome depends on timing.
  - IT-RS-02 asks "immediately after start" and expects 503. With a fast fake embedding model the backfill may already be finished.
- **Impact:** Flaky tests that either fail randomly or never exercise the path.
- **Proposed correction:** Specify the technique: a barrier in a test hook around the write for SM-17, and a gated fake embedding model that blocks until released for IT-RS-02.

### R-35 [Low] Evaluation seed set is smaller than its own minimums, and targets are statistically weak

- **Affected specs:** `evaluation-strategy.md` sections 3.4, 4, 9.
- **Problem:** Section 3.4 requires at least eight negative questions and three "named ticket, missing detail" questions. Section 4 lists seven negatives (N-01..N-07) and one such question (G-08). A true no-match target of 0.95 with seven negatives means any single failure fails the run, so the "rate" is effectively "all".
- **Impact:** Targets read as statistical but behave as absolute, and the seed set does not meet its own minimum.
- **Proposed correction:** State that section 4 is the seed set and add the missing cases, or lower the minimums. State that negatives and injection cases are pass or fail, and rates apply only to larger positive sets.

---

## 8. Findings: unsupported or unconfirmed assumptions

### R-36 [Medium] Open decisions that the design already depends on

- **Affected specs:** `architecture.md` section 2 (AD-09, OD-01 to OD-06), `rag-ingestion.md` section 17.
- **Problem:** Several items are still open, but later sections rely on a particular answer:

| Open item | What depends on it |
|---|---|
| OD-01 chat model | Prompt budget (R-04), citation compliance rate, evaluation targets |
| OD-02 threshold and top-K | 0.5 and 5 are used throughout as if validated |
| OD-03 `nomic-embed-text` prefixes | Retrieval quality, section 6 of `rag-ingestion.md` |
| OD-04 delete by metadata filter | The "replace a ticket's chunks" step and the cleanup in section 9 |
| OD-05 rebuild time | NFR-PRF-06 and the readiness behavior at start |
| OD-06 starter and artifact names | Scaffolding of `pom.xml` |
| AD-09 Next.js calls the backend directly with CORS | Everything in `ui-flow.md` |

- **Impact:** Late discovery that an assumed library capability does not exist would change the ingestion design.
- **Proposed correction:** Run one short, timeboxed spike for OD-03, OD-04, OD-05, and OD-06 before implementation and record the results in `docs/architecture-decisions.md`. Confirm AD-09. Close or restate each item in the specs.

### R-37 [Low] Prompt caching with a local model is asserted but not verifiable

- **Affected specs:** `requirements.md` section 10 (process), `architecture.md` section 11.1.
- **Problem:** The assignment asks for prompt caching of static prompt parts. `architecture.md` says the system prompt is a stable prefix that "can be cached". With a local Ollama model there is no API-level prompt cache to configure, and the spec has no way to show that a cache hit happened.
- **Impact:** A claimed capability that cannot be demonstrated.
- **Proposed correction:** Say what is actually meant (an identical prefix so that any runtime prefix cache can reuse it), record the choice and the evidence in `docs/architecture-decisions.md`, and add a test that the system prompt is byte-identical across requests (part of UT-PRM).

### R-38 [Low] Dependency direction between `rag` and ticket persistence is not stated

- **Affected specs:** `architecture.md` section 3.1 and 3.3, `rag-ingestion.md` section 10.
- **Problem:** `service` writes `ticket_ingestion` (`IngestionRequestWriter`), while `rag` (`IngestionWorker`) updates it and reads ticket snapshots. The visibility rule makes `RetrievalService` read `indexed_version` from the database on every ask. The layering rules only say Spring AI types stay inside `rag` and `config`.
- **Impact:** Unclear who owns the table and which packages may depend on which.
- **Proposed correction:** State that `rag` may depend on repository interfaces or a read-only service port for ticket snapshots and ingestion rows, that `service` and `domain` never depend on `rag`, and which package owns the `ticket_ingestion` repository.

---

## 9. Findings: steering files, assignment artefacts, and housekeeping

### R-39 [High] `.cursor/rules/rag-vector-store.mdc` contradicts the approved RAG specs

- **Affected files:** `.cursor/rules/rag-vector-store.mdc` versus `rag-ingestion.md` sections 4, 7, 9, 10, `architecture.md` section 10, `data-model.md` section 7.
- **Problem:**
  - Metadata keys: the rule lists `sourceId`, `sourceType` (`ticket` or `knowledge-base`), `chunkIndex`, `contentHash`, `title`. The specs use `ticketId`, `kind`, `sourceRef`, `ingestVersion`, `status`, `priority`, `assignee`, `category`, and others. The assignment's required metadata (status, priority, assignee, category) is not in the rule.
  - Re-ingestion: the rule says "Delete that sourceId's chunks before inserting the new ones". The approved design deliberately does the opposite (add, commit `indexed_version`, then delete) so a ticket never disappears from search.
  - Startup: the rule says "either re-ingest ... or `load` a previously saved store". The specs rebuild on every start, with an ingestion table. A saved store could hold chunks that fail the visibility rule.
  - Scope: the rule talks about "approved knowledge-base documents", but v1 indexes tickets only.
- **Impact:** This rule is applied automatically when an agent edits retrieval code. Following it would reintroduce the delete-before-commit gap that was already found and fixed in the specs, and would use the wrong metadata keys.
- **Proposed correction:** Rewrite the rule to match `rag-ingestion.md` (metadata keys, visibility rule, ordering, rebuild on start, tickets only), keeping its general points (config-driven top-K and threshold, one embedding model, no secrets).

### R-40 [Medium] Smaller rule drift in `api-standards.mdc` and `ai-grounding.mdc`

- **Affected files:** `.cursor/rules/api-standards.mdc`, `.cursor/rules/ai-grounding.mdc`.
- **Problem:**
  - `api-standards.mdc` uses `{ticketId}` as the path variable. The contract uses `{publicId}`.
  - `api-standards.mdc` says `PUT` replaces (R-14) and does not point to the error `code` catalogue in `api-contract.md`.
  - `ai-grounding.mdc` restricts the fallback to an empty retrieval (R-01), does not describe the sentinel or the new error, and says "mark it as an assumption" without the marker defined in R-07.
- **Impact:** Rules and contract describe different shapes.
- **Proposed correction:** Update both rules after R-01, R-07, R-13, and R-14 are decided.

### R-41 [Medium] Assignment artefacts are still placeholders

- **Affected files:** `.cursor/commands/` (five files of three lines each), `.cursor/skills/documentation/SKILL.md` (nine lines), `docs/ai-mistakes.md` (three lines), `docs/architecture-decisions.md` (three lines), `docs/prompt-history.md` (seven lines), `README.md` (three lines).
- **Problem:** The assignment requires reusable commands and a skill, at least one documented AI mistake, a prompt history, and recorded decisions. Real material for them already exists from this work:
  - The ingestion order that left a window where a ticket vanished from search (found and fixed).
  - The claim that rebuild uses batched embedding calls, which the Spring AI source reviewed contradicts (fixed in two files, one mention left, see R-42).
  - The steering rule that was not updated after the specs changed (R-39).
  - Decisions: H2 file-backed, `SimpleVectorStore`, Ollama in Docker, token-optimisation tool choice (not yet recorded).
- **Impact:** These are assignment deliverables, and the real mistakes are easy to forget.
- **Proposed correction:** Record the three mistakes in `docs/ai-mistakes.md` now, add the decisions to `docs/architecture-decisions.md`, and fill the commands and skill when the first implementation step starts. This does not need spec approval and is listed separately in section 11.

### R-42 [Low] Stale cross-references and status lines

- **Affected specs:**
  - `architecture.md` line 5 says the contracts are "(not yet written)". Both exist.
  - `architecture.md` OD-02 refers to "the evaluation set (section 15)". Section 15 is Security boundaries. The evaluation set is in `spec/evaluation-strategy.md`.
  - `architecture.md` risks table still lists "batched embedding" as a mitigation. The store embeds one chunk per call (see section 10.4 of the same file).
  - `ui-flow.md` section 3 refers to "the error banner from section 13". Errors are section 14; section 13 is no-match.
  - Every spec still says "Status: draft for review".
- **Impact:** Readers follow dead references, and one risk mitigation contradicts a corrected section.
- **Proposed correction:** Fix each reference. Change the status line to "approved" with a date when you approve the set.

---

## 10. Decisions needed from you

| Id | Question | Recommendation |
|---|---|---|
| D-1 (R-01) | How should an ungrounded or unverified model answer be reported? | Sentinel `NO_ANSWER` returns the no-match body. Failed validation returns 502 `ANSWER_NOT_VERIFIED`, with at most one automatic retry. |
| D-2 (R-02) | When a question names a ticket id, what is the context? | Named tickets first, then similarity results. If every named id is missing, return no-match. |
| D-3 (R-19) | Should clients send a version so a stale UI gets 409? | Yes: `ETag` and `If-Match` on `PUT` and the status endpoint, and a plain atomic version increment for comments. |
| D-4 (R-16) | Should the API return the allowed next statuses? | Yes: `allowedTransitions` on the ticket detail response. |
| D-5 (R-06) | How should status or priority questions be supported? | Add `Status` and `Priority` to the chunk header, and record the limit. |
| D-6 (R-23) | Limit the number of comments per ticket? | Yes: maximum 200 per ticket, with a clear error. |
| D-7 (R-12) | Reserve `UNASSIGNED` as an assignee name? | Yes, case-insensitive. |
| D-8 (R-29) | Add a Docker Compose file for Ollama at the repository root? | Yes, with a pinned image tag and a named volume. |

---

## 11. Proposed application order (after approval)

Nothing below happens until you approve it. Each batch is a separate, reviewable change.

| Batch | Contents | Files touched |
|---|---|---|
| 1. Editorial | R-10, R-11, R-18, R-20, R-25, R-42, and closing DQ-01 | `requirements.md`, `architecture.md`, `api-contract.md`, `data-model.md`, `rag-ingestion.md`, `ui-flow.md` |
| 2. API and concurrency | R-12 to R-17, R-19, R-21, and R-09 (once D-3, D-4, D-7 are decided) | `api-contract.md`, `state-machine.md`, `data-model.md`, `architecture.md`, `ui-flow.md`, `requirements.md` |
| 3. RAG and grounding | R-01 to R-08, R-22 to R-25 (once D-1, D-2, D-5, D-6 are decided) | `rag-api-contract.md`, `rag-ingestion.md`, `architecture.md`, `requirements.md`, `evaluation-strategy.md` |
| 4. Persistence and runtime | R-28, R-29, R-36 to R-38 (once D-8 is decided) | `architecture.md`, `data-model.md`, `docs/architecture-decisions.md` |
| 5. Tests | R-26, R-27, R-30 to R-35 | `test-strategy.md`, `rag-ingestion.md`, `evaluation-strategy.md`, `state-machine.md`, `ui-flow.md` |
| 6. Steering | R-39, R-40 (after batches 2 and 3) | `.cursor/rules/rag-vector-store.mdc`, `api-standards.mdc`, `ai-grounding.mdc` |
| 7. Artefacts | R-41 (no spec approval needed) | `docs/ai-mistakes.md`, `docs/architecture-decisions.md`, `docs/prompt-history.md`, `.cursor/commands/*`, `.cursor/skills/documentation/SKILL.md` |

Before the first implementation step, the repository also needs a `.gitignore` (R-28). The repository has a `.git` folder, and the specs say data files must stay out of source control.
