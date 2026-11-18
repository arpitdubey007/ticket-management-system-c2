export const TICKET_STATUSES = [
  "OPEN",
  "IN_PROGRESS",
  "RESOLVED",
  "CLOSED",
  "CANCELLED",
] as const;

export type TicketStatus = (typeof TICKET_STATUSES)[number];

export const TICKET_PRIORITIES = ["LOW", "MEDIUM", "HIGH", "CRITICAL"] as const;

export type TicketPriority = (typeof TICKET_PRIORITIES)[number];

export const TICKET_CATEGORIES = [
  "PAYMENT",
  "SHIPPING",
  "ACCOUNT",
  "TECHNICAL",
  "OTHER",
] as const;

export type TicketCategory = (typeof TICKET_CATEGORIES)[number];

export const LIMITS = {
  title: 120,
  description: 5000,
  commentContent: 2000,
  author: 100,
  assignee: 100,
  resolutionNotes: 2000,
  searchTerm: 100,
  question: 500,
} as const;

export const DEFAULT_PAGE_SIZE = 20;

export const NO_MATCH_ANSWER = "No relevant tickets found.";

export const STATUS_LABELS: Record<TicketStatus, string> = {
  OPEN: "Open",
  IN_PROGRESS: "In progress",
  RESOLVED: "Resolved",
  CLOSED: "Closed",
  CANCELLED: "Cancelled",
};

export const TRANSITION_LABELS: Record<string, string> = {
  IN_PROGRESS: "Start progress",
  CANCELLED: "Cancel ticket",
  RESOLVED: "Resolve",
  CLOSED: "Close ticket",
};