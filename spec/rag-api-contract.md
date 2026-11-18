# RAG API Contract

`POST /api/ai/ask` for grounded questions over ticket history.

Status: draft for review. Derived from `spec/requirements.md` (FR-12, FR-13, FR-14, RAG-09 to RAG-15a), `spec/architecture.md` (sections 11 and 12), and `.cursor/rules/ai-grounding.mdc`. This file contains no controller or DTO code. Ticket CRUD is in `spec/api-contract.md`.

The error body (`status`, `error`, `code`, `message`, `path`, `timestamp`) is the one in `spec/api-contract.md`.

## POST /api/ai/ask

One question, one retrieval, one response. The call does not create or change tickets, send notifications, or invoke other tools (FR-12 AC6). There is no conversation memory (A-13).

### Request

```json
{
  "question": "What caused previous payment failures?"
}
```

| Field | Required | Rule |
|---|---|---|
| `question` | yes | 1–500 characters after trim (A-07). Blank or missing is 400. Over 500 is 400. |

Validation runs before any embedding or chat call (FR-12 AC2).

### Response fields

| Field | Type | Meaning |
|---|---|---|
| `answer` | string | The grounded answer, or the exact no-match sentence below. |
| `grounded` | boolean | `true` only when the server has checked that every citation is a retrieved ticket. The model does not set this flag (RAG-14). |
| `citations` | array | Tickets the answer uses. Empty when `grounded` is `false`. |

`grounded: true` requires a non-empty `citations` array. `grounded: false` requires `citations` to be empty and `answer` to be the no-match sentence exactly.

### Citation object

Each citation names a real ticket that was in the retrieved set for this request (FR-13, RAG-10). A ticket id the model invents, or a real id that was not retrieved, is not a citation.

| Field | Type | Rule |
|---|---|---|
| `ticketId` | string | Public id, for example `TKT-1001`. Copied from retrieved metadata. Must exist in the database and in this request's retrieved set. |
| `snippet` | string | Present when a retrieved chunk for that ticket has text. Copied from that chunk (the highest-scoring chunk for the ticket), collapsed to one line and cut at 300 characters. The model does not write the snippet. Omitted only when no chunk text is available. |

```json
{
  "ticketId": "TKT-1001",
  "snippet": "Customer sees code 402 on retry. Gateway retries were exhausting the card."
}
```

The `answer` text cites sources as `[ticket:TKT-1001]`. Every such marker must appear in `citations`, and every `citations` entry must appear in the answer. The server builds `citations` from the retrieved chunks after checking the answer. It does not copy the model's citation list unchecked (FR-13 AC2).

### Grounded answer

HTTP 200.

```json
{
  "answer": "Previous payment failures were caused by gateway retries exhausting the card [ticket:TKT-1001].",
  "grounded": true,
  "citations": [
    {
      "ticketId": "TKT-1001",
      "snippet": "Customer sees code 402 on retry. Gateway retries were exhausting the card."
    }
  ]
}
```

Rules for this body:

- Facts come only from retrieved chunks (RAG-09). A statement that is not in those chunks is left out or labeled as an assumption, and an assumption is not given a citation (RAG-13).
- If the question names a ticket id, that ticket's chunks are retrieved directly and may be cited even when similarity is below the threshold (A-15, RAG-15a).
- The answer must not claim it lists every matching ticket. Retrieval is top-K similarity, not a complete filter (RAG-15).

### No-match response

HTTP 200. This is a normal result, not an error (FR-14 AC3). The UI must not style it as an error (FR-14 AC4).

Used when similarity search and direct ticket-id lookup produce no qualifying context, and when the question is outside support tickets (FR-12 AC5, FR-14 AC5). The chat model is not called (FR-14 AC2).

`answer` is exactly this string and nothing else:

```text
No relevant tickets found.
```

```json
{
  "answer": "No relevant tickets found.",
  "grounded": false,
  "citations": []
}
```

### Citation check failed

If the model answer contains a ticket id that was not retrieved, or contains no valid citation, the model text is discarded (FR-13 AC3, architecture AD-08). The HTTP body is the no-match body above, so the client never displays unverified text.

The log outcome is `CITATION_REJECTED`, which is distinct from `NO_MATCH` (NFR-OBS-02). The response body does not add a field for this, because the public fields are only `answer`, `grounded`, and `citations`.

### Error responses

These are not the no-match body. They use the standard error body.

| HTTP | `code` | When |
|---|---|---|
| 400 | `VALIDATION_ERROR` | Missing, blank, or over-limit `question`, or malformed JSON. |
| 503 | `SERVICE_UNAVAILABLE` | Index is still rebuilding, or the embedding service or chat model is unreachable or times out (FR-12 AC8, NFR-REL-06). The message says the assistant is unavailable. It does not answer the question. |

Example validation error:

```json
{
  "status": 400,
  "error": "Bad Request",
  "code": "VALIDATION_ERROR",
  "message": "Question must not be blank.",
  "path": "/api/ai/ask",
  "timestamp": "2026-11-18T11:00:00Z",
  "fieldErrors": [
    { "field": "question", "message": "must not be blank" }
  ]
}
```

Example when Ollama is down:

```json
{
  "status": 503,
  "error": "Service Unavailable",
  "code": "SERVICE_UNAVAILABLE",
  "message": "The assistant is unavailable. Try again later.",
  "path": "/api/ai/ask",
  "timestamp": "2026-11-18T11:00:00Z"
}
```

## Traceability

| Behavior | Requirements |
|---|---|
| Request shape and validation | FR-12 AC2, A-07 |
| Grounded answer and citations | FR-12 AC1, FR-13, RAG-09, RAG-10, RAG-14 |
| Snippet from retrieved text | FR-13, RAG-02 |
| Exact no-match body | FR-14, RAG-11 |
| Named ticket id lookup | A-15, RAG-15a, FR-12 AC4 |
| No completeness claim | RAG-15 |
| Fail closed on bad citations | FR-13 AC3 |
| 503 rather than a fabricated answer | FR-12 AC8, NFR-REL-03, NFR-REL-06 |
