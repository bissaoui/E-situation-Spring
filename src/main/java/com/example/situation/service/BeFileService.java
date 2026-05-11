package com.example.situation.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class BeFileService {

    private static final Logger log = LoggerFactory.getLogger(BeFileService.class);

    private final Path basePath;

    public BeFileService(@Value("${be.files.base-path:}") String basePath) {
        this.basePath = basePath == null || basePath.isBlank() ? null : Paths.get(basePath).normalize();
    }

    public Optional<Path> resolvePdf(String beValue) throws IOException {
        if (basePath == null || !Files.isDirectory(basePath) || beValue == null || beValue.isBlank()) {
            log.warn("BE resolve skipped. basePath={}, pathExists={}, beValue={}",
                basePath, basePath != null && Files.exists(basePath), beValue);
            return Optional.empty();
        }

        var candidates = BeFileNameResolver.buildCandidates(beValue);
        log.info("Resolving BE file for value='{}' under basePath='{}' with candidates={}", beValue, basePath, candidates);
        for (String candidate : candidates) {
            Path candidatePath = basePath.resolve(candidate + ".pdf").normalize();
            if (candidatePath.startsWith(basePath) && Files.isRegularFile(candidatePath)) {
                log.info("BE file resolved by direct match: {}", candidatePath);
                return Optional.of(candidatePath);
            }
        }

        String canonicalInput = BeFileNameResolver.canonicalBeToken(beValue);
        try (Stream<Path> stream = Files.list(basePath)) {
            for (Path file : stream.filter(Files::isRegularFile).toList()) {
                String filename = file.getFileName().toString();
                if (!filename.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
                    continue;
                }
                if (BeFileNameResolver.canonicalBeToken(filename).equals(canonicalInput)) {
                    log.info("BE file resolved by normalized scan match: {}", file);
                    return Optional.of(file);
                }
            }
        }
        log.warn("No BE file found for value='{}' (canonical='{}') in {}", beValue, canonicalInput, basePath);
        return Optional.empty();
    }

    public String getBasePathDisplay() {
        return basePath == null ? "(not configured)" : basePath.toString();
    }

    public String buildDownloadFilename(String beValue) {
        return BeFileNameResolver.buildDownloadFilename(beValue);
    }
}
