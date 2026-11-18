# API Contract

HTTP contract for ticket management.

Status: draft for review. Derived from `spec/requirements.md`, `spec/architecture.md`, `spec/data-model.md`, and `spec/state-machine.md`. This file contains no controller or DTO code. The question-answering endpoint is in `spec/rag-api-contract.md`.

`{publicId}` is the public ticket id, for example `TKT-1001` (`tickets.public_id`). In JSON the same value is `ticketId`.

## Conventions

- Base URL is the backend origin. Bodies are JSON (`Content-Type: application/json`) except where a response has no body.
- Field names are camelCase. Timestamps are ISO-8601 in UTC.
- The backend validates every input (FR-10). The UI checks are not trusted.
- Editable text is trimmed before validation. Blank after trim is invalid (A-07).
- Unknown JSON fields are ignored. Fields that this contract forbids (`ticketId`, `status`, `category`, `resolutionNotes`, `comments`, timestamps) are ignored on create and on update and never change stored values (FR-01 AC3, FR-04 AC3).
- Limits (A-07): title 1–120, description 1–5000, comment content 1–2000, author 1–100, assignee 1–100, resolution notes 1–2000, search term 1–100.
- Priority values: `LOW`, `MEDIUM`, `HIGH`, `CRITICAL`. Default `MEDIUM` (A-03).
- Category values, configured: `PAYMENT`, `SHIPPING`, `ACCOUNT`, `TECHNICAL`, `OTHER` (A-04). Required at creation, immutable afterward.
- Status values and transitions: `spec/state-machine.md`.

### Error body

Every error uses this shape. `code` is the machine-readable value the UI branches on (FR-11). `fieldErrors` is present only for validation and transition errors.

```json
{
  "status": 400,
  "error": "Bad Request",
  "code": "VALIDATION_ERROR",
  "message": "Title must not be blank.",
  "path": "/api/tickets",
  "timestamp": "2026-11-18T11:00:00Z",
  "fieldErrors": [
    { "field": "title", "message": "must not be blank" }
  ]
}
```

| `code` | HTTP | When |
|---|---|---|
| `VALIDATION_ERROR` | 400 | Malformed JSON, wrong types, blank or over-limit fields, unknown enum, bad paging or search term |
| `INVALID_TRANSITION` | 400 | Status change is not in the transition table |
| `NOT_FOUND` | 404 | Unknown `{publicId}` |
| `CONFLICT` | 409 | Edit or comment on a terminal ticket, or a lost optimistic lock |
| `INTERNAL_ERROR` | 500 | Unexpected failure. `message` is generic. No stack trace, SQL, or class name |

### Pagination

`page` is 0-based. `size` defaults to 20 and must be 1–100. Sort is `updatedAt` descending, then `ticketId` descending.

```json
{
  "items": [],
  "page": 0,
  "size": 20,
  "totalItems": 0,
  "totalPages": 0
}
```

### Ticket object

List items omit `description`, `resolutionNotes`, and `comments`. Detail responses include them. `assignee` and `resolutionNotes` are `null` when unset. Comments are oldest first.

```json
{
  "ticketId": "TKT-1001",
  "title": "Card payment declined at checkout",
  "description": "Customer sees code 402 on retry.",
  "category": "PAYMENT",
  "priority": "HIGH",
  "status": "OPEN",
  "assignee": null,
  "resolutionNotes": null,
  "createdAt": "2026-11-18T10:00:00Z",
  "updatedAt": "2026-11-18T10:00:00Z",
  "comments": [
    {
      "author": "Ada",
      "content": "Reproduced on the second attempt.",
      "createdAt": "2026-11-18T10:30:00Z"
    }
  ]
}
```

## GET /api/tickets

List tickets (FR-02). Optional keyword search (FR-07) and status filter (FR-08).

### Query parameters

| Name | Required | Rule |
|---|---|---|
| `q` | no | Case-insensitive substring match on title, description, comment text, and resolution notes (A-08). Omitted or blank means no keyword filter. Longer than 100 characters is 400. Characters such as `%` and `_` are matched literally, not as pattern syntax (FR-07 AC6). |
| `status` | no | One status. Omitted means all statuses. Any other value is 400 and the message lists the five allowed values. |
| `page` | no | Integer ≥ 0. Default 0. |
| `size` | no | Integer 1–100. Default 20. |

`q` and `status` combine with AND (FR-07 AC5). This search does not use embeddings (FR-07 AC7).

### Responses

| HTTP | When |
|---|---|
| 200 | Page of ticket summaries. No matches or no tickets is 200 with `items: []` and `totalItems: 0`, not an error. |
| 400 | `VALIDATION_ERROR` for a bad `q`, `status`, `page`, or `size`. |

Example: `GET /api/tickets?q=payment&status=OPEN&page=0&size=20`

```json
{
  "items": [
    {
      "ticketId": "TKT-1001",
      "title": "Card payment declined at checkout",
      "category": "PAYMENT",
      "priority": "HIGH",
      "status": "OPEN",
      "assignee": null,
      "updatedAt": "2026-11-18T10:00:00Z"
    }
  ],
  "page": 0,
  "size": 20,
  "totalItems": 1,
  "totalPages": 1
}
```

## POST /api/tickets

Create a ticket (FR-01). Status becomes `OPEN`. The server assigns `ticketId`. A successful create requests ingestion (FR-01 AC6).

