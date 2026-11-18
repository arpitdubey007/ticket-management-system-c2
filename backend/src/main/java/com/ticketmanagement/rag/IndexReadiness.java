package com.ticketmanagement.rag;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

@Component
public class IndexReadiness {

    private final Set<String> pendingTicketIds = ConcurrentHashMap.newKeySet();

    public void resetPending(Set<String> ticketPublicIds) {
        pendingTicketIds.clear();
        pendingTicketIds.addAll(ticketPublicIds);
    }

    public void markIndexed(String publicId) {
        pendingTicketIds.remove(publicId);
    }

    public void markFailed(String publicId) {
        pendingTicketIds.remove(publicId);
    }

    public boolean isReady() {
        return pendingTicketIds.isEmpty();
    }

    public int pendingCount() {
        return pendingTicketIds.size();
    }
}