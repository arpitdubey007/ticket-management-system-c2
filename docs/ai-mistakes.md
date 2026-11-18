# AI mistakes

This document records **proven** incorrect assumptions or suggestions from AI-assisted development of the Support Ticket Management System. Entries are grounded in [SpecStory](https://specstory.com) session history under `.specstory/history/` and in Maven/Surefire output captured there—not invented for assessment.

**Sessions reviewed**

| Session file | Focus |
|---|---|
| `2026-11-18_09-47-30Z-first-go-through-the.md` | Scaffolding, specs, Cursor rules (no application compile failures) |
| `2026-11-18_11-29-53Z-backend-foundation-and-domain.md` | Backend domain, REST, RAG ingestion (primary source of mistakes below) |

**Current verification baseline:** `docs/test-execution-report.md` (2026-11-18) — `cd backend && ./mvnw verify` → **58** tests, **0** failures.

---

## Mistake 1 — Spring Boot 3 test imports on Spring Boot 4 (`MockMvc` / `TestRestTemplate`)

### Original AI suggestion

For `TicketApiIntegrationTest`, the agent used Boot 3-era imports:

- `org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc`
- `@SpringBootTest(webEnvironment = RANDOM_PORT)` with `org.springframework.boot.test.web.client.TestRestTemplate`

It also stated that “MockMvc dependencies are already present” and that adding `spring-boot-starter-test` would expose `TestRestTemplate`.

### Why it was incorrect

Spring Boot **4.0.0** modularizes web MVC testing. `@AutoConfigureMockMvc` lives in `org.springframework.boot.webmvc.test.autoconfigure`, not `org.springframework.boot.test.autoconfigure.web.servlet`. `TestRestTemplate` is not on the test compile classpath at the old package path even after `spring-boot-starter-test` was added.

### How it was detected

`./mvnw verify` failed at **testCompile** before any test ran.

### Evidence

From `.specstory/history/2026-11-18_11-29-53Z-backend-foundation-and-domain.md` (Maven output):

```text
package org.springframework.boot.test.autoconfigure.web.servlet does not exist
cannot find symbol: class AutoConfigureMockMvc
...
package org.springframework.boot.test.web.client does not exist
cannot find symbol: class TestRestTemplate
```

The agent later inspected `spring-boot-webmvc-test-4.0.0.jar` and found `AutoConfigureMockMvc` under `org/springframework/boot/webmvc/test/autoconfigure/`.

### Correction applied

- Added `spring-boot-starter-webmvc-test` (see `backend/pom.xml`).
- Updated `TicketApiIntegrationTest` to use `@AutoConfigureMockMvc` from `org.springframework.boot.webmvc.test.autoconfigure` and in-process `MockMvc` (no `TestRestTemplate`).

### Verification after correction

Integration tests compile and run; `TicketApiIntegrationTest` is included in the **58** passing backend tests in `docs/test-execution-report.md`.

### Engineering lesson

On **major Spring Boot upgrades**, do not reuse test imports from memory or older blog posts. Inspect the **actual** JAR for the pinned Boot version (`jar tf … | rg MockMvc`) before writing integration tests.

---

## Mistake 2 — Misdiagnosed HTTP 500 on ticket create (Jackson) while root cause was JPA `@MapsId`

### Original AI suggestion

When `POST /api/tickets` returned **500** in integration tests, the agent attributed failures to:

- Jackson failing to serialize `Instant` fields
- Need for `JavaTimeModule` / `jackson-datatype-jsr310`
- A custom `JacksonConfig` using `Jackson2ObjectMapperBuilderCustomizer`

### Why it was incorrect

Surefire showed **`AssertionFailure null identifier (com.ticketmanagement.domain.TicketIngestion)`** on service integration tests—the ingestion row could not be persisted because the `@MapsId` / `@OneToOne` mapping was wrong (ticket reference and identifier not aligned). HTTP 500 on create was a **symptom of that persistence failure**, not missing JSR-310 support. Boot 4’s Jackson stack also does not match the Boot 3 packages the agent tried to add.

### How it was detected

- API tests: `Status expected:<201> but was:<500>`
- Service tests: `null identifier (com.ticketmanagement.domain.TicketIngestion)`
- Follow-up compile failure when adding `JacksonConfig`:

```text
package org.springframework.boot.autoconfigure.jackson does not exist
package com.fasterxml.jackson.datatype.jsr310 does not exist
```

### Evidence

Same SpecStory session: failures at lines documenting `TicketApiIntegrationTest` 500s and `TicketServiceIntegrationTest` `AssertionFailure null identifier`.

### Correction applied

- Removed the invalid `JacksonConfig` approach.
- Fixed `TicketIngestion`: keep `@MapsId` + `@OneToOne`, constructor sets `ticket` only; `IngestionRequestWriter.requestIngestion` calls `ticketRepository.flush()` before creating the ingestion row so the ticket id exists (see `backend/src/main/java/com/ticketmanagement/service/IngestionRequestWriter.java`).

### Verification after correction

After the entity/writer fix, API create tests progressed past 500; remaining failures were unrelated (e.g. wrong transition in one service test). Final suite: **58/58** in `docs/test-execution-report.md`.

### Engineering lesson

For **500 on write paths**, read the **first** `Caused by` in Surefire or enable a single-test run with response body logging. Do not default to JSON serialization until persistence and transaction errors are ruled out.

---

## Mistake 3 — PUT partial update: `assignee` null vs omitted conflated (record DTO)

### Original AI suggestion

`TicketController` used a **record**-style `UpdateTicketRequest` and helper logic to detect whether the client intended to clear assignee vs leave it unchanged. The agent later noted: “I made a mistake in TicketController - assigneeProvided logic is wrong.”

### Why it was incorrect

Java records (and plain nullable fields) cannot distinguish JSON `{"assignee": null}` from an omitted `assignee` key. The API contract requires explicit null to **clear** assignee and omission to **preserve** it (`spec/api-contract.md`).

### How it was detected

Code review during the same implementation pass (agent self-correction before full green build). Behavior would have violated PUT semantics even if tests did not catch every edge case immediately.

### Evidence

SpecStory thought block: “Records cannot distinguish a null assignee from an omitted field.”

### Correction applied

Replaced the record with a mutable `UpdateTicketRequest` and `@JsonProperty` setter that sets `assigneePresent = true` when the key appears in JSON (`backend/src/main/java/com/ticketmanagement/web/dto/UpdateTicketRequest.java`).

### Verification after correction

`TicketApiIntegrationTest` and service flows pass under `./mvnw verify` per `docs/test-execution-report.md`.

### Engineering lesson

For **partial updates**, deserialization must track **field presence**, not only values—use mutable DTOs with explicit flags or Jackson `@JsonSetter` patterns, not records alone.

---

## Mistake 4 — Spring AI 2.0.2 `FilterExpressionBuilder` API misuse (vector delete)

### Original AI suggestion

In `SimpleKnowledgeIndex.deleteTicketVersionNot`, the agent treated `Filter.Expression` and `FilterExpressionBuilder.Op` interchangeably and called `.build()` on the wrong type—e.g. passing an `Expression` where `and()` expects `Op`, and calling `.build()` on an `Expression`.

### Why it was incorrect

In Spring AI **2.0.2**, `FilterExpressionBuilder.and(Op, Op)` returns an `Op` that exposes `.build()` → `Filter.Expression`. `Filter.Expression` does **not** have `build()`. `vectorStore.delete(...)` requires a `Filter.Expression`.

### How it was detected

`./mvnw verify` **compile** failure when the RAG pipeline was added.

### Evidence

```text
SimpleKnowledgeIndex.java:[41,62] incompatible types:
  Filter.Expression cannot be converted to FilterExpressionBuilder.Op
SimpleKnowledgeIndex.java:[53,25] cannot find symbol: method build()
  location: class Filter.Expression
```

### Correction applied

Use builder ops consistently: `builder.eq(...)`, `builder.ne(...)`, then `builder.and(ticketFilter, versionFilter).build()` for delete; `builder.eq(...).build()` inside `SearchRequest` for metadata filter (`backend/src/main/java/com/ticketmanagement/rag/SimpleKnowledgeIndex.java`).

### Verification after correction

RAG ingestion integration tests compile and run; backend **58/58** in `docs/test-execution-report.md` (`IngestionPipelineIntegrationTest` included).

### Engineering lesson

For Spring AI vector filters, **`javap` the shipped JAR** (as the agent did for `SimpleVectorStore`) before coding delete/search filters—blog examples often target older filter APIs.

---

## Mistake 5 — Domain model out of sync with RAG writers (`setIndexedVersion`)

### Original AI suggestion

`IndexReconciler` and `IngestionOutcomeWriter` called `row.setIndexedVersion(...)` immediately after introducing the RAG worker, without extending `TicketIngestion` with that property accessor.

### Why it was incorrect

The JPA entity matched the Flyway schema (`indexed_version` column) but the Java entity lacked `setIndexedVersion` / nullable handling when the RAG layer was added—classic “schema exists, domain incomplete” drift.

### How it was detected

Maven compile errors:

```text
cannot find symbol: method setIndexedVersion
location: variable row of type com.ticketmanagement.domain.TicketIngestion
```

### Correction applied

Added `indexedVersion` field and getters/setters on `TicketIngestion` (`backend/src/main/java/com/ticketmanagement/domain/TicketIngestion.java`).

### Verification after correction

Full `./mvnw verify` green per `docs/test-execution-report.md`.

### Engineering lesson

When adding a vertical slice (RAG) on an existing table, **update the entity and repository in the same change** as writers that mutate new columns.

---

## Mistake 6 — Ollama autoconfigure assumed safe in `test` profile

### Original AI suggestion

After adding `spring-ai-starter-model-ollama`, the agent assumed tests could load the full application context without isolating Ollama. Initial test runs failed with context load errors; the agent identified that **Ollama autoconfig tries to connect at startup** (embedding and chat).

### Why it was incorrect

The default test suite must not require a live Ollama endpoint (`spec/test-strategy.md`, NFR-TST-03). Leaving Ollama autoconfigure enabled breaks `@SpringBootTest` when nothing listens on the configured base URL.

### How it was detected

Surefire: `ApplicationContext failure threshold exceeded` across `TicketManagementApplicationTests`, `IngestionPipelineIntegrationTest`, and others after RAG beans were added.

### Evidence

SpecStory: “Ollama autoconfig tries to connect at startup for chat model too” → exclude embedding and chat autoconfig in test profile; use test-only embedding bean.

### Correction applied

- `spring.config.activate.on-profile: test` → `spring.autoconfigure.exclude` for `OllamaChatAutoConfiguration` and `OllamaEmbeddingAutoConfiguration` (`backend/src/main/resources/application.yml`).
- `TestEmbeddingConfig` (`@Profile("test")`) supplies a deterministic `EmbeddingModel` (`backend/src/main/java/com/ticketmanagement/config/TestEmbeddingConfig.java`).

### Verification after correction

`docs/test-execution-report.md`: default backend verify runs **without Ollama/Docker**; 58 tests pass with `TestEmbeddingConfig` / mocked chat where applicable.

### Engineering lesson

**Autoconfigure is an integration test dependency.** For external AI runtimes, exclude production autoconfig in `test` and register explicit test doubles early in the context.

---

## Mistake 7 — Invalid `@EventListener` method signature on `VectorStoreConfig`

### Original AI suggestion

An `@EventListener` on startup combined multiple injected parameters in a way Spring does not support for event listener methods (agent note: “VectorStoreConfig has an @EventListener with two parameters - not allowed”).

### Why it was incorrect

Spring `@EventListener` methods may accept the event type (and limited optional args), not arbitrary extra beans as parallel parameters. That prevented context startup during RAG wiring.

### How it was detected

Context load failure; `Caused by` chain in Surefire for repository integration tests after RAG config was introduced.

### Correction applied

Constructor-inject `EmbeddingModel` and `RagProperties`; listener method takes only `ApplicationReadyEvent` (`backend/src/main/java/com/ticketmanagement/config/VectorStoreConfig.java`).

### Verification after correction

Context loads; full backend suite passes per `docs/test-execution-report.md`.

### Engineering lesson

Use **constructor injection + single-argument listeners**; run `contextLoads` after adding any `@Configuration` that touches the AI stack.

---

## Investigations (suspected but not the proven root cause)

| Topic | What the AI suspected | What evidence showed | Outcome |
|---|---|---|---|
| Jackson / `Instant` serialization | Primary cause of ticket-create 500 | `null identifier (TicketIngestion)` on service ITs; Boot 4 Jackson packages differ from Boot 3 | **Investigation** — fix was JPA `@MapsId`, not JSON |
| Jackson 3 vs 2 property keys | `application.yml` Jackson flags broke context | Context failed after invalid YAML/properties; removed custom Jackson deps | **Investigation** — rely on Boot 4 defaults once persistence was fixed |
| Hash-based “fake” embeddings vs spec | User prompt forbade pseudo-embeddings; agent debated hash vectors | Settled on `TestEmbeddingConfig` deterministic non-zero vectors for test profile only | **Design reconciliation**, not a shipped production bug |

---

## Minor issues (logged in SpecStory, quickly fixed)

These are included for completeness but did not define architecture:

1. **Stray YAML key** — accidental `dream_zone` line in `application.yml`; removed before verify.
2. **`Comment.setTicket` visibility** — package-private setter blocked repository integration tests; made public for bidirectional mapping.
3. **Wrong illegal transition in a test** — `OPEN` → `RESOLVED` in `TicketServiceIntegrationTest` where the machine only allows `IN_PROGRESS` / `CANCELLED`; test updated to a genuinely illegal transition.
4. **Invalid Maven POM tag** — `<n>` instead of `<name>` in generated `pom.xml`; corrected during foundation task.

---

## What we did *not* document as proven mistakes

- **Hallucinated RAG answers or bad ticket citations in production** — citation validation and fail-closed `AskService` tests pass with mocks; live Ollama evaluation was **not run** in the default cycle (`docs/test-execution-report.md`, RAG evaluation row N/A).
- **Frontend stack choice (Next.js assumption)** — recorded as an unconfirmed default in `docs/project-assessment.md`, not as an implemented wrong dependency.

---

## How to use this file

Before repeating a pattern (Boot test imports, vector filter deletes, ingestion JPA mapping, test-profile Ollama), check the matching section above. After the next AI-driven change that fails CI, append a new numbered entry with SpecStory or CI log excerpts—do not list hypotheticals.

---

## Post-review remediation (2026-11-18)

Following `docs/final-review.md`, several review findings were fixed in code (session: `.specstory/history/2026-11-18_12-15-08Z-ai-mistakes-documentation.md`):

| Review id | Fix | Verification |
|---|---|---|
| H-1 | Citation failure → HTTP 502 `ANSWER_NOT_VERIFIED`, not no-match text | `AskApiIntegrationTest`, `AskServiceTest` |
| H-2 | `CitationValidator` scans bare `TKT-n` tokens | `CitationValidatorTest` |
| H-3 | Empty retrieval when all named ids missing | `RetrievalServiceTest` |
| H-4 | `IndexReadiness` tracks `failedTicketIds` | `IndexReadinessTest` |
| H-5 | springdoc + `OpenApiParityTest` | `mvn test -Dtest=OpenApiParityTest` |

Earlier mistakes (§1–§7) remain historical evidence from the first implementation session; they are not reintroduced in the current tree.