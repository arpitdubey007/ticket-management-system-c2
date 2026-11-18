---
name: documentation
description: Placeholder skill for writing and updating project documentation. Use when the user asks to draft or revise docs, specs, or the README.
disable-model-invocation: true
---

# Documentation

When updating project docs after implementation:

1. Record **actual** test counts in `docs/test-execution-report.md` (from `mvn verify` and `npm run test`).
2. Link SpecStory sessions in `docs/prompt-history.md` (paths under `.specstory/history/`).
3. Log proven AI errors in `docs/ai-mistakes.md` with Maven/spec evidence.
4. Record stack and waivers in `docs/architecture-decisions.md`.
5. Update submission status in `docs/final-review.md` — do not mark requirements **Verified** without test reference.
6. Keep `README.md` prerequisites, limitations, and commands in sync.
