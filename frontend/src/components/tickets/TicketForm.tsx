"use client";

import {
  LIMITS,
  TICKET_CATEGORIES,
  TICKET_PRIORITIES,
} from "@/lib/constants";
import type { TicketCategory, TicketPriority } from "@/lib/constants";
import { FieldError } from "@/components/ui/FieldError";

import styles from "./TicketForm.module.css";

export interface TicketFormValues {
  title: string;
  description: string;
  category: TicketCategory | "";
  priority: TicketPriority;
  assignee: string;
}

interface TicketFormProps {
  values: TicketFormValues;
  fieldErrors: Record<string, string>;
  mode: "create" | "edit";
  disabled?: boolean;
  onChange: (values: TicketFormValues) => void;
}

export function TicketForm({
  values,
  fieldErrors,
  mode,
  disabled,
  onChange,
}: TicketFormProps) {
  return (
    <div className={styles.form}>
      <div className={styles.field}>
        <label className={styles.label} htmlFor="title">
          Title
        </label>
        <input
          id="title"
          className={styles.input}
          value={values.title}
          maxLength={LIMITS.title}
          disabled={disabled}
          onChange={(e) => onChange({ ...values, title: e.target.value })}
        />
        <FieldError message={fieldErrors.title} />
      </div>
      <div className={styles.field}>
        <label className={styles.label} htmlFor="description">
          Description
        </label>
        <textarea
          id="description"
          className={styles.textarea}
          rows={8}
          maxLength={LIMITS.description}
          value={values.description}
          disabled={disabled}
          onChange={(e) => onChange({ ...values, description: e.target.value })}
        />
        <FieldError message={fieldErrors.description} />
      </div>
      {mode === "create" && (
        <div className={styles.field}>
          <label className={styles.label} htmlFor="category">
            Category
          </label>
          <select
            id="category"
            className={styles.select}
            value={values.category}
            disabled={disabled}
            onChange={(e) =>
              onChange({ ...values, category: e.target.value as TicketCategory | "" })
            }
          >
            <option value="">Select category</option>
            {TICKET_CATEGORIES.map((c) => (
              <option key={c} value={c}>
                {c}
              </option>
            ))}
          </select>
          <FieldError message={fieldErrors.category} />
        </div>
      )}
      <div className={styles.field}>
        <label className={styles.label} htmlFor="priority">
          Priority
        </label>
        <select
          id="priority"
          className={styles.select}
          value={values.priority}
          disabled={disabled}
          onChange={(e) =>
            onChange({ ...values, priority: e.target.value as TicketPriority })
          }
        >
          {TICKET_PRIORITIES.map((p) => (
            <option key={p} value={p}>
              {p}
            </option>
          ))}
        </select>
        <FieldError message={fieldErrors.priority} />
      </div>
      <div className={styles.field}>
        <label className={styles.label} htmlFor="assignee">
          Assignee
        </label>
        <input
          id="assignee"
          className={styles.input}
          value={values.assignee}
          maxLength={LIMITS.assignee}
          disabled={disabled}
          onChange={(e) => onChange({ ...values, assignee: e.target.value })}
        />
        <FieldError message={fieldErrors.assignee} />
      </div>
    </div>
  );
}