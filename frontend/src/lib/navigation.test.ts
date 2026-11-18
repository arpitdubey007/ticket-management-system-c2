import { describe, expect, it } from "vitest";

export function ticketDetailPath(ticketId: string): string {
  return `/tickets/${encodeURIComponent(ticketId)}`;
}

describe("ticket citation navigation", () => {
  it("builds detail paths for cited tickets only", () => {
    const citations = [{ ticketId: "TKT-1001" }, { ticketId: "TKT-1002" }];
    const paths = citations.map((c) => ticketDetailPath(c.ticketId));
    expect(paths).toEqual(["/tickets/TKT-1001", "/tickets/TKT-1002"]);
    expect(paths).not.toContain("/tickets/TKT-9999");
  });
});