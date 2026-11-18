package com.ticketmanagement.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ticketmanagement.domain.Comment;
import com.ticketmanagement.service.CommentService;
import com.ticketmanagement.web.dto.CreateCommentRequest;

@RestController
@RequestMapping("/api/tickets/{publicId}/comments")
public class CommentController {

    private final CommentService commentService;
    private final TicketMapper ticketMapper;

    public CommentController(CommentService commentService, TicketMapper ticketMapper) {
        this.commentService = commentService;
        this.ticketMapper = ticketMapper;
    }

    @PostMapping
    public ResponseEntity<com.ticketmanagement.web.dto.CommentResponse> addComment(
            @PathVariable String publicId,
            @RequestBody CreateCommentRequest request) {
        Comment created = commentService.addComment(
                publicId,
                request == null ? null : request.author(),
                request == null ? null : request.content());
        return ResponseEntity.status(201).body(ticketMapper.toComment(created));
    }
}