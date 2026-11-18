import { afterEach, describe, expect, it, vi } from "vitest";

import { apiFetch } from "@/lib/api/client";
import { ApiError, NetworkError } from "@/lib/api/errors";

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("apiFetch", () => {
  it("parses API error bodies", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue({
        ok: false,
        text: async () =>
          JSON.stringify({
            status: 400,
            error: "Bad Request",
            code: "VALIDATION_ERROR",
            message: "Question must not be blank.",
            path: "/api/ai/ask",
            timestamp: "2026-01-01T00:00:00Z",
          }),
      }),
    );

    await expect(apiFetch("/api/ai/ask")).rejects.toBeInstanceOf(ApiError);
  });

  it("throws network error for non-JSON failures", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue({
        ok: false,
        text: async () => "bad gateway",
      }),
    );

    await expect(apiFetch("/api/tickets")).rejects.toBeInstanceOf(NetworkError);
  });
});