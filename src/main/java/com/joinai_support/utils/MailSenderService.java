package com.joinai_support.utils;

import com.joinai_support.domain.Admin;
import com.joinai_support.domain.SupportTicket;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.regex.Pattern;

@Service
public class MailSenderService {
    private static final Logger logger = LoggerFactory.getLogger(MailSenderService.class);

    private final JavaMailSender mailSender;

    @Autowired
    public MailSenderService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    /**
     * Sends a welcome email to a new admin/agent
     * @param to Email address of the recipient
     * @param name Name of the recipient
     * @param password Initial password for the account
     */
    @Async
    public void sendWelcomeEmail(String to, String name, String password) {
        String subject = "Welcome to JoinAI Support Platform";
        String text = "Hello " + name + ",\n\n" +
                "Welcome to the JoinAI Support Platform. Your account has been created successfully.\n\n" +
                "Your login credentials:\n" +
                "Email: " + to + "\n" +
                "Password: " + password + "\n\n" +
                "Please change your password after the first login.\n\n" +
                "Best Regards,\nThe JoinAI Support Team";

        sendEmail(to, subject, text);
    }

    /**
     * Sends a notification when a new ticket is created
     * @param ticket The support ticket that was created
     * @param admin The admin/agent the ticket is assigned to
     */
    @Async
    public void sendTicketCreationNotification(SupportTicket ticket, Admin admin) {
        String subject = "New Support Ticket Assigned - #" + ticket.getId();
        String text = "Hello " + admin.getFirstName() + ",\n\n" +
                "A new support ticket has been assigned to you:\n\n" +
                "Ticket ID: " + ticket.getId() + "\n" +
                "Subject: " + ticket.getSubject() + "\n" +
                "Priority: " + ticket.getPriority() + "\n" +
                "Category: " + ticket.getCategory() + "\n" +
                "Created: " + ticket.getLaunchTimestamp() + "\n\n" +
                "Please log in to the support platform to view the details and respond to this ticket.\n\n" +
                "Best Regards,\nThe JoinAI Support Team";

        sendEmail(admin.getEmail(), subject, text);
    }

    /**
     * Sends a notification when a ticket status is updated
     * @param ticket The support ticket that was updated
     * @param admin The admin/agent who is assigned to the ticket
     */
    @Async
    public void sendTicketUpdateNotification(SupportTicket ticket, Admin admin) {
        String subject = "Support Ticket Updated - #" + ticket.getId();
        String text = "Hello " + admin.getFirstName() + ",\n\n" +
                "A support ticket assigned to you has been updated:\n\n" +
                "Ticket ID: " + ticket.getId() + "\n" +
                "Subject: " + ticket.getContent() + "\n" +
                "Status: " + ticket.getStatus() + "\n" +
                "Last Updated: " + ticket.getServedTimestamp() + "\n\n" +
                "Please log in to the support platform to view the details.\n\n" +
                "Best Regards,\nThe JoinAI Support Team";

        sendEmail(admin.getEmail(), subject, text);
    }

    /**
     * Sends a notification to the ticket issuer when their ticket is closed
     *
     * @param ticket The support ticket that was closed
     * @param reply
     */

    //TODO WORK ON THE REPLY TO THE EMAIL
    public void sendTicketClosedNotification(SupportTicket ticket, String reply) {
        if (ticket.getSubject() == null || ticket.getSubject().isEmpty()) {
            logger.warn("Cannot send ticket closed notification: subject (which contains issuer info) is missing for ticket ID: {}",
                    ticket.getId());
            return;
        }

        logger.info("Sending ticket closure notification to: {}", ticket.getIssuerEmail());

        String emailSubject = "Support Ticket Resolved - Reference #" + ticket.getId();

        String emailBody = buildTicketClosureEmailBody(ticket, reply);

        sendEmail(ticket.getIssuerEmail(), emailSubject, emailBody);
    }

    private String buildTicketClosureEmailBody(SupportTicket ticket, String reply) {
        StringBuilder emailBody = new StringBuilder();

        // Format the closure timestamp
        String formattedClosureTime = formatTimestamp(ticket.getServedTimestamp());

        emailBody.append("Dear Valued Customer,\n\n")
                .append("We are pleased to inform you that your support ticket has been successfully resolved.\n\n")
                .append("TICKET SUMMARY:\n")
                .append("─────────────────────────────────\n")
                .append("Ticket Reference: #").append(ticket.getId()).append("\n")
                .append("Subject: ").append(ticket.getSubject()).append("\n")
                .append("Final Status: ").append(ticket.getStatus()).append("\n")
                .append("Priority Level: ").append(ticket.getPriority()).append("\n")
                .append("Resolution Date: ").append(formattedClosureTime).append("\n\n");

        // Include resolution details if reply is provided
        if (reply != null && !reply.trim().isEmpty()) {
            emailBody.append("RESOLUTION DETAILS:\n")
                    .append("─────────────────────────────────\n")
                    .append(reply.trim()).append("\n\n");
        }

        emailBody.append("CUSTOMER SATISFACTION:\n")
                .append("─────────────────────────────────\n")
                .append("We hope our solution has addressed your inquiry satisfactorily. Your feedback is important to us ")
                .append("and helps us improve our services.\n\n")
                .append("NEED ADDITIONAL ASSISTANCE?\n")
                .append("─────────────────────────────────\n")
                .append("• If you have follow-up questions related to this issue, please reply to this email ")
                .append("with reference #").append(ticket.getId()).append("\n")
                .append("• For new or unrelated issues, please submit a new support ticket\n")
                .append("• If you believe this ticket was closed in error, please contact us immediately\n\n")
                .append("We appreciate your patience throughout the resolution process and thank you for choosing JoinAI. ")
                .append("Our team remains committed to providing you with exceptional support.\n\n")
                .append("Thank you for your business.\n\n")
                .append("Best regards,\n\n")
                .append("The JoinAI Customer Support Team\n")
                .append("Your success is our priority");

        return emailBody.toString();
    }
    /**
     * Sends a notification to the ticket issuer when they open a new ticket
     * @param ticket The support ticket that was created
     */


