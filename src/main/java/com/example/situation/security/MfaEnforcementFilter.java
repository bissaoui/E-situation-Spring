package com.example.situation.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class MfaEnforcementFilter extends OncePerRequestFilter {

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {

        Object mfaPending = request.getAttribute("mfa_pending");
        if (!(mfaPending instanceof Boolean) || !((Boolean) mfaPending)) {
            filterChain.doFilter(request, response);
            return;
        }

        String path = request.getRequestURI();
        boolean allowed = path.startsWith("/api/account/mfa/")
            || path.startsWith("/api/v1/account/mfa/")
            || path.equals("/api/account/mfa")
            || path.equals("/api/v1/account/mfa");

        if (allowed) {
            filterChain.doFilter(request, response);
            return;
        }

        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"error\":\"MFA_SETUP_REQUIRED\",\"message\":\"Multi-factor authentication setup must be completed before accessing this resource.\"}");
    }
}
