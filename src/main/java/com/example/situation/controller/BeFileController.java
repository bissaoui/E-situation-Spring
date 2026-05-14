package com.example.situation.controller;

import com.example.situation.dto.BeFileMetadataResponse;
import com.example.situation.model.Situation;
import com.example.situation.repository.SituationRepository;
import com.example.situation.security.ProjetAccessService;
import com.example.situation.security.ProjetAccessService.ProjetAccessScope;
import com.example.situation.security.RemoteBeUrlValidator;
import com.example.situation.service.AuditService;
import com.example.situation.service.BeFileDownloadProgressService;
import com.example.situation.service.BeFileService;
import com.example.situation.service.OneDriveBatchImportService;
import jakarta.servlet.http.HttpServletResponse;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

@RestController
public class BeFileController {

    private static final Logger log = LoggerFactory.getLogger(BeFileController.class);

    private final SituationRepository situationRepository;
    private final BeFileService beFileService;
    private final OneDriveBatchImportService oneDriveBatchImportService;
    private final ProjetAccessService projetAccessService;
    private final RemoteBeUrlValidator remoteBeUrlValidator;
    private final AuditService auditService;
    private final BeFileDownloadProgressService downloadProgressService;
    private final RestClient restClient;

    public BeFileController(
        SituationRepository situationRepository,
        BeFileService beFileService,
        OneDriveBatchImportService oneDriveBatchImportService,
        ProjetAccessService projetAccessService,
        RemoteBeUrlValidator remoteBeUrlValidator,
        AuditService auditService,
        BeFileDownloadProgressService downloadProgressService
    ) {
        this.situationRepository = situationRepository;
        this.beFileService = beFileService;
        this.oneDriveBatchImportService = oneDriveBatchImportService;
        this.projetAccessService = projetAccessService;
        this.remoteBeUrlValidator = remoteBeUrlValidator;
        this.auditService = auditService;
        this.downloadProgressService = downloadProgressService;
        this.restClient = RestClient.builder().build();
    }

