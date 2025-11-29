package com.joinai_support.service;

import com.joinai_support.domain.Admin;
import com.joinai_support.domain.SupportTicket;
import com.joinai_support.dto.*;
import com.joinai_support.utils.AdminDTO;
import org.springframework.http.ResponseEntity;

import java.util.List;

public interface AdminService {

    ResponseEntity<String> createAgent(UserDTO admin);

    ResponseEntity<String> createAdmin(UserDTO admin);

    ResponseEntity<List<Admin>> getAll();

    Admin getAdmin(String email);

    ResponseEntity<List<Admin>> getAllAgents(GetResponse request);

    ResponseEntity<Admin> editProfile(AdminDTO request);

    ResponseEntity<Admin> deleteProfile(GetResponse request);

    void TrackActivity(Admin agent);

    ResponseEntity<List<SupportTicket>> getAllTickets();

    ResponseEntity<SystemAnalytics> systemAnalytics();

    ResponseEntity<AdminDTO> getProfileData(EmailRequest profileRequest);

    ResponseEntity<String> forgetPassword(EmailRequest request);
}
