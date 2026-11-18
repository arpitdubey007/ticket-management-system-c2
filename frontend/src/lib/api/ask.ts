import { apiFetch } from "@/lib/api/client";
import type { AskResponse } from "@/lib/api/types";

export function askQuestion(question: string) {
  return apiFetch<AskResponse>("/api/ai/ask", {
    method: "POST",
    body: JSON.stringify({ question }),
  });
}