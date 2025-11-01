package com.joinai_support.service;

import com.joinai_support.domain.Admin;
import com.joinai_support.domain.SupportTicket;
import com.joinai_support.dto.*;
import com.joinai_support.repository.AdminRepository;
import com.joinai_support.repository.SupportTicketRepository;
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
public class SupportTicketService {
    private static final Logger logger = LoggerFactory.getLogger(SupportTicketService.class);

    private final SupportTicketRepository supportTicketRepository;
    private final AdminService adminService;
    private final AdminRepository adminRepository;
    private final MailSenderService mailSenderService;
    private final TicketAnalysisService ticketAnalysisService;


    @Autowired
    public SupportTicketService(SupportTicketRepository supportTicketRepository,
                                AdminService adminService,
                                AdminRepository adminRepository,
                                MailSenderService mailSenderService, TicketAnalysisService ticketAnalysisService) {
        this.supportTicketRepository = supportTicketRepository;
        this.adminService = adminService;
        this.adminRepository = adminRepository;
        this.mailSenderService = mailSenderService;
        this.ticketAnalysisService = ticketAnalysisService;
    }

    @Transactional
    public String launchTicket(SupportTicket supportTicket) {
        supportTicket.setCategory(Category.SUPPORT);
        // Fetch all available admins
        ResponseEntity<List<Admin>> agentsResponse = adminService.getAll();
        List<Admin> agentList = agentsResponse.getBody();

        if (agentList == null || agentList.isEmpty()) {
            logger.warn("No admins available to assign the ticket");
            return "No admins available to assign the ticket.";
        }

        // Find the admin with the least number of tickets
        Admin selectedAdmin = agentList.stream()
                .filter(admin -> admin.getTickets() != null) // Ensure tickets are not null
                .min(Comparator.comparingInt(admin -> admin.getTickets().size()))
                .orElse(null);

        if (selectedAdmin == null) {
            logger.warn("Failed to find a suitable admin for ticket assignment");
            return "Failed to find a suitable admin for ticket assignment.";
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
            try {
                mailSenderService.sendTicketCreationNotification(supportTicket, selectedAdmin);
                logger.info("Ticket creation notification sent to admin: {}", selectedAdmin.getEmail());
            } catch (Exception e) {
                // Log the exception but don't fail the ticket creation
                logger.error("Failed to send ticket creation notification to admin: {}", selectedAdmin.getEmail(), e);
            }

            // Send email notification to the customer who opened the ticket
            try {
                mailSenderService.sendTicketOpenedNotification(supportTicket);

                logger.info("Ticket creation notification sent to customer: {}", supportTicket.getSubject());
            } catch (Exception e) {
                // Log the exception but don't fail the ticket creation
                logger.error("Failed to send ticket creation notification to customer: {}", supportTicket.getSubject(), e);
            }

        } catch (Exception e) {
            // Log the exception and return a failure message
            logger.error("Failed to save the support ticket", e);
            return "Failed to save the support ticket. Please try again.";
        }

        //sending the ticket to mongodb for future analysis
        ticketAnalysisService.createRecord(String.valueOf(supportTicket.getId()), supportTicket.getContent(), supportTicket.getIssuerEmail());

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
                    ticketAnalysisService.addReply(String.valueOf(supportTicket.getTicketId()), supportTicket.getReply());
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

}
