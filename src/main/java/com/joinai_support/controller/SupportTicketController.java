package com.joinai_support.controller;

import com.joinai_support.domain.Admin;
import com.joinai_support.domain.SupportTicket;
import com.joinai_support.dto.*;
import com.joinai_support.repository.AdminRepository;
import com.joinai_support.repository.UserRepository;
import com.joinai_support.service.serviceImpl.SupportTicketServiceImpl;
import com.joinai_support.utils.Authenticate;
import com.joinai_support.utils.TicketDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/ticket")
@CrossOrigin("*")
public class SupportTicketController {

    private final SupportTicketServiceImpl supportTicketServiceImpl;
    private final UserRepository userRepository;
    private final AdminRepository adminRepository;

    @Autowired
    public SupportTicketController(SupportTicketServiceImpl supportTicketServiceImpl, UserRepository userRepository, AdminRepository adminRepository) {
        this.supportTicketServiceImpl = supportTicketServiceImpl;
        this.userRepository = userRepository;
        this.adminRepository = adminRepository;
    }

    @PostMapping("/launchTicket")
    public String launchTicket(@RequestBody SupportTicket supportTicket) {
        return supportTicketServiceImpl.launchTicket(supportTicket);
    }

    //Opening a ticket using Chatbot
    @PostMapping("/openTicket")
    public String openTicket(@RequestBody SupportTicketRequest supportTicket) {
        SupportTicket ticket = new SupportTicket();
        ticket.setIssuerEmail(supportTicket.getEmail());
        ticket.setSubject(supportTicket.getSubject());
        ticket.setContent(supportTicket.getContent());
        return supportTicketServiceImpl.launchTicket(ticket);
    }

    @RequestMapping("/updateTicket")
    public ResponseEntity<String> updateTicket(@RequestBody TicketStatusDTO supportTicket) {
        return supportTicketServiceImpl.updateTicket(supportTicket);
    }

    @PostMapping("/getMyTickets")
    public ResponseEntity<List<SupportTicket>> getMyTickets(@RequestBody Authenticate authenticationResponse) {
        return supportTicketServiceImpl.getMyTickets(authenticationResponse);
    }

    @RequestMapping("/getStats")
    public ResponseEntity<StatisticsDTO> getStats(@RequestBody AuthenticationResponse authenticationResponse) {
            return supportTicketServiceImpl.getStatistics();
    }

    @RequestMapping("/getMyStats")
    public ResponseEntity<StatsByAgent> getMyStats(@RequestBody Authenticate authenticationResponse) {
        Optional<Admin> admin = Optional.ofNullable(adminRepository.findByEmail(authenticationResponse.getToken()));
        if (admin.isPresent()) {
            return supportTicketServiceImpl.getStatsByAgent(admin.get());
        }
        return new ResponseEntity<>(HttpStatus.NOT_FOUND);

    }

    @RequestMapping("/ticketNotifications")
    public ResponseEntity<List<TicketDTO>> getTicketNotifications(@RequestBody EmailRequest emailRequest) {
        return supportTicketServiceImpl.getNotifications(emailRequest.getEmail());
    }


}