    @GetMapping({"/be-files/situation/{id}", "/api/be-files/situation/{id}", "/api/v1/be-files/situation/{id}"})
    public ResponseEntity<?> resolveBySituationId(
        @PathVariable Long id,
        @RequestParam(name = "download", defaultValue = "false") boolean download,
        @RequestParam(name = "downloadId", required = false) String downloadId,
        Authentication authentication,
        HttpServletResponse response
    ) throws IOException {
        ProjetAccessScope scope = projetAccessService.resolveScope(authentication);
        if (scope == ProjetAccessScope.NONE) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        Optional<Situation> situationOpt = situationRepository.findById(id);
        if (situationOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        Situation situation = situationOpt.get();
        if (!projetAccessService.canAccess(scope, situation.getProjet())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        auditService.logSensitiveAction("BE_FILE_ACCESS", actor(authentication), "situation:" + id, "download=" + download);
        String downloadFilename = beFileService.buildDownloadFilename(
            firstNonBlank(situation.getBe(), situation.getNumeroOv(), id.toString())
        );
        if (situation.getBeUrl() != null && !situation.getBeUrl().isBlank()) {
            if (download) {
                serveRemotePdf(situation.getBeUrl(), downloadFilename, true, downloadId, actor(authentication), response);
                return null;
            }
            return redirectToCloud(situation.getBeUrl(), false);
        }

        if (situation.getBe() != null && !situation.getBe().isBlank()) {
            return servePdf(situation.getBe(), downloadFilename, download, downloadId, actor(authentication), response);
        }
        if (situation.getNumeroOv() != null && !situation.getNumeroOv().isBlank()) {
            return servePdf(situation.getNumeroOv(), downloadFilename, download, downloadId, actor(authentication), response);
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }

    @GetMapping({
        "/be-files/situation/{id}/metadata",
        "/api/be-files/situation/{id}/metadata",
        "/api/v1/be-files/situation/{id}/metadata"
    })
    public ResponseEntity<?> metadataBySituationId(
        @PathVariable Long id,
        Authentication authentication
    ) throws IOException {
        ProjetAccessScope scope = projetAccessService.resolveScope(authentication);
        if (scope == ProjetAccessScope.NONE) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        Optional<Situation> situationOpt = situationRepository.findById(id);
        if (situationOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        Situation situation = situationOpt.get();
        if (!projetAccessService.canAccess(scope, situation.getProjet())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        String downloadFilename = beFileService.buildDownloadFilename(
            firstNonBlank(situation.getBe(), situation.getNumeroOv(), id.toString())
        );
        return resolveMetadataForSituation(situation, downloadFilename);
    }

    @GetMapping({"/be-files/{beRef}", "/api/be-files/{beRef}", "/api/v1/be-files/{beRef}"})
    public ResponseEntity<?> resolveByBeRef(
        @PathVariable String beRef,
        @RequestParam(name = "download", defaultValue = "false") boolean download,
        @RequestParam(name = "downloadId", required = false) String downloadId,
        Authentication authentication,
        HttpServletResponse response
    ) throws IOException {
        ProjetAccessScope scope = projetAccessService.resolveScope(authentication);
        if (scope == ProjetAccessScope.NONE) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        Optional<Situation> situationOpt = findByBeReference(beRef, scope);
        if (situationOpt.isEmpty()) {
            situationOpt = findByNumeroOvReference(beRef, scope);
        }
        if (situationOpt.isEmpty()) {
            if (scope == ProjetAccessScope.ALL) {
                auditService.logSensitiveAction("BE_FILE_ACCESS", actor(authentication), "beRef:" + beRef, "download=" + download);
                return servePdf(beRef, beFileService.buildDownloadFilename(beRef), download, downloadId, actor(authentication), response);
            }
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        Situation matchedSituation = situationOpt.get();
        String downloadFilename = beFileService.buildDownloadFilename(
            firstNonBlank(matchedSituation.getBe(), matchedSituation.getNumeroOv(), beRef)
        );
        auditService.logSensitiveAction("BE_FILE_ACCESS", actor(authentication), "beRef:" + beRef, "download=" + download);

        if (matchedSituation.getBeUrl() != null && !matchedSituation.getBeUrl().isBlank()) {
            if (download) {
                serveRemotePdf(matchedSituation.getBeUrl(), downloadFilename, true, downloadId, actor(authentication), response);
                return null;
            }
            return redirectToCloud(matchedSituation.getBeUrl(), false);
        }

        String localBeReference = firstNonBlank(matchedSituation.getBe(), matchedSituation.getNumeroOv(), beRef);
        return servePdf(localBeReference, downloadFilename, download, downloadId, actor(authentication), response);
    }

    @GetMapping({
        "/api/be-files/download-progress/{downloadId}",
        "/api/v1/be-files/download-progress/{downloadId}"
    })
    public ResponseEntity<BeFileDownloadProgressService.ProgressSnapshot> downloadProgress(
        @PathVariable String downloadId,
        Authentication authentication
    ) {
        var snapshot = downloadProgressService.get(downloadId, actor(authentication));
        return snapshot == null
            ? ResponseEntity.status(HttpStatus.NOT_FOUND).build()
            : ResponseEntity.ok(snapshot);
    }

    @GetMapping({"/be-files/{beRef}/metadata", "/api/be-files/{beRef}/metadata", "/api/v1/be-files/{beRef}/metadata"})
    public ResponseEntity<?> metadataByBeRef(
        @PathVariable String beRef,
        Authentication authentication
    ) throws IOException {
        ProjetAccessScope scope = projetAccessService.resolveScope(authentication);
        if (scope == ProjetAccessScope.NONE) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        Optional<Situation> situationOpt = findByBeReference(beRef, scope);
        if (situationOpt.isEmpty()) {
            situationOpt = findByNumeroOvReference(beRef, scope);
        }

        if (situationOpt.isEmpty()) {
            if (scope == ProjetAccessScope.ALL) {
                return resolvePdfMetadata(beRef, beFileService.buildDownloadFilename(beRef));
            }
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        Situation matchedSituation = situationOpt.get();
        String downloadFilename = beFileService.buildDownloadFilename(
            firstNonBlank(matchedSituation.getBe(), matchedSituation.getNumeroOv(), beRef)
        );
        return resolveMetadataForSituation(matchedSituation, downloadFilename);
    }

    private ResponseEntity<Void> redirectToCloud(String beUrl, boolean download) {
        var target = remoteBeUrlValidator.validate(beUrl, download);
        log.info("Redirecting to approved remote BE host='{}' download={}", target.getHost(), download);
        return ResponseEntity.status(HttpStatus.FOUND)
            .header(HttpHeaders.LOCATION, target.toString())
            .build();
    }

    private ResponseEntity<?> servePdf(
        String beRef,
        String downloadFilename,
        boolean download,
        String downloadId,
        String actor,
        HttpServletResponse response
    ) throws IOException {
        Optional<Path> fileOpt = beFileService.resolvePdf(beRef);
        if (fileOpt.isPresent()) {
            if (download) {
                serveLocalPdf(fileOpt.get(), downloadFilename, true, downloadId, actor, response);
                return null;
            }
            return serveLocalPdf(fileOpt.get(), downloadFilename, download);
        }

        Optional<OneDriveBatchImportService.OneDriveFileDescriptor> cloudFile =
            oneDriveBatchImportService.findPdfByReference(beRef);
        if (cloudFile.isPresent()) {
            serveOneDrivePdf(cloudFile.get(), downloadFilename, download, downloadId, actor, response);
            return null;
        }

        log.warn("BE file not found for reference='{}'.", beRef);
        return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }

    private ResponseEntity<BeFileMetadataResponse> resolveMetadataForSituation(
        Situation situation,
        String downloadFilename
    ) throws IOException {
        if (situation.getBeUrl() != null && !situation.getBeUrl().isBlank()) {
            return ResponseEntity.ok(resolveRemotePdfMetadata(situation.getBeUrl(), downloadFilename));
        }

        String localBeReference = firstNonBlank(
            situation.getBe(),
            situation.getNumeroOv(),
            downloadFilename
        );
        return resolvePdfMetadata(localBeReference, downloadFilename);
    }

    private ResponseEntity<BeFileMetadataResponse> resolvePdfMetadata(
        String beRef,
        String downloadFilename
    ) throws IOException {
        Optional<Path> fileOpt = beFileService.resolvePdf(beRef);
        if (fileOpt.isPresent()) {
            return ResponseEntity.ok(buildMetadataResponse(downloadFilename, Files.size(fileOpt.get()), "LOCAL"));
        }

        Optional<OneDriveBatchImportService.OneDriveFileDescriptor> cloudFile =
            oneDriveBatchImportService.findPdfByReference(beRef);
        if (cloudFile.isPresent()) {
            return ResponseEntity.ok(buildMetadataResponse(downloadFilename, toNullableLength(cloudFile.get().size()), "ONEDRIVE"));
        }

        return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }

    private ResponseEntity<Resource> serveLocalPdf(Path file, String downloadFilename, boolean download) throws IOException {
        Resource resource = new UrlResource(file.toUri());
        if (!resource.exists() || !resource.isReadable()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_PDF)
            .contentLength(Files.size(file))
            .header(HttpHeaders.CONTENT_DISPOSITION, buildDisposition(downloadFilename, download).toString())
            .body(resource);
    }

    private void serveLocalPdf(
        Path file,
        String downloadFilename,
        boolean download,
        String downloadId,
        String actor,
        HttpServletResponse response
    ) throws IOException {
        if (!Files.isRegularFile(file)) {
            response.sendError(HttpStatus.NOT_FOUND.value());
            return;
        }
        long contentLength = Files.size(file);
        setPdfResponseHeaders(response, downloadFilename, download, contentLength);
        downloadProgressService.start(downloadId, actor, contentLength);
        try (var inputStream = Files.newInputStream(file)) {
            StreamUtils.copy(inputStream, progressStream(response.getOutputStream(), downloadId));
            response.flushBuffer();
            downloadProgressService.complete(downloadId);
        } catch (IOException ex) {
            downloadProgressService.fail(downloadId, ex.getMessage());
            throw ex;
        }
    }

    private void serveOneDrivePdf(
        OneDriveBatchImportService.OneDriveFileDescriptor fileDescriptor,
        String downloadFilename,
        boolean download,
        String downloadId,
        String actor,
        HttpServletResponse response
    ) throws IOException {
        Long contentLength = toNullableLength(fileDescriptor.size());
        setPdfResponseHeaders(response, downloadFilename, download, contentLength);
        downloadProgressService.start(downloadId, actor, contentLength);
        try {
            oneDriveBatchImportService.writeFileTo(fileDescriptor, progressStream(response.getOutputStream(), downloadId));
            response.flushBuffer();
            downloadProgressService.complete(downloadId);
        } catch (RuntimeException | IOException ex) {
            downloadProgressService.fail(downloadId, ex.getMessage());
            throw ex;
        }
    }

    private void serveRemotePdf(
        String beUrl,
        String downloadFilename,
        boolean download,
        String downloadId,
        String actor,
        HttpServletResponse response
    ) throws IOException {
        var target = remoteBeUrlValidator.validate(beUrl, download);
        log.info("Streaming remote BE from approved host='{}' with download filename='{}'", target.getHost(), downloadFilename);

        Long contentLength = fetchRemoteContentLength(target);
        setPdfResponseHeaders(response, downloadFilename, download, contentLength);
        downloadProgressService.start(downloadId, actor, contentLength);
        try {
            streamRemotePdf(target, progressStream(response.getOutputStream(), downloadId));
            response.flushBuffer();
            downloadProgressService.complete(downloadId);
        } catch (RuntimeException | IOException ex) {
            downloadProgressService.fail(downloadId, ex.getMessage());
            throw ex;
        }
    }

    private BeFileMetadataResponse resolveRemotePdfMetadata(String beUrl, String downloadFilename) {
        var target = remoteBeUrlValidator.validate(beUrl, true);
        return buildMetadataResponse(downloadFilename, fetchRemoteContentLength(target), "REMOTE");
    }

    private Long fetchRemoteContentLength(java.net.URI target) {
        try {
            return restClient.head()
                .uri(target)
                .exchange((request, response) -> {
                    if (response.getStatusCode().is3xxRedirection()) {
                        var redirectLocation = response.getHeaders().getLocation();
                        return redirectLocation == null ? null : fetchRemoteContentLength(redirectLocation);
                    }
                    if (!response.getStatusCode().is2xxSuccessful()) {
                        return null;
                    }
                    return toNullableLength(response.getHeaders().getContentLength());
                });
        } catch (Exception ex) {
            log.warn("Remote BE metadata lookup failed for host='{}': {}", target.getHost(), ex.getMessage());
            return null;
        }
    }

    private BeFileMetadataResponse buildMetadataResponse(String fileName, Long contentLength, String source) {
        return new BeFileMetadataResponse(fileName, contentLength, source);
    }

    private void setPdfResponseHeaders(
        HttpServletResponse response,
        String downloadFilename,
        boolean download,
        Long contentLength
    ) {
        response.setStatus(HttpStatus.OK.value());
        response.setContentType(MediaType.APPLICATION_PDF_VALUE);
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, buildDisposition(downloadFilename, download).toString());
        if (contentLength != null && contentLength > 0) {
            response.setContentLengthLong(contentLength);
        }
    }

    private void streamRemotePdf(URI target, OutputStream outputStream) {
        restClient.get()
            .uri(target)
            .exchange((request, response) -> {
                if (response.getStatusCode().is3xxRedirection()) {
                    URI redirectLocation = response.getHeaders().getLocation();
                    if (redirectLocation == null) {
                        throw new IllegalStateException("Remote BE redirected without a location.");
                    }
                    streamRemotePdf(redirectLocation, outputStream);
                    return null;
                }
                if (!response.getStatusCode().is2xxSuccessful()) {
                    throw new IllegalStateException("Remote BE download failed with status: " + response.getStatusCode().value());
                }
                StreamUtils.copy(response.getBody(), outputStream);
                outputStream.flush();
                return null;
            });
    }

    private OutputStream progressStream(OutputStream delegate, String downloadId) {
        if (!BeFileDownloadProgressService.isValidDownloadId(downloadId)) {
            return delegate;
        }
        return new FilterOutputStream(delegate) {
            private long bytesWritten;

            @Override
            public void write(int b) throws IOException {
                out.write(b);
                bytesWritten++;
                downloadProgressService.update(downloadId, bytesWritten);
            }

            @Override
            public void write(byte[] b, int off, int len) throws IOException {
                out.write(b, off, len);
                bytesWritten += len;
                downloadProgressService.update(downloadId, bytesWritten);
            }
        };
    }

    private ContentDisposition buildDisposition(String filename, boolean download) {
        return download
            ? ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build()
            : ContentDisposition.inline().filename(filename, StandardCharsets.UTF_8).build();
    }

    private Optional<Situation> findByBeReference(String beRef, ProjetAccessScope scope) {
        return switch (scope) {
            case ALL -> situationRepository.findTopByBeIgnoreCaseOrderByIdDesc(beRef);
            case DDZA_ONLY, DDZO_ONLY -> situationRepository
                .findTopByBeIgnoreCaseAndProjetContainingIgnoreCaseOrderByIdDesc(
                    beRef,
                    projetAccessService.requiredProjetToken(scope)
                );
            case NONE -> Optional.empty();
        };
    }

    private Optional<Situation> findByNumeroOvReference(String beRef, ProjetAccessScope scope) {
        return switch (scope) {
            case ALL -> situationRepository.findTopByNumeroOvIgnoreCaseOrderByIdDesc(beRef);
            case DDZA_ONLY, DDZO_ONLY -> situationRepository
                .findTopByNumeroOvIgnoreCaseAndProjetContainingIgnoreCaseOrderByIdDesc(
                    beRef,
                    projetAccessService.requiredProjetToken(scope)
                );
            case NONE -> Optional.empty();
        };
    }

    private static String firstNonBlank(String first, String second, String fallback) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        if (second != null && !second.isBlank()) {
            return second;
        }
        return fallback;
    }

    private static Long toNullableLength(Long value) {
        return value != null && value >= 0 ? value : null;
    }

    private static Long toNullableLength(long value) {
        return value >= 0 ? value : null;
    }

    private static String actor(Authentication authentication) {
        return authentication == null ? "-" : authentication.getName();
    }
}
