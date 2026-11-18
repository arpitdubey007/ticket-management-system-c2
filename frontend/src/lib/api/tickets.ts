import { apiFetch } from "@/lib/api/client";
import type {
  Comment,
  PageResponse,
  TicketDetail,
  TicketSummary,
} from "@/lib/api/types";
import type { TicketCategory, TicketPriority, TicketStatus } from "@/lib/constants";

export interface ListTicketsParams {
  q?: string;
  status?: TicketStatus;
  page?: number;
  size?: number;
}

export function listTickets(params: ListTicketsParams = {}) {
  const search = new URLSearchParams();
  if (params.q) {
    search.set("q", params.q);
  }
  if (params.status) {
    search.set("status", params.status);
  }
  if (params.page !== undefined) {
    search.set("page", String(params.page));
  }
  if (params.size !== undefined) {
    search.set("size", String(params.size));
  }
  const query = search.toString();
  return apiFetch<PageResponse<TicketSummary>>(
    `/api/tickets${query ? `?${query}` : ""}`,
  );
}

export function getTicket(ticketId: string) {
  return apiFetch<TicketDetail>(`/api/tickets/${encodeURIComponent(ticketId)}`);
}

export interface CreateTicketInput {
  title: string;
  description: string;
  category: TicketCategory;
  priority?: TicketPriority;
  assignee?: string;
}

export function createTicket(body: CreateTicketInput) {
  return apiFetch<TicketDetail>("/api/tickets", {
    method: "POST",
    body: JSON.stringify(body),
  });
}

export interface UpdateTicketInput {
  title?: string;
  description?: string;
  priority?: TicketPriority;
  assignee?: string | null;
}

export function updateTicket(ticketId: string, body: UpdateTicketInput) {
  return apiFetch<TicketDetail>(`/api/tickets/${encodeURIComponent(ticketId)}`, {
    method: "PUT",
    body: JSON.stringify(body),
  });
}

export interface ChangeStatusInput {
  status: TicketStatus;
  resolutionNotes?: string;
}

export function changeTicketStatus(ticketId: string, body: ChangeStatusInput) {
  return apiFetch<TicketDetail>(
    `/api/tickets/${encodeURIComponent(ticketId)}/status`,
    {
      method: "POST",
      body: JSON.stringify(body),
    },
  );
}

export interface CreateCommentInput {
  author: string;
  content: string;
}

export function addComment(ticketId: string, body: CreateCommentInput) {
  return apiFetch<Comment>(
    `/api/tickets/${encodeURIComponent(ticketId)}/comments`,
    {
      method: "POST",
      body: JSON.stringify(body),
    },
  );
}