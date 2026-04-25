package com.joinai_support.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class TicketContextDTO {
    private Long ticketId;
    private String status;
    private String issueDescription;
    private String subject;
    private String issuerEmail;
    private String assignedAgent;
    private String channelOfOrigin;
    private LocalDateTime launchTimestamp;
    private LocalDateTime updatedAt;
    private LocalDateTime servedTimestamp;
    private boolean openTicket;
    private long minutesOpen;
    private List<TicketConversationMessageDTO> conversationHistory = new ArrayList<>();
}