### Request

```json
{
  "title": "Card payment declined at checkout",
  "description": "Customer sees code 402 on retry.",
  "category": "PAYMENT",
  "priority": "HIGH",
  "assignee": "Ada"
}
```

| Field | Required | Rule |
|---|---|---|
| `title` | yes | 1–120 after trim |
| `description` | yes | 1–5000 after trim |
| `category` | yes | One of the configured categories |
| `priority` | no | One of the four priorities. Omitted defaults to `MEDIUM`. |
| `assignee` | no | 1–100 after trim, or omitted. `null` or omitted means unassigned. `""` is 400. |

### Responses

| HTTP | When |
|---|---|
| 201 | Created. `Location: /api/tickets/{publicId}`. Body is the ticket detail. |
| 400 | `VALIDATION_ERROR`. Nothing is stored. Each invalid field is listed. |

Example 400 when title is missing and category is unknown:

```json
{
  "status": 400,
  "error": "Bad Request",
  "code": "VALIDATION_ERROR",
  "message": "Request validation failed.",
  "path": "/api/tickets",
  "timestamp": "2026-11-18T11:00:00Z",
  "fieldErrors": [
    { "field": "title", "message": "must not be blank" },
    { "field": "category", "message": "must be one of PAYMENT, SHIPPING, ACCOUNT, TECHNICAL, OTHER" }
  ]
}
```

## GET /api/tickets/{publicId}

Ticket detail, including comments and resolution notes (FR-03).

| HTTP | When |
|---|---|
| 200 | The ticket object. |
| 404 | `NOT_FOUND`. Message: `Ticket TKT-1001 was not found.` |

## PUT /api/tickets/{publicId}

Update title, description, priority, and assignee (FR-04). This is a partial update: omitted fields stay as they are. At least one of the four fields must be present. Status cannot be changed here.

`assignee: null` clears the assignee. Omitting `assignee` leaves it unchanged.

### Request

```json
{
  "title": "Card payment declined at checkout",
  "priority": "CRITICAL",
  "assignee": "Grace"
}
```

Validation of present fields matches creation. An invalid field changes nothing.

### Responses

| HTTP | When |
|---|---|
| 200 | Updated ticket detail. `updatedAt` refreshed. Ingestion requested (FR-04 AC6). |
| 400 | `VALIDATION_ERROR`. No editable field present, a present field is invalid, or the body is malformed. |
| 404 | `NOT_FOUND`. |
| 409 | `CONFLICT` when status is `CLOSED` or `CANCELLED` (A-06). Nothing is changed. |

Example 409:

```json
{
  "status": 409,
  "error": "Conflict",
  "code": "CONFLICT",
  "message": "Ticket TKT-1001 is CLOSED and cannot be updated.",
  "path": "/api/tickets/TKT-1001",
  "timestamp": "2026-11-18T11:00:00Z"
}
```

## POST /api/tickets/{publicId}/comments

Append a comment (FR-06). Comments cannot be edited or deleted.

### Request

```json
{
  "author": "Ada",
  "content": "Reproduced on the second attempt."
}
```

Both fields are required, trimmed, and length-checked (`author` 1–100, `content` 1–2000).

### Responses

| HTTP | When |
|---|---|
| 201 | The created comment. The ticket's `updatedAt` and `version` change, and ingestion is requested (FR-06 AC5). |
| 400 | `VALIDATION_ERROR`. |
| 404 | `NOT_FOUND`. |
| 409 | `CONFLICT` when the ticket is `CLOSED` or `CANCELLED`. |

```json
{
  "author": "Ada",
  "content": "Reproduced on the second attempt.",
  "createdAt": "2026-11-18T10:30:00Z"
}
```

## POST /api/tickets/{publicId}/status

Apply one transition (FR-05). Rules, the matrix, and error examples are in `spec/state-machine.md`. Invalid transitions are HTTP 400, not 409.

### Request

```json
{
  "status": "IN_PROGRESS"
}
```

Resolving a ticket:

```json
{
  "status": "RESOLVED",
  "resolutionNotes": "Gateway retries were exhausting the card. Disabled the duplicate retry."
}
```

`resolutionNotes` is required only when `status` is `RESOLVED`. It is ignored on other transitions. Length 1–2000 after trim.

### Responses

| HTTP | When |
|---|---|
| 200 | Updated ticket detail. |
| 400 | `INVALID_TRANSITION` or `VALIDATION_ERROR`. Ticket unchanged. |
| 404 | `NOT_FOUND`. |
| 409 | `CONFLICT` when a concurrent valid transition won the version check. |

Success example after `OPEN → IN_PROGRESS`: the ticket detail with `"status": "IN_PROGRESS"`.

## Traceability

| Endpoint | Requirements |
|---|---|
| `GET /api/tickets` | FR-02, FR-07, FR-08, FR-10 |
| `POST /api/tickets` | FR-01, FR-10, RAG-04 |
| `GET /api/tickets/{publicId}` | FR-03 |
| `PUT /api/tickets/{publicId}` | FR-04, A-06, RAG-04 |
| `POST /api/tickets/{publicId}/comments` | FR-06, A-06, RAG-04 |
| `POST /api/tickets/{publicId}/status` | FR-05, section 5, RAG-04 |
