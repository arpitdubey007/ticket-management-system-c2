"use client";

import {
  createContext,
  useCallback,
  useContext,
  useMemo,
  useState,
  type ReactNode,
} from "react";

interface AssistantDrawerContextValue {
  isOpen: boolean;
  openDrawer: () => void;
  closeDrawer: () => void;
}

const AssistantDrawerContext = createContext<AssistantDrawerContextValue | null>(
  null,
);

export function AssistantDrawerProvider({ children }: { children: ReactNode }) {
  const [isOpen, setIsOpen] = useState(false);

  const openDrawer = useCallback(() => setIsOpen(true), []);
  const closeDrawer = useCallback(() => setIsOpen(false), []);

  const value = useMemo(
    () => ({ isOpen, openDrawer, closeDrawer }),
    [isOpen, openDrawer, closeDrawer],
  );

  return (
    <AssistantDrawerContext.Provider value={value}>
      {children}
    </AssistantDrawerContext.Provider>
  );
}

export function useAssistantDrawer(): AssistantDrawerContextValue {
  const ctx = useContext(AssistantDrawerContext);
  if (!ctx) {
    throw new Error("useAssistantDrawer must be used within AssistantDrawerProvider");
  }
  return ctx;
}