import { Button } from "@/components/ui/Button";

import styles from "./Pagination.module.css";

interface PaginationProps {
  page: number;
  totalPages: number;
  onPageChange: (page: number) => void;
}

export function Pagination({ page, totalPages, onPageChange }: PaginationProps) {
  return (
    <div className={styles.row}>
      <Button
        variant="secondary"
        disabled={page <= 0}
        onClick={() => onPageChange(page - 1)}
      >
        Previous
      </Button>
      <span className={styles.status}>
        Page {page + 1}
        {totalPages > 0 ? ` of ${totalPages}` : ""}
      </span>
      <Button
        variant="secondary"
        disabled={totalPages === 0 || page >= totalPages - 1}
        onClick={() => onPageChange(page + 1)}
      >
        Next
      </Button>
    </div>
  );
}