    @Async
    public void sendTicketOpenedNotification(SupportTicket ticket) {
        if (ticket.getSubject() == null || ticket.getSubject().isEmpty()) {
            logger.warn("Cannot send ticket opened notification: subject (which contains issuer info) is missing for ticket ID: {}",
                    ticket.getId());
            return;
        }

        logger.info("Sending ticket creation notification to: {}", ticket.getIssuerEmail());

        String emailSubject = "Support Ticket Created Successfully - Reference #" + ticket.getId();

        String emailBody = buildTicketCreationEmailBody(ticket);

        sendEmail(ticket.getIssuerEmail(), emailSubject, emailBody);
    }

    @Async
    private String buildTicketCreationEmailBody(SupportTicket ticket) {
        StringBuilder emailBody = new StringBuilder();

        // Format the timestamp to be user-friendly
        String formattedTimestamp = formatTimestamp(ticket.getLaunchTimestamp());

        emailBody.append("Dear Valued Customer,\n\n")
                .append("Thank you for contacting JoinAI Support. We have successfully received and processed your support request.\n\n")
                .append("TICKET DETAILS:\n")
                .append("─────────────────────────────────\n")
                .append("Ticket Reference: #").append(ticket.getId()).append("\n")
                .append("Subject: ").append(ticket.getSubject()).append("\n")
                .append("Description: ").append(ticket.getContent()).append("\n")
                .append("Current Status: ").append(ticket.getStatus()).append("\n")
                .append("Date Created: ").append(formattedTimestamp).append("\n\n")
                .append("WHAT HAPPENS NEXT:\n")
                .append("─────────────────────────────────\n")
                .append("• Our technical support team has been automatically notified\n")
                .append("• Your ticket will be reviewed and prioritized based on urgency\n")
                .append("• You will receive email updates as your ticket progresses\n")
                .append("• Please reference ticket #").append(ticket.getId()).append(" in any future correspondence\n\n")
                .append("We appreciate your patience as we work to resolve your inquiry promptly. ")
                .append("Our team is committed to providing you with the highest level of support.\n\n")
                .append("If you have any additional questions or need to provide supplementary information, ")
                .append("please reply to this email with your ticket reference number.\n\n")
                .append("Best regards,\n\n")
                .append("The JoinAI Customer Support Team\n")
                .append("Available 24/7 for your assistance");

        return emailBody.toString();
    }

    private String formatTimestamp(Object timestamp) {
        try {
            if (timestamp instanceof LocalDateTime) {
                LocalDateTime dateTime = (LocalDateTime) timestamp;
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMMM d, yyyy 'at' h:mm a");
                return dateTime.format(formatter);
            } else if (timestamp instanceof String) {
                LocalDateTime dateTime = LocalDateTime.parse(timestamp.toString());
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMMM d, yyyy 'at' h:mm a");
                return dateTime.format(formatter);
            } else {
                return timestamp.toString();
            }
        } catch (Exception e) {
            logger.warn("Failed to format timestamp: {}", timestamp, e);
            return timestamp.toString();
        }
    }

    /**
     * Sends a password reset email with OTP
     * @param otp One-time password for resetting the password
     * @param email Email address of the recipient
     */

    @Async
    public void sendPasswordResetEmail(String otp, String email) {
        String subject = "Password Reset Request - JoinAI Support Platform";
        String text = "Hello,\n\n" +
                "We received a request to reset your password for your JoinAI Support Platform account.\n\n" +
                "Your one-time password (OTP) for password reset is: " + otp + "\n\n" +
                "Please note that this OTP will expire in 15 minutes for security purposes.\n\n" +
                "If you did not request a password reset, please ignore this email or contact our support team immediately.\n\n" +
                "For your security, never share this OTP with anyone.\n\n" +
                "Best regards,\n" +
                "The JoinAI Support Team\n" +
                "support@joinai.com";

        sendEmail(email, subject, text);
    }

    /**
     * Validates if the provided string is a valid email address
     * @param email The email address to validate
     * @return true if the email is valid, false otherwise
     */
    private boolean isValidEmail(String email) {
        if (email == null || email.isEmpty()) {
            return false;
        }
        // RFC 5322 compliant email regex pattern
        String emailRegex = "^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,7}$";
        Pattern pattern = Pattern.compile(emailRegex);
        return pattern.matcher(email).matches();
    }

    /**
     * Helper method to send emails
     * @param to Email address of the recipient
     * @param subject Subject of the email
     * @param text Body of the email
     */

    @Async
    private void sendEmail(String to, String subject, String text) {
        // Validate email address before sending
        if (!isValidEmail(to)) {
            logger.error("Invalid email address: {}, email not sent", to);
            return; // Skip sending email to invalid addresses
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(to);
            message.setSubject(subject);
            message.setText(text);
            mailSender.send(message);
            logger.info("Email sent successfully to {}", to);
        } catch (MailException e) {
            logger.error("Failed to send email to {}: {}", to, e.getMessage());
            throw new RuntimeException("Failed to send email", e);
        }
    }
}
