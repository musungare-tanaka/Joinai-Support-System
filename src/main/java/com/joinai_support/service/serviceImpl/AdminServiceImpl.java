package com.joinai_support.service.serviceImpl;

import com.joinai_support.domain.SupportTicket;
import com.joinai_support.domain.User;
import com.joinai_support.dto.*;
import com.joinai_support.repository.AdminRepository;
import com.joinai_support.domain.Admin;
import com.joinai_support.repository.SupportTicketRepository;
import com.joinai_support.repository.UserRepository;
import com.joinai_support.service.AdminService;
import com.joinai_support.utils.*;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;


@Service
public class AdminServiceImpl implements AdminService {
    private static final Logger logger = LoggerFactory.getLogger(AdminServiceImpl.class);

    private final AdminRepository adminRepository;
    private final UserRepository userRepository;
    private final SupportTicketRepository supportTicketRepository;
    private final MailSenderService mailSenderService;
    private RandomPasswordGenerator passwordGenerator;


    @Autowired
    public AdminServiceImpl(AdminRepository adminRepository,
                            UserRepository userRepository,
                            SupportTicketRepository supportTicketRepository,
                            MailSenderService mailSenderService) {
        this.adminRepository = adminRepository;
        this.userRepository = userRepository;
        this.supportTicketRepository = supportTicketRepository;
        this.mailSenderService = mailSenderService;
    }

