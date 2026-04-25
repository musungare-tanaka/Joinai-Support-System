package com.joinai_support.service;

import com.joinai_support.domain.TicketAnalysis;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface TicketAnalysisService {

    TicketAnalysis createRecord(String ticketId, String question, String issuerEmail);

    TicketAnalysis addReply(String ticketId, String reply);

    Optional<TicketAnalysis> getTicket(String ticketId);

    List<String> associatedReplies(String ticketId);

    TicketAnalysis appendConversationEntry(
            String ticketId,
            String actorRole,
            String channel,
            String message,
            LocalDateTime timestamp
    );

    List<TicketAnalysis.TicketConversationEntry> getConversationHistory(String ticketId);
}
