"use client";

import { FormEvent, useEffect, useState } from "react";

import { LIMITS } from "@/lib/constants";
import { getLastAuthor, setLastAuthor } from "@/lib/session";
import { Button } from "@/components/ui/Button";
import { FieldError } from "@/components/ui/FieldError";

import styles from "./CommentForm.module.css";

interface CommentFormProps {
  disabled?: boolean;
  fieldErrors: Record<string, string>;
  onSubmit: (author: string, content: string) => Promise<void>;
}

export function CommentForm({ disabled, fieldErrors, onSubmit }: CommentFormProps) {
  const [author, setAuthor] = useState("");
  const [content, setContent] = useState("");
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    setAuthor(getLastAuthor());
  }, []);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setSubmitting(true);
    try {
      await onSubmit(author, content);
      setLastAuthor(author);
      setContent("");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <form onSubmit={handleSubmit} className={styles.form}>
      <div className={styles.field}>
        <label className={styles.label} htmlFor="comment-author">
          Author
        </label>
        <input
          id="comment-author"
          className={styles.input}
          value={author}
          maxLength={LIMITS.author}
          disabled={disabled || submitting}
          onChange={(e) => setAuthor(e.target.value)}
        />
        <FieldError message={fieldErrors.author} />
      </div>
      <div className={styles.field}>
        <label className={styles.label} htmlFor="comment-content">
          Comment
        </label>
        <textarea
          id="comment-content"
          className={styles.textarea}
          rows={4}
          maxLength={LIMITS.commentContent}
          value={content}
          disabled={disabled || submitting}
          onChange={(e) => setContent(e.target.value)}
        />
        <FieldError message={fieldErrors.content} />
      </div>
      <Button type="submit" disabled={disabled || submitting}>
        Add comment
      </Button>
    </form>
  );
}