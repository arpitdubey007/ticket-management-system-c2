import { ApiError, NetworkError } from "@/lib/api/errors";
import type { ApiErrorBody } from "@/lib/api/types";

const JSON_HEADERS = {
  Accept: "application/json",
  "Content-Type": "application/json",
};

export async function apiFetch<T>(
  path: string,
  init?: RequestInit,
): Promise<{ data: T; response: Response }> {
  let response: Response;
  try {
    response = await fetch(path, {
      ...init,
      headers: {
        ...JSON_HEADERS,
        ...(init?.headers ?? {}),
      },
    });
  } catch {
    throw new NetworkError();
  }

  const text = await response.text();
  let parsed: unknown = null;
  if (text) {
    try {
      parsed = JSON.parse(text);
    } catch {
      if (!response.ok) {
        throw new NetworkError();
      }
    }
  }

  if (!response.ok) {
    if (parsed && typeof parsed === "object" && parsed !== null && "code" in parsed) {
      throw new ApiError(parsed as ApiErrorBody);
    }
    throw new NetworkError();
  }

  return { data: parsed as T, response };
}