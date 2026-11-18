export function formatDateTime(iso: string): string {
  try {
    return new Intl.DateTimeFormat(undefined, {
      dateStyle: "medium",
      timeStyle: "short",
    }).format(new Date(iso));
  } catch {
    return iso;
  }
}

export function displayAssignee(assignee: string | null | undefined): string {
  return assignee?.trim() ? assignee : "Unassigned";
}