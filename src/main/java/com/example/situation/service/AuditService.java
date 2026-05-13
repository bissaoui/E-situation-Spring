package com.example.situation.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AuditService {

    private static final Logger auditLog = LoggerFactory.getLogger("AUDIT");

    public void logAuthSuccess(String username, String role, String ipAddress, String userAgent) {
        auditLog.info("event=AUTH_LOGIN_SUCCESS username={} role={} ip={} userAgent={}",
            safe(username), safe(role), safe(ipAddress), safe(userAgent));
    }

    public void logAuthFailure(String username, String ipAddress, String userAgent, String reason) {
        auditLog.warn("event=AUTH_LOGIN_FAILURE username={} ip={} userAgent={} reason={}",
            safe(username), safe(ipAddress), safe(userAgent), safe(reason));
    }

    public void logRefresh(String username, String ipAddress, String userAgent) {
        auditLog.info("event=AUTH_REFRESH username={} ip={} userAgent={}",
            safe(username), safe(ipAddress), safe(userAgent));
    }

    public void logLogout(String username, String ipAddress, String userAgent) {
        auditLog.info("event=AUTH_LOGOUT username={} ip={} userAgent={}",
            safe(username), safe(ipAddress), safe(userAgent));
    }

    public void logPasswordChange(String username, String ipAddress, String userAgent) {
        auditLog.info("event=ACCOUNT_PASSWORD_CHANGE username={} ip={} userAgent={}",
            safe(username), safe(ipAddress), safe(userAgent));
    }

    public void logSensitiveAction(String action, String actor, String target, String detail) {
        auditLog.info("event={} actor={} target={} detail={}",
            safe(action), safe(actor), safe(target), safe(detail));
    }

    private static String safe(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        return value.replaceAll("[\\r\\n\\t]", "_").trim();
    }
}
