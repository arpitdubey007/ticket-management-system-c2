"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { useCallback, useEffect, useState } from "react";

import {
  addComment,
  changeTicketStatus,
  getTicket,
  updateTicket,
} from "@/lib/api/tickets";
import {
  ApiError,
  bannerMessage,
  fieldErrorsByName,
  isApiError,
} from "@/lib/api/errors";
import type { TicketDetail } from "@/lib/api/types";
import type { TicketStatus } from "@/lib/constants";
import { STATUS_LABELS } from "@/lib/constants";
import { displayAssignee, formatDateTime } from "@/lib/format";
import { Button } from "@/components/ui/Button";
import { ErrorBanner } from "@/components/ui/ErrorBanner";
import { Spinner } from "@/components/ui/Spinner";
import { CommentForm } from "@/components/tickets/CommentForm";
import { CommentTimeline } from "@/components/tickets/CommentTimeline";
import { StatusControls } from "@/components/tickets/StatusControls";
import {
  TicketForm,
  type TicketFormValues,
} from "@/components/tickets/TicketForm";

import styles from "./page.module.css";

function isTerminal(status: TicketDetail["status"]): boolean {
  return status === "CLOSED" || status === "CANCELLED";
}

function toFormValues(ticket: TicketDetail): TicketFormValues {
  return {
    title: ticket.title,
    description: ticket.description,
    category: ticket.category,
    priority: ticket.priority,
    assignee: ticket.assignee ?? "",
  };
}

