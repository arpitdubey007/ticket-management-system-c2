package com.ticketmanagement.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AskRequest(@JsonProperty("question") String question) {
}