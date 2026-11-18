import { describe, expect, it } from "vitest";

import { NO_MATCH_ANSWER } from "@/lib/constants";

describe("Assistant citation display rules", () => {
  it("uses only server citations for source links", () => {
    const apiCitations = [{ ticketId: "TKT-1001", snippet: "Seen 402 on retry." }];
    const invented = "TKT-9999";
    const renderedIds = apiCitations.map((c) => c.ticketId);
    expect(renderedIds).not.toContain(invented);
    expect(renderedIds).toEqual(["TKT-1001"]);
    expect(NO_MATCH_ANSWER).toBe("No relevant tickets found.");
  });
});