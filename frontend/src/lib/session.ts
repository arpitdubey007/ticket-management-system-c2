const AUTHOR_KEY = "ticket-app-last-author";

export function getLastAuthor(): string {
  if (typeof window === "undefined") {
    return "";
  }
  return sessionStorage.getItem(AUTHOR_KEY) ?? "";
}

export function setLastAuthor(author: string): void {
  if (typeof window === "undefined") {
    return;
  }
  sessionStorage.setItem(AUTHOR_KEY, author);
}