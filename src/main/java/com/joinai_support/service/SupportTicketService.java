package com.joinai_support.service;

import com.joinai_support.domain.Admin;
import com.joinai_support.domain.SupportTicket;
import com.joinai_support.dto.*;
import com.joinai_support.utils.Authenticate;
import com.joinai_support.utils.TicketDTO;
import org.springframework.http.ResponseEntity;

import java.util.List;

public interface SupportTicketService {

    String launchTicket(SupportTicket supportTicket);

    ResponseEntity<String> updateTicket(TicketStatusDTO supportTicket);

    ResponseEntity<List<SupportTicket>> getMyTickets(Authenticate authenticationResponse);

    ResponseEntity<StatisticsDTO> getStatistics();

    ResponseEntity<StatsByAgent> getStatsByAgent(Admin admin);

    ResponseEntity<List<TicketDTO>> getNotifications(String email);
}
