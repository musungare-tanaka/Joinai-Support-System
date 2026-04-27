package com.joinai_support.domain;

import com.fasterxml.jackson.annotation.JsonManagedReference;

import com.joinai_support.utils.Gender;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Data
public class Admin extends User {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    private String phone;
    private String name;
    private String address;
    private String city;
    private String state;
    private String zip;
    private String country;
    private LocalDateTime lastLogin;
    private String firstName;
    private String username;
    private Gender gender;
    private Boolean emailNotifications;
    private Boolean inAppNotifications;
    private Boolean ticketEscalationAlerts;
    private String language;
    private String defaultTicketView;
    @Column(length = 1000)
    private String signature;
    private Integer refreshIntervalSeconds;
    private Boolean compactTicketCards;

    @OneToMany(mappedBy = "assignedTo", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference
    private List<SupportTicket> tickets;

    @PrePersist
    public void ensureSettingsDefaults() {
        if (emailNotifications == null) {
            emailNotifications = Boolean.TRUE;
        }
        if (inAppNotifications == null) {
            inAppNotifications = Boolean.TRUE;
        }
        if (ticketEscalationAlerts == null) {
            ticketEscalationAlerts = Boolean.TRUE;
        }
        if (language == null || language.isBlank()) {
            language = "English";
        }
        if (defaultTicketView == null || defaultTicketView.isBlank()) {
            defaultTicketView = "list";
        }
        if (signature == null) {
            signature = "";
        }
        if (refreshIntervalSeconds == null) {
            refreshIntervalSeconds = 60;
        }
        if (compactTicketCards == null) {
            compactTicketCards = Boolean.FALSE;
        }
    }
}
