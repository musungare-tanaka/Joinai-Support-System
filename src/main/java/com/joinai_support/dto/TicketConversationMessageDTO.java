package com.joinai_support.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TicketConversationMessageDTO {
    private String actorRole;
    private String channel;
    private String message;
    private LocalDateTime timestamp;
}
