package com.ticketmanagement.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Validated
@ConfigurationProperties(prefix = "app.tickets")
public class TicketAppProperties {

    @Valid
    @NotNull
    private Limits limits = new Limits();

    @NotEmpty
    private List<String> categories = List.of(
            "PAYMENT", "SHIPPING", "ACCOUNT", "TECHNICAL", "OTHER");

    @Min(1)
    private int commentsMaxPerTicket = 200;

    public Limits getLimits() {
        return limits;
    }

    public void setLimits(Limits limits) {
        this.limits = limits;
    }

    public List<String> getCategories() {
        return categories;
    }

    public void setCategories(List<String> categories) {
        this.categories = categories;
    }

    public int getCommentsMaxPerTicket() {
        return commentsMaxPerTicket;
    }

    public void setCommentsMaxPerTicket(int commentsMaxPerTicket) {
        this.commentsMaxPerTicket = commentsMaxPerTicket;
    }

    @Validated
    public static class Limits {

        @Min(1)
        private int titleMax = 120;

        @Min(1)
        private int descriptionMax = 5000;

        @Min(1)
        private int commentContentMax = 2000;

        @Min(1)
        private int authorMax = 100;

        @Min(1)
        private int assigneeMax = 100;

        @Min(1)
        private int resolutionNotesMax = 2000;

        @Min(1)
        @Max(100)
        private int searchTermMax = 100;

        public int getTitleMax() {
            return titleMax;
        }

        public void setTitleMax(int titleMax) {
            this.titleMax = titleMax;
        }

        public int getDescriptionMax() {
            return descriptionMax;
        }

        public void setDescriptionMax(int descriptionMax) {
            this.descriptionMax = descriptionMax;
        }

        public int getCommentContentMax() {
            return commentContentMax;
        }

        public void setCommentContentMax(int commentContentMax) {
            this.commentContentMax = commentContentMax;
        }

        public int getAuthorMax() {
            return authorMax;
        }

        public void setAuthorMax(int authorMax) {
            this.authorMax = authorMax;
        }

        public int getAssigneeMax() {
            return assigneeMax;
        }

        public void setAssigneeMax(int assigneeMax) {
            this.assigneeMax = assigneeMax;
        }

        public int getResolutionNotesMax() {
            return resolutionNotesMax;
        }

        public void setResolutionNotesMax(int resolutionNotesMax) {
            this.resolutionNotesMax = resolutionNotesMax;
        }

        public int getSearchTermMax() {
            return searchTermMax;
        }

        public void setSearchTermMax(int searchTermMax) {
            this.searchTermMax = searchTermMax;
        }
    }
}