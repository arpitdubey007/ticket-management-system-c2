# Support Ticket Management System

AI-assisted support tickets (REST + UI) with grounded `POST /api/ai/ask` over ticket history.

## Prerequisites

- **Java 21** (enforced by Maven enforcer)
- **Node.js** 20+ for the frontend
- **Ollama** (optional for local RAG): Docker or native, models `nomic-embed-text` and `llama3.2` per `backend/src/main/resources/application.yml`

## Run locally

1. **Ollama** (for real embeddings/chat): ensure `http://127.0.0.1:11434` serves the configured models.
2. **Backend** (port 8080):

   ```bash
   cd backend && ./mvnw spring-boot:run
   ```

   Default profile uses file-backed H2 under `backend/data/tickets`.

3. **Frontend** (port 3000):

   ```bash
   cd frontend && cp .env.example .env.local && npm run dev
   ```

   Next.js rewrites `/api/*` to `BACKEND_URL` (default `http://127.0.0.1:8080`).

## Tests (default CI gate — no Ollama)

```bash
cd backend && ./mvnw verify          # 63 tests, test profile + fake embed/chat
cd frontend && npm run lint && npm run typecheck && npm run test && npm run build
```

See **`docs/test-execution-report.md`** for the latest recorded counts and known gaps.

**OpenAPI:** `GET http://localhost:8080/v3/api-docs` when the backend is running.

## Specifications & docs

| Path | Purpose |
|---|---|
| `spec/` | Approved requirements and contracts |
| `docs/final-review.md` | Independent review + submission traceability |
| `docs/ai-mistakes.md` | Documented AI errors from development |
| `docs/architecture-decisions.md` | Stack and RAG decisions |
| `docs/prompt-history.md` | Index to `.specstory/history/` |
| `.specstory/history/` | Full SpecStory prompt transcripts |

## Known limitations

- **No authentication** (v1 assumption A-01).
- **CI does not run live Ollama** — semantic retrieval quality is not proven in `mvn verify` (see test report).
- **In-memory vector store** — rebuilt via `IndexReconciler` on startup; not persisted to JSON.
- **Partial test strategy** — many `spec/test-strategy.md` IT/UI cases are not automated (listed in test report).
- **No `ETag`/`If-Match`** on ticket updates (documented waiver in architecture decisions).
- **Ingestion `FAILED` blocks the assistant** until data/index is repaired (by design after review fix).

Do not claim assignment items are complete without checking `docs/final-review.md` § Submission traceability.