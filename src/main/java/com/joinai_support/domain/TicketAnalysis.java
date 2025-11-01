package com.joinai_support.domain;

import com.joinai_support.utils.Category;
import com.joinai_support.utils.Priority;
import com.joinai_support.utils.Status;
import com.joinai_support.utils.TicketSource;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static javax.naming.ldap.Control.CRITICAL;

@Document(collection = "ticket_analysis")
@Data
public class TicketAnalysis {

    @Id
    private String ticketId;
    private String question;
    private String issuerEmail;
    private List<String> replies = new ArrayList<>();

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime resolvedAt;
    private Long resolutionTimeMinutes;

    private Status status;
    private Priority priority;
    private Category category;

    private TicketSource source;

    private Integer totalReplies;
    private LocalDateTime lastReplyAt;
    private Boolean requiresFollowup;

    private String periodKey;                       // "2024-W45" for trend analysis
    private String timeToResolveBucket;             // "<1h", "1-4h", "1-3d", ">3d"
    private Boolean isHighPriority;                 // Derived field for quick filtering

    private LocalDateTime firstResponseAt;          // When agent first replied
    private Long firstResponseTimeMinutes;          // Time to first response
    private String slaStatus;                       // "MET", "MISSED", "AT_RISK"
    private Integer customerSatisfactionScore;      // 1-5 scale

    private String businessImpact;                  // "LOW", "MEDIUM", "HIGH"
    private String affectedProduct;                 // Which product/service
    private Boolean isRevenueAffecting;             // Does this impact revenue?
    private String resolutionType;                  // "FIX", "WORKAROUND", "REFUND"

    public TicketAnalysis(String ticketId, String question, String issuerEmail) {
    }


    // BUSINESS METHODS
    public void recordFirstResponse(LocalDateTime responseTime) {
        if (this.firstResponseAt == null) {
            this.firstResponseAt = responseTime;
            this.firstResponseTimeMinutes = calculateMinutesBetween(createdAt, responseTime);
        }
    }

    public void markAsResolved(LocalDateTime resolutionTime) {
        this.resolvedAt = resolutionTime;
        this.status = Status.CLOSED;
        this.resolutionTimeMinutes = calculateMinutesBetween(createdAt, resolutionTime);
        this.timeToResolveBucket = calculateTimeBucket(resolutionTimeMinutes);
        updateSlaStatus();
        updateDerivedFields();
    }

    public void addReply(String reply, String repliedBy) {
        this.replies.add(reply);
        this.lastReplyAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.totalReplies = this.replies.size();

        if (repliedBy.startsWith("agent:") && firstResponseAt == null) {
            recordFirstResponse(lastReplyAt);
        }

        updateDerivedFields();
    }

    // ✅ HELPER METHODS (Private - internal use only)
    private void updateDerivedFields() {
        this.isHighPriority = (priority == Priority.HIGH || priority == Priority.URGENT);
        this.periodKey = generatePeriodKey(createdAt);
    }

    private void updateSlaStatus() {
        if (resolutionTimeMinutes == null) return;

        long slaThreshold = getSlaThresholdForPriority();
        this.slaStatus = (resolutionTimeMinutes <= slaThreshold) ? "MET" : "MISSED";
    }

    private Long calculateMinutesBetween(LocalDateTime start, LocalDateTime end) {
        return (start != null && end != null) ?
                Duration.between(start, end).toMinutes() : null;
    }

    private String calculateTimeBucket(Long minutes) {
        if (minutes == null) return "PENDING";
        if (minutes <= 60) return "<1h";
        if (minutes <= 240) return "1-4h";
        if (minutes <= 4320) return "1-3d";
        return ">3d";
    }

    private String generatePeriodKey(LocalDateTime date) {
        return date.getYear() + "-W" +
                String.format("%02d", (date.getDayOfYear() - 1) / 7 + 1);
    }

    private long getSlaThresholdForPriority() {
        return switch (this.priority) {
            case CRITICAL -> 60L;    // 1 hour
            case URGENT -> 240L;     // 4 hours
            case HIGH -> 1440L;      // 24 hours
            case MEDIUM -> 4320L;    // 3 days
            case LOW -> 10080L;      // 7 days
            default -> 4320L;
        };
    }



    public void addReply(String reply) {
        this.replies.add(reply);
    }
}
