package com.joinai_support.dto;

import lombok.Data;

@Data
public class AgentSettingsDTO {
    private String email;
    private Boolean emailNotifications;
    private Boolean inAppNotifications;
    private Boolean ticketEscalationAlerts;
    private String language;
    private String defaultTicketView;
    private String signature;
    private Integer refreshIntervalSeconds;
    private Boolean compactTicketCards;
}
