import type { Comment } from "@/lib/api/types";
import { formatDateTime } from "@/lib/format";

import styles from "./CommentTimeline.module.css";

export function CommentTimeline({ comments }: { comments: Comment[] }) {
  if (comments.length === 0) {
    return <p className={styles.empty}>No comments yet.</p>;
  }

  return (
    <ol className={styles.list}>
      {comments.map((comment, index) => (
        <li key={`${comment.createdAt}-${index}`} className={styles.item}>
          <div className={styles.meta}>
            <span className={styles.author}>{comment.author}</span>
            <span className={styles.time}>{formatDateTime(comment.createdAt)}</span>
          </div>
          <p className={styles.content}>{comment.content}</p>
        </li>
      ))}
    </ol>
  );
}