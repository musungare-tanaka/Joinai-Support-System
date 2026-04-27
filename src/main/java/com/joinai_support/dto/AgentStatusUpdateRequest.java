package com.joinai_support.dto;

import lombok.Data;

@Data
public class AgentStatusUpdateRequest {
    private String token;
    private String agentEmail;
    private Boolean enabled;
}
