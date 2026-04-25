package com.joinai_support.dto;

import lombok.Data;

@Data
public class SupportTicketRequest {
    private String email;
    private String subject;
    private String content;
    private String source;
}
