# Support Ticket Management System

## Run locally

1. **Backend** (port 8080): `cd backend && ./mvnw spring-boot:run`
2. **Frontend** (port 3000): `cd frontend && cp .env.example .env.local && npm run dev`

The Next.js app rewrites `/api/*` to `BACKEND_URL` (default `http://127.0.0.1:8080`).

## Frontend checks

```bash
cd frontend
npm run lint
npm run typecheck
npm run test
npm run build
```

Specifications live under `spec/`.
