"use client";

import { LIMITS, STATUS_LABELS, TICKET_STATUSES } from "@/lib/constants";
import type { TicketStatus } from "@/lib/constants";
import { FieldError } from "@/components/ui/FieldError";

import styles from "./TicketFilters.module.css";

interface TicketFiltersProps {
  query: string;
  status: string;
  searchError: string;
  onQueryChange: (value: string) => void;
  onStatusChange: (value: string) => void;
}

export function TicketFilters({
  query,
  status,
  searchError,
  onQueryChange,
  onStatusChange,
}: TicketFiltersProps) {
  return (
    <div className={styles.row}>
      <div className={styles.field}>
        <label className={styles.label} htmlFor="ticket-search">
          Search
        </label>
        <input
          id="ticket-search"
          className={styles.input}
          type="search"
          value={query}
          maxLength={LIMITS.searchTerm + 1}
          placeholder="Title, description, comments…"
          onChange={(e) => onQueryChange(e.target.value)}
        />
        <FieldError message={searchError} />
      </div>
      <div className={styles.field}>
        <label className={styles.label} htmlFor="ticket-status-filter">
          Status
        </label>
        <select
          id="ticket-status-filter"
          className={styles.select}
          value={status}
          onChange={(e) => onStatusChange(e.target.value)}
        >
          <option value="">All statuses</option>
          {TICKET_STATUSES.map((s) => (
            <option key={s} value={s}>
              {STATUS_LABELS[s as TicketStatus]}
            </option>
          ))}
        </select>
      </div>
    </div>
  );
}