"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { FormEvent, useState } from "react";

import { createTicket } from "@/lib/api/tickets";
import { fieldErrorsByName, isApiError, bannerMessage } from "@/lib/api/errors";
import { Button } from "@/components/ui/Button";
import { ErrorBanner } from "@/components/ui/ErrorBanner";
import {
  TicketForm,
  type TicketFormValues,
} from "@/components/tickets/TicketForm";

import styles from "./page.module.css";

const INITIAL: TicketFormValues = {
  title: "",
  description: "",
  category: "",
  priority: "MEDIUM",
  assignee: "",
};

export default function CreateTicketPage() {
  const router = useRouter();
  const [values, setValues] = useState<TicketFormValues>(INITIAL);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [banner, setBanner] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [dirty, setDirty] = useState(false);

  function onChange(next: TicketFormValues) {
    setDirty(true);
    setValues(next);
  }

  function onCancel() {
    if (dirty && !window.confirm("Discard this ticket?")) {
      return;
    }
    router.push("/tickets");
  }

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    setSubmitting(true);
    setBanner("");
    setFieldErrors({});
    try {
      const { data } = await createTicket({
        title: values.title,
        description: values.description,
        category: values.category as import("@/lib/constants").TicketCategory,
        priority: values.priority,
        assignee: values.assignee.trim() || undefined,
      });
      router.push(`/tickets/${data.ticketId}`);
    } catch (error) {
      if (isApiError(error)) {
        setFieldErrors(fieldErrorsByName(error.body.fieldErrors));
        setBanner(bannerMessage(error));
      } else {
        setBanner(bannerMessage(error));
      }
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className={styles.page}>
      <div className={styles.header}>
        <h1 className={styles.title}>New ticket</h1>
        <Link href="/tickets" className={styles.back}>
          Back to list
        </Link>
      </div>
      <ErrorBanner message={banner} />
      <form onSubmit={onSubmit} className={styles.form}>
        <TicketForm
          values={values}
          fieldErrors={fieldErrors}
          mode="create"
          disabled={submitting}
          onChange={onChange}
        />
        <div className={styles.actions}>
          <Button type="submit" disabled={submitting}>
            Create ticket
          </Button>
          <Button type="button" variant="secondary" onClick={onCancel} disabled={submitting}>
            Cancel
          </Button>
        </div>
      </form>
    </div>
  );
}