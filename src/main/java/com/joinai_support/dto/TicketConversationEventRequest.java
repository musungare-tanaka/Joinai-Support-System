package com.joinai_support.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TicketConversationEventRequest {
    private Long ticketId;
    private String email;
    private String actorRole;
    private String channel;
    private String message;
    private LocalDateTime timestamp;
}
