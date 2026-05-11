package com.example.situation.security;

import java.text.Normalizer;
import java.util.Collection;
import java.util.Locale;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

@Service
public class ProjetAccessService {

    public enum ProjetAccessScope {
        ALL,
        DDZA_ONLY,
        DDZO_ONLY,
        NONE
    }

    public ProjetAccessScope resolveScope(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ProjetAccessScope.NONE;
        }

        Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();
        if (hasAnyRole(authorities, "ROLE_ADMIN", "ROLE_DG", "ROLE_DAF")) {
            return ProjetAccessScope.ALL;
        }
        if (hasAnyRole(authorities, "ROLE_DDZA")) {
            return ProjetAccessScope.DDZA_ONLY;
        }
        if (hasAnyRole(authorities, "ROLE_DDZO")) {
            return ProjetAccessScope.DDZO_ONLY;
        }
        return ProjetAccessScope.NONE;
    }

    public boolean canAccess(ProjetAccessScope scope, String projet) {
        String normalizedProjet = normalize(projet);
        return switch (scope) {
            case ALL -> true;
            case DDZA_ONLY -> normalizedProjet.contains("DDZA");
            case DDZO_ONLY -> normalizedProjet.contains("DDZO");
            case NONE -> false;
        };
    }

    public String requiredProjetToken(ProjetAccessScope scope) {
        return switch (scope) {
            case DDZA_ONLY -> "ddza";
            case DDZO_ONLY -> "ddzo";
            case ALL, NONE -> "";
        };
    }

    private static boolean hasAnyRole(Collection<? extends GrantedAuthority> authorities, String... roles) {
        for (GrantedAuthority authority : authorities) {
            String granted = authority.getAuthority();
            for (String role : roles) {
                if (role.equals(granted)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        String decomposed = Normalizer.normalize(value, Normalizer.Form.NFD);
        return decomposed
            .replaceAll("\\p{M}+", "")
            .replaceAll("[^A-Za-z0-9]", "")
            .toUpperCase(Locale.ROOT)
            .trim();
    }
}
