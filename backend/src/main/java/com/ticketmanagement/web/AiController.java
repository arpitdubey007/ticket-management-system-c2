package com.ticketmanagement.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ticketmanagement.rag.AskResult;
import com.ticketmanagement.rag.AskService;
import com.ticketmanagement.web.dto.AskRequest;
import com.ticketmanagement.web.dto.AskResponse;

@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final AskService askService;

    public AiController(AskService askService) {
        this.askService = askService;
    }

    @PostMapping("/ask")
    public ResponseEntity<AskResponse> ask(@RequestBody AskRequest request) {
        AskResult result = askService.ask(request == null ? null : request.question());
        return ResponseEntity.ok(new AskResponse(result.answer(), result.grounded(), result.citations()));
    }
}