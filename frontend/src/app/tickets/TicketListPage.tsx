"use client";

import Link from "next/link";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { useCallback, useEffect, useState } from "react";

import { listTickets } from "@/lib/api/tickets";
import { bannerMessage } from "@/lib/api/errors";
import type { TicketSummary } from "@/lib/api/types";
import { DEFAULT_PAGE_SIZE, LIMITS } from "@/lib/constants";
import type { TicketStatus } from "@/lib/constants";
import { Button } from "@/components/ui/Button";
import { ErrorBanner } from "@/components/ui/ErrorBanner";
import { Spinner } from "@/components/ui/Spinner";
import { Pagination } from "@/components/tickets/Pagination";
import { TicketFilters } from "@/components/tickets/TicketFilters";
import { TicketTable } from "@/components/tickets/TicketTable";
import { useDebouncedValue } from "@/hooks/useDebouncedValue";

import styles from "./ticketList.module.css";

export function TicketListPage() {
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();

  const page = Number(searchParams.get("page") ?? "0") || 0;
  const size = Number(searchParams.get("size") ?? String(DEFAULT_PAGE_SIZE)) || DEFAULT_PAGE_SIZE;
  const status = searchParams.get("status") ?? "";
  const qFromUrl = searchParams.get("q") ?? "";

  const [queryInput, setQueryInput] = useState(qFromUrl);
  const debouncedQuery = useDebouncedValue(queryInput, 400);
  const [searchError, setSearchError] = useState("");

  const [items, setItems] = useState<TicketSummary[]>([]);
  const [totalPages, setTotalPages] = useState(0);
  const [totalItems, setTotalItems] = useState(0);
  const [loading, setLoading] = useState(true);
  const [banner, setBanner] = useState("");

  const replaceParams = useCallback(
    (next: Record<string, string | undefined>) => {
      const params = new URLSearchParams(searchParams.toString());
      Object.entries(next).forEach(([key, value]) => {
        if (!value) {
          params.delete(key);
        } else {
          params.set(key, value);
        }
      });
      router.replace(`${pathname}?${params.toString()}`);
    },
    [pathname, router, searchParams],
  );

  useEffect(() => {
    setQueryInput(qFromUrl);
  }, [qFromUrl]);

  useEffect(() => {
    const trimmed = debouncedQuery.trim();
    if (trimmed.length > LIMITS.searchTerm) {
      setSearchError(`Search must be at most ${LIMITS.searchTerm} characters.`);
      return;
    }
    setSearchError("");
    if (trimmed !== qFromUrl) {
      replaceParams({ q: trimmed || undefined, page: "0" });
    }
  }, [debouncedQuery, qFromUrl, replaceParams]);

  useEffect(() => {
    let cancelled = false;
    async function load() {
      setLoading(true);
      setBanner("");
      try {
        const { data } = await listTickets({
          q: qFromUrl || undefined,
          status: status ? (status as TicketStatus) : undefined,
          page,
          size,
        });
        if (cancelled) {
          return;
        }
        setItems(data.items);
        setTotalPages(data.totalPages);
        setTotalItems(data.totalItems);
      } catch (error) {
        if (!cancelled) {
          setItems([]);
          setTotalPages(0);
          setTotalItems(0);
          setBanner(bannerMessage(error));
        }
      } finally {
        if (!cancelled) {
          setLoading(false);
        }
      }
    }
    load();
    return () => {
      cancelled = true;
    };
  }, [qFromUrl, status, page, size]);

  const hasFilters = Boolean(qFromUrl || status);

  return (
    <div>
      <div className={styles.toolbar}>
        <h1 className={styles.heading}>Tickets</h1>
        <Link href="/tickets/new" className={styles.newLink}>
          New ticket
        </Link>
      </div>

      <TicketFilters
        query={queryInput}
        status={status}
        searchError={searchError}
        onQueryChange={setQueryInput}
        onStatusChange={(value) => replaceParams({ status: value || undefined, page: "0" })}
      />

      <ErrorBanner message={banner} />

      {loading ? (
        <Spinner label="Loading tickets…" />
      ) : items.length === 0 ? (
        <div className={styles.empty}>
          <p>{hasFilters ? "No tickets match your search." : "No tickets yet."}</p>
          {hasFilters ? (
            <Button
              variant="secondary"
              onClick={() => {
                setQueryInput("");
                replaceParams({ q: undefined, status: undefined, page: "0" });
              }}
            >
              Clear search and filters
            </Button>
          ) : (
            <Link href="/tickets/new" className={styles.emptyCta}>
              Create your first ticket
            </Link>
          )}
        </div>
      ) : (
        <>
          <TicketTable items={items} />
          <Pagination
            page={page}
            totalPages={totalPages}
            onPageChange={(next) => replaceParams({ page: String(next) })}
          />
          <p className={styles.meta}>{totalItems} ticket(s)</p>
        </>
      )}
    </div>
  );
}