    @Transactional
    public ResponseEntity<String> createAgent(UserDTO admin) {
        try {
            Admin agent = new Admin();
            agent.setEmail(admin.getEmail());
            agent.setPassword(admin.getPassword());
            agent.setFirstName(admin.getFirstName());
            agent.setRole(Role.AGENT);
            agent.setEnabled(Boolean.TRUE);
            adminRepository.save(agent);

            // Send welcome email to the new agent
            try {
                mailSenderService.sendWelcomeEmail(agent.getEmail(), agent.getFirstName(), admin.getPassword());
                logger.info("Welcome email sent to new agent: {}", agent.getEmail());
            } catch (Exception e) {
                // Log the exception but don't fail the agent creation
                logger.error("Failed to send welcome email to new agent: {}", agent.getEmail(), e);
            }

            return ResponseEntity.ok("Agent created successfully");
        } catch (Exception e) {
            logger.error("Failed to create agent", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to create agent: " + e.getMessage());
        }
    }


    @Transactional
    public ResponseEntity<String> createAdmin(UserDTO admin) {

        Optional<Admin> optionalAdmin = Optional.ofNullable(adminRepository.findByEmail(admin.getEmail()));
        if (optionalAdmin.isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("Admin already exists");
        }

        try {
            Admin admin1 = new Admin();
            admin1.setEmail(admin.getEmail());
            admin1.setPassword(admin.getPassword());
            admin1.setFirstName(admin.getFirstName());
            admin1.setRole(Role.ADMIN);
            admin1.setEnabled(Boolean.TRUE);
            adminRepository.save(admin1);

            // Send welcome email to the new admin
            try {
                mailSenderService.sendWelcomeEmail(admin1.getEmail(), admin1.getFirstName(), admin.getPassword());
                logger.info("Welcome email sent to new admin: {}", admin1.getEmail());
            } catch (Exception e) {
                // Log the exception but don't fail the admin creation
                logger.error("Failed to send welcome email to new admin: {}", admin1.getEmail(), e);
            }

            return ResponseEntity.ok("Admin created successfully");
        } catch (Exception e) {
            logger.error("Failed to create admin", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to create admin: " + e.getMessage());
        }
    }

    public ResponseEntity<List<Admin>> getAll() {
        List<Admin> adminList = adminRepository.findAllByRole(Role.AGENT);
        return ResponseEntity.ok(adminList);
    }

    public Admin getAdmin(String email) {
        return adminRepository.findByEmail(email);
    }

    public ResponseEntity<List<Admin>> getAllAgents(GetResponse request) {
        Optional<User> user = userRepository.findByEmail(request.getToken());

        if (user.isPresent()) {
            List<Admin> allAdmins = adminRepository.findAll();
            return ResponseEntity.ok(allAdmins.stream().filter(admin -> admin.getRole() == Role.AGENT).toList());
        } else {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }

    @Transactional
    public ResponseEntity<Admin> updateAgentStatus(AgentStatusUpdateRequest request) {
        if (request.getToken() == null || request.getToken().isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        if (request.getAgentEmail() == null || request.getAgentEmail().isBlank() || request.getEnabled() == null) {
            return ResponseEntity.badRequest().build();
        }

        Optional<User> actor = userRepository.findByEmail(request.getToken());
        if (actor.isEmpty() || actor.get().getRole() != Role.ADMIN) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        Admin target = adminRepository.findByEmail(request.getAgentEmail().trim());
        if (target == null) {
            return ResponseEntity.notFound().build();
        }

        if (target.getRole() != Role.AGENT && target.getRole() != Role.ADMIN) {
            return ResponseEntity.badRequest().build();
        }

        target.setEnabled(request.getEnabled());
        Admin updated = adminRepository.save(target);
        return ResponseEntity.ok(updated);
    }

    @Transactional
    public ResponseEntity<Admin> editProfile(AdminDTO request) {
        Optional<User> user = userRepository.findByEmail(request.getEmail());

        if (user.isPresent()) {
            Admin admin = adminRepository.findByEmail(request.getEmail());

            // Check if each field is not empty before setting
            if (request.getName() != null && !request.getName().isEmpty()) {
                admin.setFirstName(request.getName());
            }

            if (request.getPassword() != null && !request.getPassword().isEmpty()) {
                admin.setPassword(request.getPassword());
            }

            if (request.getCity() != null && !request.getCity().isEmpty()) {
                admin.setCity(request.getCity());
            }

            if (request.getCountry() != null && !request.getCountry().isEmpty()) {
                admin.setCountry(request.getCountry());
            }

            if (request.getPhone() != null && !request.getPhone().isEmpty()) {
                admin.setPhone(request.getPhone());
            }

            if (request.getAddress() != null && !request.getAddress().isEmpty()) {
                admin.setAddress(request.getAddress());
            }

            if (request.getZip() != null && !request.getZip().isEmpty()) {
                admin.setZip(request.getZip());
            }

            // Save the updated admin profile
            adminRepository.save(admin);

            return ResponseEntity.ok(admin);
        } else {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }


    @Transactional
    public ResponseEntity<Admin> deleteProfile(GetResponse request) {

        Optional<User> user = userRepository.findByEmail(request.getToken());

        if (user.isPresent()) {
            Admin admin = adminRepository.findByEmail(request.getAdmin().getEmail());
            adminRepository.delete(admin);
        return ResponseEntity.ok(admin);
        }
         return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }

    @Transactional
    public void TrackActivity(Admin agent) {
       Admin user = adminRepository.findByEmail(agent.getEmail());
       if (user != null) {
           user.setLastLogin(LocalDateTime.now());
       }

    }

    public ResponseEntity<List<SupportTicket>> getAllTickets() {
        return ResponseEntity.ok(supportTicketRepository.findAll());
    }

    @Transactional
    public ResponseEntity<SystemAnalytics> systemAnalytics() {
        List<SupportTicket> allTickets = supportTicketRepository.findAll();
        List<Admin> agents = adminRepository.findAllByRole(Role.AGENT);

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime dailyStart = now.minusHours(24);
        LocalDateTime weeklyStart = now.minusDays(7);
        LocalDateTime monthlyStart = now.minusDays(30);

        long totalTickets = allTickets.size();
        long openTickets = allTickets.stream().filter(ticket -> ticket.getStatus() == Status.OPEN).count();
        long closedTickets = allTickets.stream().filter(ticket -> ticket.getStatus() == Status.CLOSED).count();
        long newTickets = allTickets.stream().filter(ticket -> ticket.getStatus() == Status.NEW).count();

        long dailyTickets = allTickets.stream()
                .filter(ticket -> isWithinWindow(ticket.getLaunchTimestamp(), dailyStart))
                .count();
        long weeklyTickets = allTickets.stream()
                .filter(ticket -> isWithinWindow(ticket.getLaunchTimestamp(), weeklyStart))
                .count();
        long monthlyTickets = allTickets.stream()
                .filter(ticket -> isWithinWindow(ticket.getLaunchTimestamp(), monthlyStart))
                .count();

        long resolvedToday = allTickets.stream()
                .filter(ticket -> isWithinWindow(resolveClosedTimestamp(ticket), dailyStart))
                .count();
        long resolvedThisWeek = allTickets.stream()
                .filter(ticket -> isWithinWindow(resolveClosedTimestamp(ticket), weeklyStart))
                .count();
        long resolvedThisMonth = allTickets.stream()
                .filter(ticket -> isWithinWindow(resolveClosedTimestamp(ticket), monthlyStart))
                .count();

        List<PerformanceDTO> performanceByAgent = new ArrayList<>();
        List<Ticket> ticketDistribution = new ArrayList<>();

        for (Admin admin : agents) {
            List<SupportTicket> agentTickets = admin.getTickets() == null ? new ArrayList<>() : admin.getTickets();

            long agentTotal = agentTickets.size();
            long agentOpen = agentTickets.stream().filter(ticket -> ticket.getStatus() == Status.OPEN).count();
            long agentClosed = agentTickets.stream().filter(ticket -> ticket.getStatus() == Status.CLOSED).count();
            long agentNew = agentTickets.stream().filter(ticket -> ticket.getStatus() == Status.NEW).count();
            long oldOpenTickets = agentTickets.stream()
                    .filter(ticket -> ticket.getStatus() == Status.OPEN)
                    .filter(ticket -> ticket.getLaunchTimestamp() != null && !ticket.getLaunchTimestamp().isAfter(dailyStart))
                    .count();
            long agentHighPriority = agentTickets.stream()
                    .filter(ticket -> ticket.getPriority() == Priority.HIGH
                            || ticket.getPriority() == Priority.URGENT
                            || ticket.getPriority() == Priority.CRITICAL)
                    .count();
            long agentUrgent = agentTickets.stream()
                    .filter(ticket -> ticket.getPriority() == Priority.URGENT || ticket.getPriority() == Priority.CRITICAL)
                    .count();
            long repliesCount = agentTickets.stream()
                    .mapToLong(ticket -> ticket.getReplies() == null ? 0 : ticket.getReplies().size())
                    .sum();

            long solvedPast24Hours = agentTickets.stream()
                    .filter(ticket -> isWithinWindow(resolveClosedTimestamp(ticket), dailyStart))
                    .count();
            long solvedPastWeek = agentTickets.stream()
                    .filter(ticket -> isWithinWindow(resolveClosedTimestamp(ticket), weeklyStart))
                    .count();
            long solvedPastMonth = agentTickets.stream()
                    .filter(ticket -> isWithinWindow(resolveClosedTimestamp(ticket), monthlyStart))
                    .count();

            PerformanceDTO performanceDTO = new PerformanceDTO();
            performanceDTO.setAgentName(resolveAgentName(admin));
            performanceDTO.setAgentEmail(admin.getEmail());
            performanceDTO.setTotalTickets(agentTotal);
            performanceDTO.setOpenTickets(agentOpen);
            performanceDTO.setClosedTickets(agentClosed);
            performanceDTO.setNewTickets(agentNew);
            performanceDTO.setOldTickets(oldOpenTickets);
            performanceDTO.setHighPriorityTickets(agentHighPriority);
            performanceDTO.setUrgentTickets(agentUrgent);
            performanceDTO.setRepliesCount(repliesCount);
            performanceDTO.setSolvedPast24Hours(solvedPast24Hours);
            performanceDTO.setSolvedPastWeek(solvedPastWeek);
            performanceDTO.setSolvedPastMonth(solvedPastMonth);
            performanceDTO.setFrc(calculateFcrRate(agentTickets));
            performanceDTO.setAvgResponseTimeMinutes(calculateAverageResponseTimeMinutes(agentTickets));
            performanceDTO.setAvgResolutionTimeMinutes(calculateAverageResolutionTimeMinutes(agentTickets));
            performanceDTO.setResolutionRate(percentage(agentClosed, agentTotal));
            performanceDTO.setSlaBreachRate(calculateSlaBreachRate(agentTickets));
            performanceByAgent.add(performanceDTO);

            Ticket agentPriority = new Ticket();
            agentPriority.setName(resolveAgentName(admin));
            for (SupportTicket ticket : agentTickets) {
                if (ticket.getPriority() == Priority.HIGH) {
                    agentPriority.setHigh(agentPriority.getHigh() + 1);
                } else if (ticket.getPriority() == Priority.LOW) {
                    agentPriority.setLow(agentPriority.getLow() + 1);
                } else if (ticket.getPriority() == Priority.NORMAL || ticket.getPriority() == Priority.MEDIUM) {
                    agentPriority.setNormal(agentPriority.getNormal() + 1);
                } else if (ticket.getPriority() == Priority.URGENT || ticket.getPriority() == Priority.CRITICAL) {
                    agentPriority.setUrgent(agentPriority.getUrgent() + 1);
                }
            }
            ticketDistribution.add(agentPriority);
        }

        SystemAnalytics systemAnalytics = new SystemAnalytics();
        systemAnalytics.setTotalTickets(totalTickets);
        systemAnalytics.setOpenTickets(openTickets);
        systemAnalytics.setClosedTickets(closedTickets);
        systemAnalytics.setNewTickets(newTickets);
        systemAnalytics.setTotalAgents(agents.size());
        systemAnalytics.setDailyTickets(dailyTickets);
        systemAnalytics.setWeeklyTickets(weeklyTickets);
        systemAnalytics.setMonthlyTickets(monthlyTickets);
        systemAnalytics.setResolvedToday(resolvedToday);
        systemAnalytics.setResolvedThisWeek(resolvedThisWeek);
        systemAnalytics.setResolvedThisMonth(resolvedThisMonth);
        systemAnalytics.setAvgResponseTimeMinutes(calculateAverageResponseTimeMinutes(allTickets));
        systemAnalytics.setAvgResolutionTimeMinutes(calculateAverageResolutionTimeMinutes(allTickets));
        systemAnalytics.setClosureRate(percentage(closedTickets, totalTickets));
        systemAnalytics.setFrcRate(calculateFcrRate(allTickets));
        systemAnalytics.setSlaBreachRate(calculateSlaBreachRate(allTickets));
        systemAnalytics.setPerformance(performanceByAgent);
        systemAnalytics.setTickets(ticketDistribution);

        return ResponseEntity.ok(systemAnalytics);
    }

    private String resolveAgentName(Admin admin) {
        if (admin.getFirstName() != null && !admin.getFirstName().isBlank()) {
            return admin.getFirstName();
        }
        return admin.getEmail();
    }

    private LocalDateTime resolveClosedTimestamp(SupportTicket ticket) {
        if (ticket == null || ticket.getStatus() != Status.CLOSED) {
            return null;
        }
        if (ticket.getServedTimestamp() != null) {
            return ticket.getServedTimestamp();
        }
        return ticket.getUpdatedAt();
    }

    private boolean isWithinWindow(LocalDateTime value, LocalDateTime startInclusive) {
        if (value == null || startInclusive == null) {
            return false;
        }
        return value.isAfter(startInclusive) || value.isEqual(startInclusive);
    }

    private double calculateAverageResponseTimeMinutes(List<SupportTicket> tickets) {
        double averageMinutes = tickets.stream()
                .map(this::resolveResponseDuration)
                .flatMap(Optional::stream)
                .mapToLong(Duration::toMinutes)
                .average()
                .orElse(0.0);
        return roundToTwoDecimals(averageMinutes);
    }

    private double calculateAverageResolutionTimeMinutes(List<SupportTicket> tickets) {
        double averageMinutes = tickets.stream()
                .map(this::resolveResolutionDuration)
                .flatMap(Optional::stream)
                .mapToLong(Duration::toMinutes)
                .average()
                .orElse(0.0);
        return roundToTwoDecimals(averageMinutes);
    }

    private double calculateFcrRate(List<SupportTicket> tickets) {
        long closedTickets = tickets.stream().filter(ticket -> ticket.getStatus() == Status.CLOSED).count();
        long firstContactResolved = tickets.stream()
                .filter(ticket -> ticket.getStatus() == Status.CLOSED)
                .filter(ticket -> ticket.getReplies() == null || ticket.getReplies().size() <= 1)
                .count();
        return percentage(firstContactResolved, closedTickets);
    }

    private double calculateSlaBreachRate(List<SupportTicket> tickets) {
        List<Duration> responseDurations = tickets.stream()
                .map(this::resolveResponseDuration)
                .flatMap(Optional::stream)
                .toList();

        if (responseDurations.isEmpty()) {
            return 0.0;
        }

        long breached = responseDurations.stream()
                .filter(duration -> duration.toHours() > 24)
                .count();
        return percentage(breached, responseDurations.size());
    }

    private Optional<Duration> resolveResponseDuration(SupportTicket ticket) {
        if (ticket == null || ticket.getLaunchTimestamp() == null || ticket.getServedTimestamp() == null) {
            return Optional.empty();
        }

        Duration duration = Duration.between(ticket.getLaunchTimestamp(), ticket.getServedTimestamp());
        if (duration.isNegative()) {
            return Optional.empty();
        }
        return Optional.of(duration);
    }

    private Optional<Duration> resolveResolutionDuration(SupportTicket ticket) {
        if (ticket == null || ticket.getStatus() != Status.CLOSED || ticket.getLaunchTimestamp() == null) {
            return Optional.empty();
        }

        if (ticket.getTimeLimit() != null && !ticket.getTimeLimit().isNegative()) {
            return Optional.of(ticket.getTimeLimit());
        }

        LocalDateTime resolvedAt = ticket.getServedTimestamp() != null
                ? ticket.getServedTimestamp()
                : ticket.getUpdatedAt();

        if (resolvedAt == null) {
            return Optional.empty();
        }

        Duration duration = Duration.between(ticket.getLaunchTimestamp(), resolvedAt);
        if (duration.isNegative()) {
            return Optional.empty();
        }
        return Optional.of(duration);
    }

    private double percentage(long value, long total) {
        if (total <= 0) {
            return 0.0;
        }
        return roundToTwoDecimals((value * 100.0) / total);
    }

    private double roundToTwoDecimals(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    public ResponseEntity<AdminDTO> getProfileData(EmailRequest profileRequest) {
        AdminDTO adminDTO = new AdminDTO();

        Admin optionalAdmin = adminRepository.findByEmail(profileRequest.getEmail());
        if (optionalAdmin != null) {
            adminDTO.setName(optionalAdmin.getFirstName());
            adminDTO.setEmail(optionalAdmin.getEmail());
            adminDTO.setPassword(optionalAdmin.getPassword());
            adminDTO.setAddress(optionalAdmin.getAddress());
            adminDTO.setCountry(optionalAdmin.getCountry());
            adminDTO.setCity(optionalAdmin.getCity());
            adminDTO.setState(optionalAdmin.getState());
            adminDTO.setPhone(optionalAdmin.getPhone());
            adminDTO.setZip(optionalAdmin.getZip());
        }

        return ResponseEntity.ok(adminDTO);
    }

    @Override
    public ResponseEntity<AgentSettingsDTO> getAgentSettings(EmailRequest request) {
        if (request == null || request.getEmail() == null || request.getEmail().isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        String email = request.getEmail().trim();
        Optional<User> user = userRepository.findByEmail(email);
        if (user.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Admin admin = adminRepository.findByEmail(email);
        if (admin == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        boolean needsDefaults = admin.getEmailNotifications() == null
                || admin.getInAppNotifications() == null
                || admin.getTicketEscalationAlerts() == null
                || admin.getLanguage() == null
                || admin.getDefaultTicketView() == null
                || admin.getSignature() == null
                || admin.getRefreshIntervalSeconds() == null
                || admin.getCompactTicketCards() == null;

        if (needsDefaults) {
            admin.ensureSettingsDefaults();
            admin = adminRepository.save(admin);
        }

        return ResponseEntity.ok(mapSettings(admin));
    }

    @Override
    @Transactional
    public ResponseEntity<AgentSettingsDTO> updateAgentSettings(AgentSettingsDTO request) {
        if (request == null || request.getEmail() == null || request.getEmail().isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        String email = request.getEmail().trim();
        Optional<User> user = userRepository.findByEmail(email);
        if (user.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Admin admin = adminRepository.findByEmail(email);
        if (admin == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        if (request.getEmailNotifications() != null) {
            admin.setEmailNotifications(request.getEmailNotifications());
        }
        if (request.getInAppNotifications() != null) {
            admin.setInAppNotifications(request.getInAppNotifications());
        }
        if (request.getTicketEscalationAlerts() != null) {
            admin.setTicketEscalationAlerts(request.getTicketEscalationAlerts());
        }
        if (request.getCompactTicketCards() != null) {
            admin.setCompactTicketCards(request.getCompactTicketCards());
        }

        String normalizedLanguage = normalizeLanguage(request.getLanguage());
        if (normalizedLanguage != null) {
            admin.setLanguage(normalizedLanguage);
        }

        String normalizedView = normalizeTicketView(request.getDefaultTicketView());
        if (normalizedView != null) {
            admin.setDefaultTicketView(normalizedView);
        }

        Integer normalizedRefresh = normalizeRefreshInterval(request.getRefreshIntervalSeconds());
        if (normalizedRefresh != null) {
            admin.setRefreshIntervalSeconds(normalizedRefresh);
        }

        if (request.getSignature() != null) {
            String normalizedSignature = request.getSignature().trim();
            if (normalizedSignature.length() > 500) {
                normalizedSignature = normalizedSignature.substring(0, 500);
            }
            admin.setSignature(normalizedSignature);
        }

        admin.ensureSettingsDefaults();
        Admin saved = adminRepository.save(admin);
        return ResponseEntity.ok(mapSettings(saved));
    }

    @Transactional
    public ResponseEntity<String> forgetPassword(EmailRequest request) {
        //checking to see if the user exists in the user tables before sending a email

        Optional<Admin> userExists = Optional.ofNullable(adminRepository.findByEmail(request.getEmail()));
        if (userExists.isPresent()){
            String newPassword = RandomPasswordGenerator.generatePassword(6);

            /// sending the password to the recipient
            mailSenderService.sendPasswordResetEmail(newPassword, request.getEmail());
            userExists.get().setPassword(newPassword);
            adminRepository.save(userExists.get());
            return ResponseEntity.ok("Success");

        }

        return ResponseEntity.notFound().build();



    }

    private AgentSettingsDTO mapSettings(Admin admin) {
        AgentSettingsDTO dto = new AgentSettingsDTO();
        dto.setEmail(admin.getEmail());
        dto.setEmailNotifications(admin.getEmailNotifications() != null ? admin.getEmailNotifications() : Boolean.TRUE);
        dto.setInAppNotifications(admin.getInAppNotifications() != null ? admin.getInAppNotifications() : Boolean.TRUE);
        dto.setTicketEscalationAlerts(admin.getTicketEscalationAlerts() != null ? admin.getTicketEscalationAlerts() : Boolean.TRUE);
        dto.setLanguage(normalizeLanguage(admin.getLanguage()) != null ? normalizeLanguage(admin.getLanguage()) : "English");
        dto.setDefaultTicketView(normalizeTicketView(admin.getDefaultTicketView()) != null ? normalizeTicketView(admin.getDefaultTicketView()) : "list");
        dto.setSignature(admin.getSignature() != null ? admin.getSignature() : "");
        dto.setRefreshIntervalSeconds(normalizeRefreshInterval(admin.getRefreshIntervalSeconds()) != null ? normalizeRefreshInterval(admin.getRefreshIntervalSeconds()) : 60);
        dto.setCompactTicketCards(admin.getCompactTicketCards() != null ? admin.getCompactTicketCards() : Boolean.FALSE);
        return dto;
    }

    private String normalizeLanguage(String language) {
        if (language == null || language.isBlank()) {
            return null;
        }
        String value = language.trim();
        List<String> allowed = List.of("English", "Spanish", "French", "German");
        return allowed.contains(value) ? value : "English";
    }

    private String normalizeTicketView(String view) {
        if (view == null || view.isBlank()) {
            return null;
        }
        return "kanban".equalsIgnoreCase(view.trim()) ? "kanban" : "list";
    }

    private Integer normalizeRefreshInterval(Integer seconds) {
        if (seconds == null) {
            return null;
        }
        return Math.max(15, Math.min(300, seconds));
    }


}
