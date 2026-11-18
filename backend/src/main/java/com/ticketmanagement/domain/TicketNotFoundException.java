package com.ticketmanagement.domain;

public class TicketNotFoundException extends RuntimeException {

    private final String publicId;

    public TicketNotFoundException(String publicId) {
        super("Ticket " + publicId + " was not found.");
        this.publicId = publicId;
    }

    public String getPublicId() {
        return publicId;
    }
}