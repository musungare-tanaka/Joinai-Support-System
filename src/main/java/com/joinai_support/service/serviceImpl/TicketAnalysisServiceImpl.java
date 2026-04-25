package com.joinai_support.service.serviceImpl;


import com.joinai_support.domain.TicketAnalysis;
import com.joinai_support.repository.TicketAnalysisRepository;
import com.joinai_support.service.TicketAnalysisService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Service
public class TicketAnalysisServiceImpl implements TicketAnalysisService {

    private final TicketAnalysisRepository repository;

    public TicketAnalysisServiceImpl(TicketAnalysisRepository repository) {
        this.repository = repository;
    }

    // Create a new ticket
    public TicketAnalysis createRecord(String ticketId, String question, String issuerEmail) {
        TicketAnalysis ticket = new TicketAnalysis(ticketId, question,issuerEmail);
        return repository.save(ticket);
    }

    @Transactional
    public TicketAnalysis addReply(String ticketId, String reply) {
        Optional<TicketAnalysis> ticketOpt = repository.findById(ticketId);
        if (ticketOpt.isPresent()) {
            TicketAnalysis ticket = ticketOpt.get();
            ticket.addReply(reply);
            return repository.save(ticket);
        } else {
            throw new RuntimeException("Ticket with ID " + ticketId + " not found");
        }
    }

    // Fetch a ticket
    public Optional<TicketAnalysis> getTicket(String ticketId) {
        return repository.findById(ticketId);
    }

    //method to find replies associated with a ticket
    public List<String>  associatedReplies(String ticketId){TicketAnalysis ticket = repository.findByTicketId(ticketId);
        return  ticket != null ? ticket.getReplies() : Collections.emptyList();




    }

    @Transactional
    public TicketAnalysis appendConversationEntry(
            String ticketId,
            String actorRole,
            String channel,
            String message,
            LocalDateTime timestamp
    ) {
        Optional<TicketAnalysis> ticketOpt = repository.findById(ticketId);
        if (ticketOpt.isEmpty()) {
            throw new RuntimeException("Ticket with ID " + ticketId + " not found");
        }

        TicketAnalysis ticket = ticketOpt.get();
        ticket.addConversationEntry(actorRole, channel, message, timestamp);
        return repository.save(ticket);
    }

    public List<TicketAnalysis.TicketConversationEntry> getConversationHistory(String ticketId) {
        Optional<TicketAnalysis> ticketOpt = repository.findById(ticketId);
        if (ticketOpt.isEmpty()) {
            return Collections.emptyList();
        }

        List<TicketAnalysis.TicketConversationEntry> conversationHistory = ticketOpt.get().getConversationHistory();
        if (conversationHistory == null || conversationHistory.isEmpty()) {
            return Collections.emptyList();
        }

        List<TicketAnalysis.TicketConversationEntry> sorted = new ArrayList<>(conversationHistory);
        sorted.sort(Comparator.comparing(TicketAnalysis.TicketConversationEntry::getTimestamp));
        return sorted;
    }
}
