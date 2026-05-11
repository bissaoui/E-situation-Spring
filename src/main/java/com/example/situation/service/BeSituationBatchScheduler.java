package com.example.situation.service;

import com.example.situation.repository.SituationRepository;
import java.io.InputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BeSituationBatchScheduler {

    private static final Logger log = LoggerFactory.getLogger(BeSituationBatchScheduler.class);

    private final SituationRepository situationRepository;
    private final SituationImportService situationImportService;
    private final OneDriveBatchImportService oneDriveBatchImportService;
    private final boolean enabled;
    private final String source;
    private final Path watchDirectory;
    private final String filePrefix;

    private volatile String lastImportedSignature;

    public BeSituationBatchScheduler(
        SituationRepository situationRepository,
        SituationImportService situationImportService,
        OneDriveBatchImportService oneDriveBatchImportService,
        @Value("${app.batch.be-watch.enabled:true}") boolean enabled,
        @Value("${app.batch.be-watch.source:local}") String source,
        @Value("${app.batch.be-watch.directory:${be.files.base-path:}}") String watchDirectory,
        @Value("${app.batch.be-watch.file-prefix:BE-2025}") String filePrefix
    ) {
        this.situationRepository = situationRepository;
        this.situationImportService = situationImportService;
        this.oneDriveBatchImportService = oneDriveBatchImportService;
        this.enabled = enabled;
        this.source = source == null ? "local" : source.trim().toLowerCase(Locale.ROOT);
        this.watchDirectory = watchDirectory == null || watchDirectory.isBlank()
            ? null
            : Paths.get(watchDirectory).normalize();
        this.filePrefix = filePrefix == null ? "" : filePrefix.trim().toLowerCase(Locale.ROOT);
    }

    @Scheduled(fixedDelayString = "${app.batch.be-watch.fixed-delay-ms:300000}")
    @Transactional(rollbackFor = Exception.class)
    public void refreshSituationsWhenFileNameChanges() {
        if (!enabled) {
            return;
        }
        if ("onedrive".equals(source)) {
            refreshFromOneDrive();
            return;
        }
        refreshFromLocalDirectory();
    }

    private void refreshFromLocalDirectory() {
        if (watchDirectory == null || !Files.isDirectory(watchDirectory)) {
            log.debug("BE batch disabled for this tick: watch directory is invalid ({})", watchDirectory);
            return;
        }

        Path latestFile;
        try {
            Optional<Path> file = findLatestBeFile();
            if (file.isEmpty()) {
                return;
            }
            latestFile = file.get();
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot scan BE directory: " + watchDirectory, ex);
        }

        String currentFilename = latestFile.getFileName().toString();
        if (currentFilename.equalsIgnoreCase(lastImportedSignature)) {
            return;
        }

        try {
            long existingCount = situationRepository.count();
            situationRepository.deleteAllInBatch();
            int imported = situationImportService.importFile(latestFile);
            lastImportedSignature = currentFilename;
            log.info(
                "BE file changed to '{}' -> situation table replaced (deleted={}, imported={})",
                currentFilename,
                existingCount,
                imported
            );
        } catch (Exception ex) {
            throw new IllegalStateException("BE batch import failed for file: " + latestFile, ex);
        }
    }

    private void refreshFromOneDrive() {
        if (!oneDriveBatchImportService.isConfigured()) {
            log.debug("BE OneDrive batch disabled for this tick: OneDrive configuration is incomplete.");
            return;
        }

        OneDriveBatchImportService.OneDriveFileDescriptor latestFile = oneDriveBatchImportService
            .findLatestImportFile(filePrefix)
            .orElse(null);
        if (latestFile == null) {
            return;
        }

        String currentSignature = latestFile.importSignature();
        if (currentSignature.equals(lastImportedSignature)) {
            return;
        }

        try (InputStream inputStream = oneDriveBatchImportService.downloadFile(latestFile)) {
            long existingCount = situationRepository.count();
            situationRepository.deleteAllInBatch();
            int imported = situationImportService.importFile(latestFile.name(), inputStream);
            lastImportedSignature = currentSignature;
            log.info(
                "BE OneDrive file changed to '{}' -> situation table replaced (deleted={}, imported={})",
                latestFile.name(),
                existingCount,
                imported
            );
        } catch (Exception ex) {
            throw new IllegalStateException("BE OneDrive batch import failed for file: " + latestFile.name(), ex);
        }
    }

    private Optional<Path> findLatestBeFile() throws IOException {
        try (Stream<Path> stream = Files.list(watchDirectory)) {
            return stream
                .filter(Files::isRegularFile)
                .filter(this::matchesExpectedName)
                .max(Comparator.comparingLong(this::safeLastModifiedMillis));
        }
    }

    private boolean matchesExpectedName(Path path) {
        String filename = path.getFileName().toString().toLowerCase(Locale.ROOT);
        boolean extensionOk = filename.endsWith(".xlsx") || filename.endsWith(".xls");
        boolean prefixOk = filePrefix.isBlank() || filename.startsWith(filePrefix);
        return extensionOk && prefixOk;
    }

    private long safeLastModifiedMillis(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException ex) {
            return Long.MIN_VALUE;
        }
    }
}
