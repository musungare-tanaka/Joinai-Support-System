package com.joinai_support.controller;

import com.joinai_support.domain.Admin;
import com.joinai_support.domain.SupportTicket;
import com.joinai_support.dto.*;
import com.joinai_support.repository.SupportTicketRepository;
import com.joinai_support.service.serviceImpl.AdminServiceImpl;


import com.joinai_support.utils.AdminDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin")
@CrossOrigin("*")
public class AdminController {

    private final AdminServiceImpl adminServiceImpl;

    private final SupportTicketRepository supportTicketRepository;

    @Autowired
    public AdminController(AdminServiceImpl adminServiceImpl, SupportTicketRepository supportTicketRepository) {
        this.adminServiceImpl = adminServiceImpl;

        this.supportTicketRepository = supportTicketRepository;
    }

    @PostMapping("/createAdmin")
    public ResponseEntity<String> createAdmin(@RequestBody UserDTO admin) {
        return adminServiceImpl.createAdmin(admin);
    }

    @PostMapping("/createAgent")
    public ResponseEntity<String> createAgent(@RequestBody UserDTO admin) {
        return adminServiceImpl.createAgent(admin);
    }

    @PostMapping("/authenticate/")
    public ResponseEntity<ResponseDTO> authenticate(@RequestBody AdminLoginRequest authenticationRequest) {
        ResponseDTO response = new ResponseDTO();

        Admin user = adminServiceImpl.getAdmin(authenticationRequest.getEmail());
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();}

        if (!user.getPassword().equals(authenticationRequest.getPassword())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        adminServiceImpl.TrackActivity(user);
        response.setRole(user.getRole());
        response.setId(user.getId());

        return ResponseEntity.ok(response);
    }

    @PostMapping("/forget-password")
    public ResponseEntity<String> forgetPassword(@RequestBody EmailRequest request){
        return adminServiceImpl.forgetPassword(request);
    }


    @PostMapping("/getAgents")
    public ResponseEntity<List<Admin>> getAgents(@RequestBody GetResponse request) {
        return adminServiceImpl.getAllAgents(request);
    }

    @GetMapping("/getAll")
    public ResponseEntity<List<SupportTicket>> getAllTickets() {
        List<SupportTicket> users = supportTicketRepository.findAll();
       return ResponseEntity.ok(users);
    }

    @PostMapping("/updateProfile")
    public ResponseEntity<Admin> editProfile(@RequestBody AdminDTO request) {
        return adminServiceImpl.editProfile(request);
    }

    @PostMapping("/getProfileData")
    public ResponseEntity<AdminDTO> getProfileData(@RequestBody EmailRequest profileRequest) {
        return adminServiceImpl.getProfileData(profileRequest);
    }


    @PostMapping("/deleteProfile")
    public ResponseEntity<Admin> deleteAgentProfile(@RequestBody GetResponse request) {
        return adminServiceImpl.deleteProfile(request);
    }

    @PostMapping("/getAllTickets")
    public ResponseEntity<List<SupportTicket>> getAllTickets(@RequestBody EmailRequest request) {
        Admin admin = adminServiceImpl.getAdmin(request.getEmail());
        if (admin == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        else return adminServiceImpl.getAllTickets();
    }

    @PostMapping("/getAnalytics")
    public ResponseEntity<SystemAnalytics>  getAnalytics() {
        return adminServiceImpl.systemAnalytics();
    }


}
