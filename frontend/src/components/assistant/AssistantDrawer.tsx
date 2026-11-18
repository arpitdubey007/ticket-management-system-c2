"use client";

import Link from "next/link";
import { FormEvent, useState } from "react";

import { askQuestion } from "@/lib/api/ask";
import { bannerMessage, isApiError } from "@/lib/api/errors";
import type { AskResponse } from "@/lib/api/types";
import { LIMITS, NO_MATCH_ANSWER } from "@/lib/constants";
import { Button } from "@/components/ui/Button";
import { ErrorBanner } from "@/components/ui/ErrorBanner";
import { FieldError } from "@/components/ui/FieldError";
import { InfoBanner } from "@/components/ui/InfoBanner";
import { Spinner } from "@/components/ui/Spinner";
import { useAssistantDrawer } from "@/components/assistant/AssistantDrawerContext";

import styles from "./AssistantDrawer.module.css";

export function AssistantDrawer() {
  const { isOpen, closeDrawer } = useAssistantDrawer();
  const [question, setQuestion] = useState("");
  const [clientError, setClientError] = useState("");
  const [serverError, setServerError] = useState("");
  const [loading, setLoading] = useState(false);
  const [result, setResult] = useState<AskResponse | null>(null);

  if (!isOpen) {
    return null;
  }

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    setClientError("");
    setServerError("");
    const trimmed = question.trim();
    if (!trimmed) {
      setClientError("Question must not be blank.");
      return;
    }
    if (trimmed.length > LIMITS.question) {
      setClientError(`Question must be at most ${LIMITS.question} characters.`);
      return;
    }

    setLoading(true);
    setResult(null);
    try {
      const { data } = await askQuestion(trimmed);
      setResult(data);
    } catch (error) {
      setServerError(bannerMessage(error));
      if (isApiError(error) && error.body.fieldErrors?.length) {
        const q = error.body.fieldErrors.find((f) => f.field === "question");
        if (q) {
          setClientError(q.message);
        }
      }
    } finally {
      setLoading(false);
    }
  }

  const isNoMatch =
    result &&
    !result.grounded &&
    result.answer === NO_MATCH_ANSWER &&
    result.citations.length === 0;

  const showMissingSourcesNote =
    result?.grounded === true && result.citations.length === 0;

  return (
    <>
      <button
        type="button"
        className={styles.backdrop}
        aria-label="Close assistant"
        onClick={closeDrawer}
      />
      <aside className={styles.drawer} aria-label="AI assistant">
        <header className={styles.header}>
          <h2 className={styles.title}>Ask assistant</h2>
          <Button variant="ghost" onClick={closeDrawer} aria-label="Close">
            Close
          </Button>
        </header>
        <p className={styles.hint}>
          Answers come only from existing tickets in the knowledge base. Citations
          link to tickets returned by the server.
        </p>

        <form onSubmit={onSubmit} className={styles.form}>
          <label className={styles.label} htmlFor="assistant-question">
            Your question
          </label>
          <textarea
            id="assistant-question"
            className={styles.textarea}
            rows={4}
            maxLength={LIMITS.question}
            value={question}
            onChange={(e) => setQuestion(e.target.value)}
            disabled={loading}
          />
          <FieldError message={clientError} />
          <Button type="submit" disabled={loading}>
            Ask
          </Button>
        </form>

        {loading && <Spinner label="Waiting for an answer…" />}

        {serverError && <ErrorBanner message={serverError} />}

        {result && !loading && (
          <section className={styles.result} aria-live="polite">
            {isNoMatch ? (
              <InfoBanner message={NO_MATCH_ANSWER} />
            ) : (
              <p className={styles.answer}>{result.answer}</p>
            )}

            {showMissingSourcesNote && (
              <p className={styles.note}>
                No source list was returned with this answer.
              </p>
            )}

            {result.citations.length > 0 && (
              <div>
                <h3 className={styles.sourcesTitle}>Sources</h3>
                <ul className={styles.sources}>
                  {result.citations.map((citation) => (
                    <li key={citation.ticketId} className={styles.sourceItem}>
                      <Link
                        href={`/tickets/${encodeURIComponent(citation.ticketId)}`}
                        className={styles.citationLink}
                      >
                        {citation.ticketId}
                      </Link>
                      {citation.snippet ? (
                        <p className={styles.snippet}>{citation.snippet}</p>
                      ) : null}
                    </li>
                  ))}
                </ul>
              </div>
            )}
          </section>
        )}
      </aside>
    </>
  );
}