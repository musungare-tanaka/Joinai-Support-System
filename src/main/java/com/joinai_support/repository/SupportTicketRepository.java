package com.joinai_support.repository;

import com.joinai_support.domain.Admin;
import com.joinai_support.domain.SupportTicket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SupportTicketRepository extends JpaRepository<SupportTicket, Long> {


    List<SupportTicket> findAll();

    List<SupportTicket> findAllByAssignedTo(Admin assignedTo);

    List<SupportTicket> findAllByIssuerEmailIgnoreCaseOrderByLaunchTimestampDesc(String issuerEmail);
}
