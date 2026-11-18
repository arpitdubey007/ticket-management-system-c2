"use client";

import Link from "next/link";
import type { ReactNode } from "react";

import { Button } from "@/components/ui/Button";
import { useAssistantDrawer } from "@/components/assistant/AssistantDrawerContext";

import styles from "./AppChrome.module.css";

export function AppChrome({ children }: { children: ReactNode }) {
  const { openDrawer } = useAssistantDrawer();

  return (
    <div className={styles.shell}>
      <header className={styles.header}>
        <Link href="/tickets" className={styles.brand}>
          Support tickets
        </Link>
        <nav className={styles.nav}>
          <Link href="/tickets" className={styles.navLink}>
            Tickets
          </Link>
          <Button variant="secondary" onClick={openDrawer}>
            Ask assistant
          </Button>
        </nav>
      </header>
      <main className={styles.main}>{children}</main>
    </div>
  );
}