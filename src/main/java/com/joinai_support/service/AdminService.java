package com.joinai_support.service;

import com.joinai_support.domain.SupportTicket;
import com.joinai_support.domain.User;
import com.joinai_support.dto.*;
import com.joinai_support.repository.AdminRepository;
import com.joinai_support.domain.Admin;
import com.joinai_support.repository.SupportTicketRepository;
import com.joinai_support.repository.UserRepository;
import com.joinai_support.utils.*;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;


@Service
public class AdminService {
    private static final Logger logger = LoggerFactory.getLogger(AdminService.class);

    private final AdminRepository adminRepository;
    private final UserRepository userRepository;
    private final SupportTicketRepository supportTicketRepository;
    private final MailSenderService mailSenderService;
    private RandomPasswordGenerator passwordGenerator;


    @Autowired
    public AdminService(AdminRepository adminRepository, 
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

    public ResponseEntity<SystemAnalytics> systemAnalytics() {
        // total  agents
        List<Admin> allAdmins = adminRepository.findAll();
        long agents = allAdmins.stream().filter(admin -> admin.getRole() == Role.AGENT).toList().size();

        //open tickets
        long openTickets=supportTicketRepository.findAll().parallelStream()
                .filter(supportTicket -> supportTicket.getStatus() == Status.OPEN).count();

        //daily tickets  

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime localDateTime =LocalDateTime.now();
        List<SupportTicket> tickets = supportTicketRepository.findAll();

        // Daily stats (last 24 hours)
        LocalDateTime dailyStart = now.minusHours(24);
        long dailytickets = (tickets.stream()
                .filter(supportTicket ->
                        supportTicket.getLaunchTimestamp().isAfter(dailyStart) ||
                                supportTicket.getLaunchTimestamp().isEqual(dailyStart))
                .count());





        List<PerformanceDTO> list = new ArrayList<>();

        adminRepository.findAllByRole(Role.AGENT).forEach(admin -> {
            PerformanceDTO performance = new PerformanceDTO();
            performance.setFrc(34.2);
            performance.setAgentName(admin.getFirstName());
            performance.setOpenTickets(admin.getTickets()
                    .parallelStream().filter(adminTicket -> adminTicket.getStatus() == Status.OPEN).count());
            performance.setOldTickets(admin.getTickets().parallelStream()
                    .filter(adminTicket ->adminTicket.getStatus() ==Status.OPEN && adminTicket.getLaunchTimestamp().isAfter(dailyStart)).count());
            list.add(performance);

        });

        SystemAnalytics systemAnalytics = new SystemAnalytics();
        systemAnalytics.setTotalAgents(agents);
        systemAnalytics.setOpenTickets(openTickets);
        systemAnalytics.setDailyTickets(dailytickets);
        systemAnalytics.setPerformance(list);

        List<Ticket> ticklist = new ArrayList<>();


        allAdmins.parallelStream().filter(admin -> admin.getRole() == Role.AGENT).forEach(admin -> {
            Ticket ticket = new Ticket();
            ticket.setName(admin.getFirstName());
            admin.getTickets().parallelStream().forEach(adminTicket -> {
                if (adminTicket.getPriority() == Priority.HIGH) {
                    ticket.setHigh(ticket.getHigh() + 1);
                } else if (adminTicket.getPriority() == Priority.LOW) {
                    ticket.setLow(ticket.getLow() + 1);
                }else if (adminTicket.getPriority() == Priority.NORMAL) {
                    ticket.setNormal(ticket.getNormal() + 1);
                } else if (adminTicket.getPriority() == Priority.URGENT) {
                    ticket.setUrgent(ticket.getUrgent() + 1);
                }
            });
            ticklist.add(ticket);
        });
        systemAnalytics.setTickets(ticklist);


        return ResponseEntity.ok(systemAnalytics);
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


}
