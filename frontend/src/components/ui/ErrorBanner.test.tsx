import React from "react";
import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { ErrorBanner } from "@/components/ui/ErrorBanner";

describe("ErrorBanner", () => {
  it("renders API error text for agents", () => {
    render(<ErrorBanner message="The server could not be reached." />);
    expect(screen.getByRole("alert")).toHaveTextContent(
      "The server could not be reached.",
    );
  });

  it("renders nothing when message is empty", () => {
    const { container } = render(<ErrorBanner message="" />);
    expect(container).toBeEmptyDOMElement();
  });
});