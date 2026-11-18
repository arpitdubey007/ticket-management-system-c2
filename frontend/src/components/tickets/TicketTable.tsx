import Link from "next/link";

import type { TicketSummary } from "@/lib/api/types";
import { displayAssignee, formatDateTime } from "@/lib/format";
import { STATUS_LABELS } from "@/lib/constants";
import type { TicketStatus } from "@/lib/constants";

import styles from "./TicketTable.module.css";

export function TicketTable({ items }: { items: TicketSummary[] }) {
  return (
    <div className={styles.wrap}>
      <table className={styles.table}>
        <thead>
          <tr>
            <th>Ticket</th>
            <th>Title</th>
            <th>Status</th>
            <th>Priority</th>
            <th>Assignee</th>
            <th>Category</th>
            <th>Updated</th>
          </tr>
        </thead>
        <tbody>
          {items.map((ticket) => (
            <tr key={ticket.ticketId}>
              <td>
                <Link href={`/tickets/${ticket.ticketId}`} className={styles.link}>
                  {ticket.ticketId}
                </Link>
              </td>
              <td>{ticket.title}</td>
              <td>{STATUS_LABELS[ticket.status as TicketStatus]}</td>
              <td>{ticket.priority}</td>
              <td>{displayAssignee(ticket.assignee)}</td>
              <td>{ticket.category}</td>
              <td>{formatDateTime(ticket.updatedAt)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}