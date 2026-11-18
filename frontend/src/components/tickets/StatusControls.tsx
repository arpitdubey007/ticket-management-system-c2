"use client";

import { FormEvent, useState } from "react";

import { LIMITS, TRANSITION_LABELS } from "@/lib/constants";
import type { TicketStatus } from "@/lib/constants";
import { Button } from "@/components/ui/Button";
import { FieldError } from "@/components/ui/FieldError";

import styles from "./StatusControls.module.css";

interface StatusControlsProps {
  allowedTransitions: string[];
  disabled?: boolean;
  fieldErrors: Record<string, string>;
  onTransition: (status: TicketStatus, resolutionNotes?: string) => Promise<void>;
}

export function StatusControls({
  allowedTransitions,
  disabled,
  fieldErrors,
  onTransition,
}: StatusControlsProps) {
  const [resolving, setResolving] = useState(false);
  const [notes, setNotes] = useState("");
  const [pending, setPending] = useState(false);

  if (allowedTransitions.length === 0) {
    return null;
  }

  async function handleResolve(event: FormEvent) {
    event.preventDefault();
    setPending(true);
    try {
      await onTransition("RESOLVED", notes);
      setResolving(false);
      setNotes("");
    } finally {
      setPending(false);
    }
  }

  async function handleImmediate(status: TicketStatus) {
    setPending(true);
    try {
      await onTransition(status);
    } finally {
      setPending(false);
    }
  }

  return (
    <section className={styles.section}>
      <h2 className={styles.heading}>Change status</h2>
      <div className={styles.actions}>
        {allowedTransitions.map((status) => {
          if (status === "RESOLVED") {
            return (
              <Button
                key={status}
                type="button"
                variant="secondary"
                disabled={disabled || pending}
                onClick={() => setResolving((open) => !open)}
              >
                {TRANSITION_LABELS[status] ?? status}
              </Button>
            );
          }
          return (
            <Button
              key={status}
              type="button"
              variant="secondary"
              disabled={disabled || pending}
              onClick={() => handleImmediate(status as TicketStatus)}
            >
              {TRANSITION_LABELS[status] ?? status}
            </Button>
          );
        })}
      </div>
      {resolving && (
        <form onSubmit={handleResolve} className={styles.resolveForm}>
          <label className={styles.label} htmlFor="resolution-notes">
            Resolution notes
          </label>
          <textarea
            id="resolution-notes"
            className={styles.textarea}
            rows={4}
            maxLength={LIMITS.resolutionNotes}
            value={notes}
            disabled={disabled || pending}
            onChange={(e) => setNotes(e.target.value)}
          />
          <FieldError message={fieldErrors.resolutionNotes} />
          <div className={styles.resolveActions}>
            <Button type="submit" disabled={disabled || pending}>
              Save resolution
            </Button>
            <Button
              type="button"
              variant="ghost"
              disabled={pending}
              onClick={() => setResolving(false)}
            >
              Cancel
            </Button>
          </div>
        </form>
      )}
    </section>
  );
}