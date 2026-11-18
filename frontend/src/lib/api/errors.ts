import type { ApiErrorBody, FieldError } from "@/lib/api/types";

export class ApiError extends Error {
  readonly body: ApiErrorBody;

  constructor(body: ApiErrorBody) {
    super(body.message);
    this.name = "ApiError";
    this.body = body;
  }
}

export class NetworkError extends Error {
  constructor() {
    super("The server could not be reached.");
    this.name = "NetworkError";
  }
}

export function isApiError(error: unknown): error is ApiError {
  return error instanceof ApiError;
}

export function fieldErrorsByName(
  fieldErrors: FieldError[] | undefined,
): Record<string, string> {
  if (!fieldErrors) {
    return {};
  }
  return Object.fromEntries(fieldErrors.map((fe) => [fe.field, fe.message]));
}

export function bannerMessage(error: unknown): string {
  if (error instanceof NetworkError) {
    return error.message;
  }
  if (error instanceof ApiError) {
    const { code, message } = error.body;
    if (code === "SERVICE_UNAVAILABLE") {
      return message || "The assistant is unavailable. Try again later.";
    }
    if (code === "ANSWER_NOT_VERIFIED") {
      return message || "The assistant could not verify the answer. Try again later.";
    }
    if (code === "INTERNAL_ERROR" || !code) {
      return "Something went wrong. Try again.";
    }
    return message;
  }
  return "Something went wrong. Try again.";
}