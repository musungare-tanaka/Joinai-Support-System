package com.joinai_support.service.serviceImpl;

import com.joinai_support.domain.Admin;
import com.joinai_support.domain.SupportTicket;
import com.joinai_support.domain.TicketAnalysis;
import com.joinai_support.dto.*;
import com.joinai_support.repository.AdminRepository;
import com.joinai_support.repository.SupportTicketRepository;
import com.joinai_support.service.SupportTicketService;
import com.joinai_support.utils.*;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class SupportTicketServiceImpl implements SupportTicketService {
    private static final Logger logger = LoggerFactory.getLogger(SupportTicketServiceImpl.class);

    private final SupportTicketRepository supportTicketRepository;
    private final AdminServiceImpl adminServiceImpl;
    private final AdminRepository adminRepository;
    private final MailSenderService mailSenderService;
    private final TicketAnalysisServiceImpl ticketAnalysisServiceImpl;


    @Autowired
    public SupportTicketServiceImpl(SupportTicketRepository supportTicketRepository,
                                    AdminServiceImpl adminServiceImpl,
                                    AdminRepository adminRepository,
                                    MailSenderService mailSenderService, TicketAnalysisServiceImpl ticketAnalysisServiceImpl) {
        this.supportTicketRepository = supportTicketRepository;
        this.adminServiceImpl = adminServiceImpl;
        this.adminRepository = adminRepository;
        this.mailSenderService = mailSenderService;
        this.ticketAnalysisServiceImpl = ticketAnalysisServiceImpl;
    }

    @Transactional
    public String launchTicket(SupportTicket supportTicket) {
        supportTicket.setCategory(Category.SUPPORT);
        // Fetch all available admins
        ResponseEntity<List<Admin>> agentsResponse = adminServiceImpl.getAll();
        List<Admin> agentList = agentsResponse.getBody();
        Admin selectedAdmin = null;

        if (agentList == null || agentList.isEmpty()) {
            logger.warn("No admins available to assign ticket. Ticket will be opened as unassigned.");
        } else {
            // Find the admin with the least number of tickets
            selectedAdmin = agentList.stream()
                    .min(Comparator.comparingInt(admin -> admin.getTickets() == null ? 0 : admin.getTickets().size()))
                    .orElse(null);

            if (selectedAdmin == null) {
                logger.warn("Failed to find a suitable admin for ticket assignment. Ticket will be opened as unassigned.");
            }
        }

        // Assign the ticket to the selected admin
        supportTicket.setAssignedTo(selectedAdmin);
        supportTicket.setLaunchTimestamp(LocalDateTime.now());

        Priority[] priorities = Priority.values();

        int randomIndex = new Random().nextInt(priorities.length);
        supportTicket.setPriority(priorities[randomIndex]);
        supportTicket.setStatus(Status.OPEN);

        // Ensure subject is set if not already provided (subject contains issuer info)
        if (supportTicket.getSubject() == null || supportTicket.getSubject().isEmpty()) {
            logger.warn("Ticket created without subject (which contains issuer info). Notifications to issuer will not be possible.");
        }

        try {
            supportTicketRepository.save(supportTicket);

            // Send email notification to the assigned admin
            if (selectedAdmin != null) {
                try {
                    mailSenderService.sendTicketCreationNotification(supportTicket, selectedAdmin);
                    logger.info("Ticket creation notification queued for admin: {}", selectedAdmin.getEmail());
                } catch (Exception e) {
                    // Log the exception but don't fail the ticket creation
                    logger.error("Failed to send ticket creation notification to admin: {}", selectedAdmin.getEmail(), e);
                }
            } else {
                logger.info("Skipping admin ticket creation notification because no admin is currently assigned for ticket {}", supportTicket.getId());
            }

            // Send email notification to the customer who opened the ticket
            try {
                mailSenderService.sendTicketOpenedNotification(supportTicket);

                logger.info("Ticket creation notification queued for customer: {}", supportTicket.getIssuerEmail());
            } catch (Exception e) {
                // Log the exception but don't fail the ticket creation
                logger.error("Failed to send ticket creation notification to customer: {}", supportTicket.getIssuerEmail(), e);
            }

        } catch (Exception e) {
            // Log the exception and return a failure message
            logger.error("Failed to save the support ticket", e);
            return "Failed to save the support ticket. Please try again.";
        }

        // sending the ticket to mongodb for contextual history + analytics
        try {
            ticketAnalysisServiceImpl.createRecord(
                    String.valueOf(supportTicket.getId()),
                    supportTicket.getContent(),
                    supportTicket.getIssuerEmail()
            );
            ticketAnalysisServiceImpl.appendConversationEntry(
                    String.valueOf(supportTicket.getId()),
                    "SYSTEM",
                    "system",
                    selectedAdmin == null
                            ? "Ticket opened and waiting for agent assignment."
                            : "Ticket opened and assigned to " + resolveAssignedAgentName(supportTicket),
                    LocalDateTime.now()
            );
        } catch (Exception e) {
            logger.warn("Failed to initialize MongoDB ticket analysis record for ticket {}", supportTicket.getId(), e);
        }

        return "Ticket successfully opened " ;
    }

    @Transactional
    public ResponseEntity<String> updateTicket(TicketStatusDTO supportTicket) {
        Optional<SupportTicket> supportTicketEntity = supportTicketRepository.findById(supportTicket.getTicketId());
        if (supportTicketEntity.isEmpty()) {
            logger.warn("Ticket not found with ID: {}", supportTicket.getTicketId());
            return ResponseEntity.notFound().build();
        }

        SupportTicket ticket = supportTicketEntity.get();

        // Safely add reply - only if not null or empty
        if (supportTicket.getReply() != null && !supportTicket.getReply().trim().isEmpty()) {
            if (ticket.getReplies() == null) {
                ticket.setReplies(new ArrayList<>());
            }
            ticket.getReplies().add(supportTicket.getReply().trim());
            logger.info("Reply added to ticket ID: {}", supportTicket.getTicketId());
        } else {
            logger.info("No reply provided for ticket ID: {}, updating status only", supportTicket.getTicketId());
        }

        // Update ticket status and timestamps
        ticket.setStatus(supportTicket.getStatus());
        ticket.setTimeLimit(Duration.between(ticket.getLaunchTimestamp(), LocalDateTime.now()));
        ticket.setServedTimestamp(LocalDateTime.now());
        ticket.setUpdatedAt(LocalDateTime.now());

        // Save updated ticket
        supportTicketRepository.save(ticket);

        if (supportTicket.getReply() != null && !supportTicket.getReply().trim().isEmpty()) {
            try {
                ticketAnalysisServiceImpl.appendConversationEntry(
                        String.valueOf(ticket.getId()),
                        "AGENT",
                        "agent-dashboard",
                        supportTicket.getReply().trim(),
                        LocalDateTime.now()
                );
            } catch (Exception e) {
                logger.warn("Failed to append agent reply to MongoDB history for ticket {}", ticket.getId(), e);
            }
        }

        try {
            ticketAnalysisServiceImpl.appendConversationEntry(
                    String.valueOf(ticket.getId()),
                    "SYSTEM",
                    "system",
                    "Ticket status updated to " + ticket.getStatus(),
                    LocalDateTime.now()
            );
        } catch (Exception e) {
            logger.warn("Failed to append status update to MongoDB history for ticket {}", ticket.getId(), e);
        }

        // Send email to assigned admin
        Admin assignedAdmin = ticket.getAssignedTo();
        if (assignedAdmin != null) {
            try {
                mailSenderService.sendTicketUpdateNotification(ticket, assignedAdmin);
                logger.info("Ticket update notification sent to admin: {}", assignedAdmin.getEmail());
            } catch (Exception e) {
                logger.error("Failed to send ticket update notification to admin: {}", assignedAdmin.getEmail(), e);
            }
        } else {
            logger.warn("No admin assigned to ticket ID: {}, cannot send notification", ticket.getId());
        }

        // Send issuer notification if ticket is closed
        if (ticket.getStatus() == Status.CLOSED) {
            try {
                // Only pass reply if it's not null/empty
                String replyForNotification = (supportTicket.getReply() != null && !supportTicket.getReply().trim().isEmpty())
                        ? supportTicket.getReply()
                        : "Ticket closed without additional comments.";

                mailSenderService.sendTicketClosedNotification(ticket, replyForNotification);

                // Only add to analysis if reply exists
                if (supportTicket.getReply() != null && !supportTicket.getReply().trim().isEmpty()) {
                    ticketAnalysisServiceImpl.addReply(String.valueOf(supportTicket.getTicketId()), supportTicket.getReply());
                }

                logger.info("Ticket closed notification sent to issuer: {}", ticket.getSubject());
            } catch (Exception e) {
                logger.error("Failed to send ticket closed notification to issuer: {}", ticket.getSubject(), e);
            }
        }

        return ResponseEntity.ok("Ticket successfully updated.");
    }
    public ResponseEntity<List<SupportTicket>> getMyTickets(Authenticate authenticationResponse) {
      // List<SupportTicket> tickets = userValidator.getUser(authenticationResponse.getToken()).getTickets();
        List<SupportTicket> tickets= adminRepository.findByEmail(authenticationResponse.getEmail()).getTickets();
       return ResponseEntity.ok(tickets);
    }

    public ResponseEntity<StatisticsDTO> getStatistics() {
        double avgTimeLimit = supportTicketRepository.findAll()
                .parallelStream()
                .mapToDouble(supportTicket -> supportTicket.getTimeLimit().toSeconds())
                .average()
                .orElse(0);
        StatisticsDTO statisticsDTO = new StatisticsDTO();
        statisticsDTO.setAvgResolveTime(avgTimeLimit);
        return ResponseEntity.ok(statisticsDTO);
    }

    //method for calculating statics for agents for use by agents
    public ResponseEntity<StatsByAgent> getStatsByAgent(Admin admin) {
        List<SupportTicket> tickets = supportTicketRepository.findAllByAssignedTo(admin);
        StatsByAgent statsByAgent = new StatsByAgent();

        LocalDateTime now = LocalDateTime.now();

        // Daily stats (last 24 hours)
        LocalDateTime dailyStart = now.minusHours(24);
        statsByAgent.setDAILY_TICKETS(tickets.stream()
                .filter(supportTicket ->
                        supportTicket.getLaunchTimestamp().isAfter(dailyStart) ||
                                supportTicket.getLaunchTimestamp().isEqual(dailyStart))
                .count());

        statsByAgent.setSOLVED_DAILY(tickets.stream()
                .filter(supportTicket ->
                        (supportTicket.getLaunchTimestamp().isAfter(dailyStart) ||
                                supportTicket.getLaunchTimestamp().isEqual(dailyStart)) &&
                                (supportTicket.getStatus() == Status.CLOSED))
                .count());

        // Weekly stats (last 7 days / 168 hours)
        LocalDateTime weeklyStart = now.minusHours(168);
        statsByAgent.setWEEKLY_TICKETS(tickets.stream()
                .filter(supportTicket ->
                        supportTicket.getLaunchTimestamp().isAfter(weeklyStart) ||
                                supportTicket.getLaunchTimestamp().isEqual(weeklyStart))
                .count());

        statsByAgent.setSOLVED_WEEKLY(tickets.stream()
                .filter(supportTicket ->
                        (supportTicket.getLaunchTimestamp().isAfter(weeklyStart) ||
                                supportTicket.getLaunchTimestamp().isEqual(weeklyStart)) &&
                                (supportTicket.getStatus() == Status.CLOSED))
                .count());

        // Monthly stats (last 28 days / 672 hours)
        LocalDateTime monthlyStart = now.minusHours(672);
        statsByAgent.setMONTHLY_TICKETS(tickets.stream()
                .filter(supportTicket ->
                        supportTicket.getLaunchTimestamp().isAfter(monthlyStart) ||
                                supportTicket.getLaunchTimestamp().isEqual(monthlyStart))
                .count());

        statsByAgent.setSOLVED_MONTHLY(tickets.stream()
                .filter(supportTicket ->
                        (supportTicket.getLaunchTimestamp().isAfter(monthlyStart) ||
                                supportTicket.getLaunchTimestamp().isEqual(monthlyStart)) &&
                                (supportTicket.getStatus() == Status.CLOSED))
                .count());

        return ResponseEntity.ok(statsByAgent);
    }
    public ResponseEntity<List<TicketDTO>> getNotifications(String email) {
        List<TicketDTO> notifications = new ArrayList<>();
        List<SupportTicket> tickets = adminRepository.findByEmail(email).getTickets();

        tickets.forEach(supportTicket -> {
            TicketDTO ticketDTO = new TicketDTO();
            ticketDTO.setId(supportTicket.getId());
            ticketDTO.setStatus(supportTicket.getStatus());
            ticketDTO.setLaunchTimestamp(supportTicket.getLaunchTimestamp());
            ticketDTO.setCategory(supportTicket.getCategory());
            ticketDTO.setPriority(supportTicket.getPriority());
            ticketDTO.setAttachments(supportTicket.getAttachments());
            ticketDTO.setSubject(supportTicket.getSubject());
            ticketDTO.setIssuerEmail(supportTicket.getIssuerEmail());

            notifications.add(ticketDTO);
        });

        return ResponseEntity.ok(notifications);
    }

    public ResponseEntity<TicketLookupResponse> lookupTicketContext(TicketLookupRequest request) {
        TicketLookupResponse response = new TicketLookupResponse();

        if (request == null || (request.getTicketId() == null
                && (request.getEmail() == null || request.getEmail().isBlank()))) {
            response.setFound(false);
            response.setMessage("Please provide either a ticket number or an email address.");
            return ResponseEntity.ok(response);
        }

        List<SupportTicket> matchedTickets = new ArrayList<>();

        if (request.getTicketId() != null) {
            Optional<SupportTicket> byTicketId = supportTicketRepository.findById(request.getTicketId());
            if (byTicketId.isPresent()) {
                SupportTicket supportTicket = byTicketId.get();
                if (request.getEmail() != null && !request.getEmail().isBlank()) {
                    boolean ownerMatches = supportTicket.getIssuerEmail() != null
                            && supportTicket.getIssuerEmail().equalsIgnoreCase(request.getEmail().trim());
                    if (!ownerMatches) {
                        response.setFound(false);
                        response.setMessage("Ticket #" + request.getTicketId()
                                + " exists, but it was not opened with the provided email address.");
                        return ResponseEntity.ok(response);
                    }
                }
                matchedTickets.add(supportTicket);
            }
        } else if (request.getEmail() != null && !request.getEmail().isBlank()) {
            matchedTickets = supportTicketRepository
                    .findAllByIssuerEmailIgnoreCaseOrderByLaunchTimestampDesc(request.getEmail().trim());
        }

        if (!request.isIncludeClosed()) {
            matchedTickets = matchedTickets.stream()
                    .filter(ticket -> ticket.getStatus() != Status.CLOSED)
                    .toList();
        }

        if (matchedTickets.isEmpty()) {
            response.setFound(false);
            response.setMessage("No open ticket was found for the provided identifier.");
            return ResponseEntity.ok(response);
        }

        List<TicketContextDTO> ticketContexts = matchedTickets.stream()
                .map(this::toTicketContextDTO)
                .toList();

        response.setFound(true);
        response.setTickets(ticketContexts);
        response.setMessage("Loaded " + ticketContexts.size() + " ticket(s) with full context.");
        return ResponseEntity.ok(response);
    }

    @Transactional
    public ResponseEntity<String> appendConversationEvent(TicketConversationEventRequest request) {
        if (request == null || request.getMessage() == null || request.getMessage().isBlank()) {
            return ResponseEntity.badRequest().body("Conversation event message is required.");
        }

        Optional<SupportTicket> ticketOptional = resolveTicketForConversationEvent(request);
        if (ticketOptional.isEmpty()) {
            return ResponseEntity.status(404).body("No matching ticket found for conversation event.");
        }

        SupportTicket ticket = ticketOptional.get();
        ticketAnalysisServiceImpl.appendConversationEntry(
                String.valueOf(ticket.getId()),
                request.getActorRole(),
                request.getChannel(),
                request.getMessage(),
                request.getTimestamp()
        );

        if (ticket.getUpdatedAt() == null || ticket.getUpdatedAt().isBefore(LocalDateTime.now().minusSeconds(1))) {
            ticket.setUpdatedAt(LocalDateTime.now());
            supportTicketRepository.save(ticket);
        }

        return ResponseEntity.ok("Conversation event recorded.");
    }

    private Optional<SupportTicket> resolveTicketForConversationEvent(TicketConversationEventRequest request) {
        if (request.getTicketId() != null) {
            return supportTicketRepository.findById(request.getTicketId());
        }

        if (request.getEmail() == null || request.getEmail().isBlank()) {
            return Optional.empty();
        }

        List<SupportTicket> byEmail = supportTicketRepository
                .findAllByIssuerEmailIgnoreCaseOrderByLaunchTimestampDesc(request.getEmail().trim());

        return byEmail.stream()
                .filter(ticket -> ticket.getStatus() != Status.CLOSED)
                .findFirst()
                .or(() -> byEmail.stream().findFirst());
    }

    private TicketContextDTO toTicketContextDTO(SupportTicket ticket) {
        TicketContextDTO dto = new TicketContextDTO();
        dto.setTicketId(ticket.getId());
        dto.setStatus(ticket.getStatus() == null ? "UNKNOWN" : ticket.getStatus().name());
        dto.setIssueDescription(ticket.getContent());
        dto.setSubject(ticket.getSubject());
        dto.setIssuerEmail(ticket.getIssuerEmail());
        dto.setAssignedAgent(resolveAssignedAgentName(ticket));
        dto.setChannelOfOrigin(resolveChannelOfOrigin(ticket.getId()));
        dto.setLaunchTimestamp(ticket.getLaunchTimestamp());
        dto.setUpdatedAt(ticket.getUpdatedAt());
        dto.setServedTimestamp(ticket.getServedTimestamp());
        dto.setOpenTicket(ticket.getStatus() != Status.CLOSED);
        dto.setMinutesOpen(calculateMinutesOpen(ticket));

        List<TicketAnalysis.TicketConversationEntry> entries =
                ticketAnalysisServiceImpl.getConversationHistory(String.valueOf(ticket.getId()));

        List<TicketConversationMessageDTO> conversation = new ArrayList<>();
        for (TicketAnalysis.TicketConversationEntry entry : entries) {
            TicketConversationMessageDTO conversationMessageDTO = new TicketConversationMessageDTO();
            conversationMessageDTO.setActorRole(entry.getActorRole());
            conversationMessageDTO.setChannel(entry.getChannel());
            conversationMessageDTO.setMessage(entry.getMessage());
            conversationMessageDTO.setTimestamp(entry.getTimestamp());
            conversation.add(conversationMessageDTO);
        }
        dto.setConversationHistory(conversation);

        return dto;
    }

    private String resolveAssignedAgentName(SupportTicket ticket) {
        if (ticket.getAssignedTo() == null) {
            return "Unassigned";
        }
        String firstName = ticket.getAssignedTo().getFirstName();
        return (firstName != null && !firstName.isBlank())
                ? firstName
                : ticket.getAssignedTo().getEmail();
    }

    private String resolveChannelOfOrigin(Long ticketId) {
        Optional<TicketAnalysis> analysis = ticketAnalysisServiceImpl.getTicket(String.valueOf(ticketId));
        if (analysis.isPresent()) {
            List<TicketAnalysis.TicketConversationEntry> history = analysis.get().getConversationHistory();
            if (history != null && !history.isEmpty()) {
                for (TicketAnalysis.TicketConversationEntry entry : history) {
                    if (entry.getChannel() == null || entry.getChannel().isBlank()) {
                        continue;
                    }
                    String channel = entry.getChannel().toLowerCase();
                    if (!"unknown".equals(channel) && !"system".equals(channel)) {
                        return channel;
                    }
                }
            }
            if (analysis.get().getSource() != null) {
                return analysis.get().getSource().name().toLowerCase();
            }
        }
        return "web";
    }

    private long calculateMinutesOpen(SupportTicket ticket) {
        if (ticket.getLaunchTimestamp() == null) {
            return 0;
        }

        LocalDateTime end = ticket.getStatus() == Status.CLOSED && ticket.getServedTimestamp() != null
                ? ticket.getServedTimestamp()
                : LocalDateTime.now();

        Duration duration = Duration.between(ticket.getLaunchTimestamp(), end);
        return Math.max(0, duration.toMinutes());
    }

}