export function TicketDetailPage() {
  const params = useParams<{ ticketId: string }>();
  const ticketId = params.ticketId;

  const [ticket, setTicket] = useState<TicketDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [notFound, setNotFound] = useState(false);
  const [banner, setBanner] = useState("");
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [editing, setEditing] = useState(false);
  const [editValues, setEditValues] = useState<TicketFormValues | null>(null);
  const [saving, setSaving] = useState(false);

  const loadTicket = useCallback(async () => {
    setLoading(true);
    setBanner("");
    try {
      const { data } = await getTicket(ticketId);
      setTicket(data);
      setNotFound(false);
      setEditValues(toFormValues(data));
    } catch (error) {
      if (isApiError(error) && error.body.code === "NOT_FOUND") {
        setNotFound(true);
        setTicket(null);
      } else {
        setBanner(bannerMessage(error));
      }
    } finally {
      setLoading(false);
    }
  }, [ticketId]);

  useEffect(() => {
    void loadTicket();
  }, [loadTicket]);

  async function onSaveEdit() {
    if (!ticket || !editValues) {
      return;
    }
    setSaving(true);
    setBanner("");
    setFieldErrors({});
    try {
      const body: Parameters<typeof updateTicket>[1] = {};
      if (editValues.title !== ticket.title) {
        body.title = editValues.title;
      }
      if (editValues.description !== ticket.description) {
        body.description = editValues.description;
      }
      if (editValues.priority !== ticket.priority) {
        body.priority = editValues.priority;
      }
      const currentAssignee = ticket.assignee ?? "";
      if (editValues.assignee !== currentAssignee) {
        body.assignee = editValues.assignee.trim() ? editValues.assignee.trim() : null;
      }

      const { data } = await updateTicket(ticket.ticketId, body);
      setTicket(data);
      setEditValues(toFormValues(data));
      setEditing(false);
    } catch (error) {
      if (isApiError(error)) {
        setFieldErrors(fieldErrorsByName(error.body.fieldErrors));
        setBanner(bannerMessage(error));
        if (error.body.code === "CONFLICT") {
          await loadTicket();
        }
      } else {
        setBanner(bannerMessage(error));
      }
    } finally {
      setSaving(false);
    }
  }

  async function onStatus(status: TicketStatus, resolutionNotes?: string) {
    if (!ticket) {
      return;
    }
    setBanner("");
    setFieldErrors({});
    try {
      const { data } = await changeTicketStatus(ticket.ticketId, {
        status,
        resolutionNotes,
      });
      setTicket(data);
      setEditValues(toFormValues(data));
    } catch (error) {
      if (error instanceof ApiError) {
        setFieldErrors(fieldErrorsByName(error.body.fieldErrors));
        setBanner(error.body.message);
        if (error.body.code === "INVALID_TRANSITION" || error.body.code === "CONFLICT") {
          await loadTicket();
        }
      } else {
        setBanner(bannerMessage(error));
      }
    }
  }

  async function onAddComment(author: string, content: string) {
    if (!ticket) {
      return;
    }
    setBanner("");
    setFieldErrors({});
    try {
      await addComment(ticket.ticketId, { author, content });
      await loadTicket();
    } catch (error) {
      if (isApiError(error)) {
        setFieldErrors(fieldErrorsByName(error.body.fieldErrors));
        setBanner(bannerMessage(error));
        if (error.body.code === "CONFLICT" || error.body.code === "NOT_FOUND") {
          await loadTicket();
        }
      } else {
        setBanner(bannerMessage(error));
      }
    }
  }

  if (loading) {
    return <Spinner label="Loading ticket…" />;
  }

  if (notFound) {
    return (
      <div className={styles.notFound}>
        <h1>This ticket was not found</h1>
        <Link href="/tickets">Back to ticket list</Link>
      </div>
    );
  }

  if (!ticket) {
    return <ErrorBanner message={banner || "Unable to load this ticket."} />;
  }

  const terminal = isTerminal(ticket.status);

  return (
    <div className={styles.page}>
      <div className={styles.header}>
        <div>
          <Link href="/tickets" className={styles.back}>
            ← Back to list
          </Link>
          <h1 className={styles.title}>
            {ticket.ticketId} · {ticket.title}
          </h1>
        </div>
        {!terminal && !editing && (
          <Button type="button" variant="secondary" onClick={() => setEditing(true)}>
            Edit
          </Button>
        )}
      </div>

      <ErrorBanner message={banner} />

      {terminal && (
        <p className={styles.terminalNote}>
          This ticket is {STATUS_LABELS[ticket.status]} and can no longer be changed.
        </p>
      )}

      <section className={styles.metaGrid}>
        <Meta label="Status" value={STATUS_LABELS[ticket.status]} />
        <Meta label="Priority" value={ticket.priority} />
        <Meta label="Category" value={ticket.category} />
        <Meta label="Assignee" value={displayAssignee(ticket.assignee)} />
        <Meta label="Created" value={formatDateTime(ticket.createdAt)} />
        <Meta label="Updated" value={formatDateTime(ticket.updatedAt)} />
      </section>

      {editing && editValues ? (
        <section className={styles.section}>
          <h2 className={styles.sectionTitle}>Edit ticket</h2>
          <TicketForm
            mode="edit"
            values={editValues}
            fieldErrors={fieldErrors}
            disabled={saving}
            onChange={setEditValues}
          />
          <div className={styles.editActions}>
            <Button type="button" disabled={saving} onClick={() => void onSaveEdit()}>
              Save
            </Button>
            <Button
              type="button"
              variant="secondary"
              disabled={saving}
              onClick={() => {
                setEditing(false);
                setEditValues(toFormValues(ticket));
                setFieldErrors({});
              }}
            >
              Cancel edit
            </Button>
          </div>
        </section>
      ) : (
        <>
          <section className={styles.section}>
            <h2 className={styles.sectionTitle}>Description</h2>
            <p className={styles.bodyText}>{ticket.description}</p>
          </section>
          {ticket.resolutionNotes && (
            <section className={styles.section}>
              <h2 className={styles.sectionTitle}>Resolution notes</h2>
              <p className={styles.bodyText}>{ticket.resolutionNotes}</p>
            </section>
          )}
        </>
      )}

      <StatusControls
        allowedTransitions={ticket.allowedTransitions}
        disabled={terminal}
        fieldErrors={fieldErrors}
        onTransition={onStatus}
      />

      <section className={styles.section}>
        <h2 className={styles.sectionTitle}>Comments</h2>
        <CommentTimeline comments={ticket.comments} />
        {!terminal && (
          <CommentForm
            disabled={terminal}
            fieldErrors={fieldErrors}
            onSubmit={onAddComment}
          />
        )}
      </section>
    </div>
  );
}

function Meta({ label, value }: { label: string; value: string }) {
  return (
    <div className={styles.metaItem}>
      <span className={styles.metaLabel}>{label}</span>
      <span className={styles.metaValue}>{value}</span>
    </div>
  );
}
