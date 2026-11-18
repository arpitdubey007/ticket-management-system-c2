import { describe, expect, it } from "vitest";

import { fieldErrorsByName } from "@/lib/api/errors";
import { LIMITS, NO_MATCH_ANSWER } from "@/lib/constants";
import { displayAssignee } from "@/lib/format";

describe("client validation constants", () => {
  it("matches backend ask question limit", () => {
    expect(LIMITS.question).toBe(500);
    expect(LIMITS.searchTerm).toBe(100);
  });

  it("uses exact no-match sentence", () => {
    expect(NO_MATCH_ANSWER).toBe("No relevant tickets found.");
  });
});

describe("fieldErrorsByName", () => {
  it("maps backend field errors", () => {
    expect(
      fieldErrorsByName([
        { field: "title", message: "must not be blank" },
        { field: "question", message: "too long" },
      ]),
    ).toEqual({
      title: "must not be blank",
      question: "too long",
    });
  });
});

describe("displayAssignee", () => {
  it("shows Unassigned for empty values", () => {
    expect(displayAssignee(null)).toBe("Unassigned");
    expect(displayAssignee("")).toBe("Unassigned");
    expect(displayAssignee("Ada")).toBe("Ada");
  });
});