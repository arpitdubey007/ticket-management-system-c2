import { Suspense } from "react";

import { Spinner } from "@/components/ui/Spinner";
import { TicketListPage } from "@/app/tickets/TicketListPage";

export default function TicketsPage() {
  return (
    <Suspense fallback={<Spinner label="Loading tickets…" />}>
      <TicketListPage />
    </Suspense>
  );
}