import type { Metadata } from "next";

import { AppChrome } from "@/components/layout/AppChrome";
import { AssistantDrawer } from "@/components/assistant/AssistantDrawer";
import { AssistantDrawerProvider } from "@/components/assistant/AssistantDrawerContext";

import "./globals.css";

export const metadata: Metadata = {
  title: "Support tickets",
  description: "Support ticket management with grounded AI assistant",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="en">
      <body>
        <AssistantDrawerProvider>
          <AppChrome>{children}</AppChrome>
          <AssistantDrawer />
        </AssistantDrawerProvider>
      </body>
    </html>
  );
}