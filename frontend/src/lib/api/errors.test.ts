import { describe, expect, it } from "vitest";

import { ApiError, NetworkError, bannerMessage } from "@/lib/api/errors";

describe("bannerMessage", () => {
  it("maps network errors", () => {
    expect(bannerMessage(new NetworkError())).toBe("The server could not be reached.");
  });

  it("maps assistant outage", () => {
    const error = new ApiError({
      status: 503,
      error: "Service Unavailable",
      code: "SERVICE_UNAVAILABLE",
      message: "The assistant is unavailable. Try again later.",
      path: "/api/ai/ask",
      timestamp: "2026-01-01T00:00:00Z",
    });
    expect(bannerMessage(error)).toBe(
      "The assistant is unavailable. Try again later.",
    );
  });
});