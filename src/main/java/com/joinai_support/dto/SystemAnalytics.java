package com.joinai_support.dto;

import lombok.Data;

import java.util.List;

@Data
public class SystemAnalytics {

    private long totalTickets;
    private long openTickets;
    private long closedTickets;
    private long newTickets;
    private long totalAgents;
    private long dailyTickets;
    private long weeklyTickets;
    private long monthlyTickets;
    private long resolvedToday;
    private long resolvedThisWeek;
    private long resolvedThisMonth;
    private double avgResponseTimeMinutes;
    private double avgResolutionTimeMinutes;
    private double closureRate;
    private double frcRate;
    private double slaBreachRate;
    private List<PerformanceDTO> performance;
    private List<Ticket> tickets;

}
