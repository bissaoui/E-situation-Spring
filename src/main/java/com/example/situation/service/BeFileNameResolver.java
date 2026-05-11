package com.example.situation.service;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

final class BeFileNameResolver {

    private BeFileNameResolver() {
    }

    static Set<String> buildCandidates(String input) {
        String raw = sanitizeInput(stripExtension(input));
        Set<String> candidates = new LinkedHashSet<>();
        if (raw.isBlank()) {
            return candidates;
        }

        candidates.add(raw);
        String noBe = raw.replaceFirst("(?i)^BE\\s*", "").trim();
        String normalizedNoBe = normalizeNumericToken(noBe);
        if (!noBe.isBlank()) {
            candidates.add("BE " + noBe);
            candidates.add("BE" + noBe);
            candidates.add(noBe);

            String zeroPaddedNoBe = zeroPadTwoDigits(normalizedNoBe);
            if (!zeroPaddedNoBe.equals(normalizedNoBe)) {
                candidates.add("BE " + zeroPaddedNoBe);
                candidates.add("BE" + zeroPaddedNoBe);
                candidates.add(zeroPaddedNoBe);
            }

            if (!normalizedNoBe.equals(noBe)) {
                candidates.add("BE " + normalizedNoBe);
                candidates.add("BE" + normalizedNoBe);
                candidates.add(normalizedNoBe);
            }
        }
        return candidates;
    }

    static String canonicalBeToken(String value) {
        String token = sanitizeInput(stripExtension(value)).toUpperCase(Locale.ROOT);
        token = token.replaceFirst("^BE\\s*", "");
        token = token.replaceAll("\\s+", "");
        token = normalizeNumericToken(token);
        if (token.matches("\\d+")) {
            token = token.replaceFirst("^0+(?!$)", "");
        }
        return token;
    }

    static String buildDownloadFilename(String input) {
        String raw = sanitizeInput(stripExtension(input));
        if (raw.isBlank()) {
            return "document.pdf";
        }

        String withoutPrefix = raw.replaceFirst("(?i)^BE[\\s_.-]*", "").trim();
        String token = canonicalBeToken(withoutPrefix.isBlank() ? raw : withoutPrefix);
        if (token.isBlank()) {
            return "document.pdf";
        }
        return "BE_" + token + ".pdf";
    }

    private static String sanitizeInput(String input) {
        String value = input.trim()
            .replace('/', ' ')
            .replace('\\', ' ')
            .replace("..", " ")
            .replaceAll("\\s+", " ");
        return value.replaceAll("[^A-Za-z0-9+_. -]", "").trim();
    }

    private static String stripExtension(String filename) {
        int idx = filename.lastIndexOf('.');
        return idx > 0 ? filename.substring(0, idx) : filename;
    }

    private static String normalizeNumericToken(String token) {
        String t = token.trim();
        if (t.matches("\\d+\\.0+")) {
            return t.substring(0, t.indexOf('.'));
        }
        return t;
    }

    private static String zeroPadTwoDigits(String token) {
        String t = token.trim();
        if (t.matches("\\d")) {
            return "0" + t;
        }
        return t;
    }
}
