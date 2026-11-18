import type { TicketCategory, TicketPriority, TicketStatus } from "@/lib/constants";

export interface FieldError {
  field: string;
  message: string;
}

export type ApiErrorCode =
  | "VALIDATION_ERROR"
  | "INVALID_TRANSITION"
  | "NOT_FOUND"
  | "CONFLICT"
  | "SERVICE_UNAVAILABLE"
  | "INTERNAL_ERROR"
  | string;

export interface ApiErrorBody {
  status: number;
  error: string;
  code: ApiErrorCode;
  message: string;
  path: string;
  timestamp: string;
  fieldErrors?: FieldError[];
}

export interface PageResponse<T> {
  items: T[];
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
}

export interface Comment {
  author: string;
  content: string;
  createdAt: string;
}

export interface TicketSummary {
  ticketId: string;
  title: string;
  category: TicketCategory;
  priority: TicketPriority;
  status: TicketStatus;
  assignee: string | null;
  updatedAt: string;
}

export interface TicketDetail {
  ticketId: string;
  title: string;
  description: string;
  category: TicketCategory;
  priority: TicketPriority;
  status: TicketStatus;
  assignee: string | null;
  resolutionNotes: string | null;
  createdAt: string;
  updatedAt: string;
  allowedTransitions: string[];
  comments: Comment[];
}

export interface Citation {
  ticketId: string;
  snippet?: string | null;
}

export interface AskResponse {
  answer: string;
  grounded: boolean;
  citations: Citation[];
}