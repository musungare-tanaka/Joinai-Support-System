package com.joinai_support.dto;

import lombok.Data;

@Data
public class PerformanceDTO {
    // Agent profile
    private String agentName;
    private String agentEmail;
    private String photo = "/Images/pro pic.jpg";

    // Workload
    private long totalTickets;
    private long openTickets;
    private long closedTickets;
    private long newTickets;
    private long oldTickets;
    private long highPriorityTickets;
    private long urgentTickets;
    private long repliesCount;

    // Throughput windows
    private long solvedPast24Hours;
    private long solvedPastWeek;
    private long solvedPastMonth;

    // Quality and speed
    private double frc;
    private double avgResponseTimeMinutes;
    private double avgResolutionTimeMinutes;
    private double resolutionRate;
    private double slaBreachRate;

}
