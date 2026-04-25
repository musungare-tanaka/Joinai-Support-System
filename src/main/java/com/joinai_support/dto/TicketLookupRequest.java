package com.joinai_support.dto;

import lombok.Data;

@Data
public class TicketLookupRequest {
    private Long ticketId;
    private String email;
    private boolean includeClosed;
}
