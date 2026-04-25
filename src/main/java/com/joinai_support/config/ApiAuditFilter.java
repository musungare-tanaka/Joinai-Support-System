package com.joinai_support.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.joinai_support.domain.AuditLog;
import com.joinai_support.service.AuditLogService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
public class ApiAuditFilter extends OncePerRequestFilter {

    private static final int MAX_PAYLOAD_LENGTH = 4000;

    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    public ApiAuditFilter(AuditLogService auditLogService, ObjectMapper objectMapper) {
        this.auditLogService = auditLogService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/actuator")
                || path.startsWith("/swagger-ui")
                || path.startsWith("/v3/api-docs");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        ContentCachingRequestWrapper wrappedRequest = new ContentCachingRequestWrapper(request);
        ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(response);

        long startTime = System.currentTimeMillis();
        try {
            filterChain.doFilter(wrappedRequest, wrappedResponse);
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            storeAuditLog(wrappedRequest, wrappedResponse, duration);
            wrappedResponse.copyBodyToResponse();
        }
    }

    private void storeAuditLog(
            ContentCachingRequestWrapper request,
            ContentCachingResponseWrapper response,
            long duration
    ) {
        String requestBody = sanitizePayload(getRequestBody(request));
        String responseBody = sanitizePayload(getResponseBody(response));

        AuditLog auditLog = new AuditLog();
        auditLog.setMethod(request.getMethod());
        auditLog.setPath(request.getRequestURI());
        auditLog.setQueryString(request.getQueryString());
        auditLog.setStatusCode(response.getStatus());
        auditLog.setClientIp(request.getRemoteAddr());
        auditLog.setUserAgent(request.getHeader("User-Agent"));
        auditLog.setDurationMs(duration);
        auditLog.setActorEmail(resolveActorEmail(request, requestBody));
        auditLog.setRequestBody(requestBody);
        auditLog.setResponseBody(responseBody);

        auditLogService.record(auditLog);
    }

    private String getRequestBody(ContentCachingRequestWrapper request) {
        byte[] content = request.getContentAsByteArray();
        if (content.length == 0) {
            return "";
        }
        return new String(content, StandardCharsets.UTF_8);
    }

    private String getResponseBody(ContentCachingResponseWrapper response) {
        byte[] content = response.getContentAsByteArray();
        if (content.length == 0) {
            return "";
        }
        return new String(content, StandardCharsets.UTF_8);
    }

    private String resolveActorEmail(HttpServletRequest request, String requestBody) {
        String headerEmail = request.getHeader("X-User-Email");
        if (headerEmail != null && !headerEmail.isBlank()) {
            return headerEmail;
        }

        String email = extractField(requestBody, "email");
        if (email != null && !email.isBlank()) {
            return email;
        }

        String token = extractField(requestBody, "token");
        if (token != null && token.contains("@")) {
            return token;
        }

        return "anonymous";
    }

    private String extractField(String json, String fieldName) {
        if (json == null || json.isBlank()) {
            return null;
        }

        try {
            JsonNode node = objectMapper.readTree(json);
            JsonNode value = node.get(fieldName);
            return value != null && value.isTextual() ? value.asText() : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private String sanitizePayload(String payload) {
        if (payload == null || payload.isBlank()) {
            return "";
        }

        String sanitized = payload
                .replaceAll("(?i)\"password\"\\s*:\\s*\"[^\"]*\"", "\"password\":\"***\"")
                .replaceAll("(?i)\"token\"\\s*:\\s*\"[^\"]*\"", "\"token\":\"***\"")
                .replaceAll("(?i)\"secret\"\\s*:\\s*\"[^\"]*\"", "\"secret\":\"***\"");

        if (sanitized.length() > MAX_PAYLOAD_LENGTH) {
            return sanitized.substring(0, MAX_PAYLOAD_LENGTH) + "...(truncated)";
        }

        return sanitized;
    }
}
