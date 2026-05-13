package com.example.situation.controller;

import com.example.situation.dto.BeFileMetadataResponse;
import com.example.situation.model.Situation;
import com.example.situation.repository.SituationRepository;
import com.example.situation.security.ProjetAccessService;
import com.example.situation.security.ProjetAccessService.ProjetAccessScope;
import com.example.situation.service.BeFileService;
import com.example.situation.service.OneDriveBatchImportService;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

@RestController
public class BeFileController {

    private static final Logger log = LoggerFactory.getLogger(BeFileController.class);

    private final SituationRepository situationRepository;
    private final BeFileService beFileService;
    private final OneDriveBatchImportService oneDriveBatchImportService;
    private final ProjetAccessService projetAccessService;
    private final RestClient restClient;

    public BeFileController(
        SituationRepository situationRepository,
        BeFileService beFileService,
        OneDriveBatchImportService oneDriveBatchImportService,
        ProjetAccessService projetAccessService
    ) {
        this.situationRepository = situationRepository;
        this.beFileService = beFileService;
        this.oneDriveBatchImportService = oneDriveBatchImportService;
        this.projetAccessService = projetAccessService;
        this.restClient = RestClient.builder().build();
    }

    @GetMapping({"/be-files/situation/{id}", "/api/be-files/situation/{id}"})
    public ResponseEntity<?> resolveBySituationId(
        @PathVariable Long id,
        @RequestParam(name = "download", defaultValue = "false") boolean download,
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

        Situation s = situationOpt.get();
        if (!projetAccessService.canAccess(scope, s.getProjet())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        log.info("BE request by situation id={}, download={}", id, download);
        String downloadFilename = beFileService.buildDownloadFilename(firstNonBlank(s.getBe(), s.getNumeroOv(), id.toString()));
        if (s.getBeUrl() != null && !s.getBeUrl().isBlank()) {
            if (download) {
                return serveRemotePdf(s.getBeUrl(), downloadFilename, true);
            }
            return redirectToCloud(s.getBeUrl(), false);
        }

        if (s.getBe() != null && !s.getBe().isBlank()) {
            return servePdf(s.getBe(), downloadFilename, download);
        }
        if (s.getNumeroOv() != null && !s.getNumeroOv().isBlank()) {
            return servePdf(s.getNumeroOv(), downloadFilename, download);
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }

    @GetMapping({"/be-files/situation/{id}/metadata", "/api/be-files/situation/{id}/metadata"})
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

        Situation s = situationOpt.get();
        if (!projetAccessService.canAccess(scope, s.getProjet())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        String downloadFilename = beFileService.buildDownloadFilename(firstNonBlank(s.getBe(), s.getNumeroOv(), id.toString()));
        return resolveMetadataForSituation(s, downloadFilename);
    }

    @GetMapping({"/be-files/{beRef}", "/api/be-files/{beRef}"})
    public ResponseEntity<?> resolveByBeRef(
        @PathVariable String beRef,
        @RequestParam(name = "download", defaultValue = "false") boolean download,
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
                return servePdf(beRef, beFileService.buildDownloadFilename(beRef), download);
            }
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        Situation matchedSituation = situationOpt.get();
        String downloadFilename = beFileService.buildDownloadFilename(
            firstNonBlank(matchedSituation.getBe(), matchedSituation.getNumeroOv(), beRef)
        );
        if (matchedSituation.getBeUrl() != null && !matchedSituation.getBeUrl().isBlank()) {
            if (download) {
                return serveRemotePdf(matchedSituation.getBeUrl(), downloadFilename, true);
            }
            return redirectToCloud(matchedSituation.getBeUrl(), false);
        }

        String localBeReference = firstNonBlank(matchedSituation.getBe(), matchedSituation.getNumeroOv(), beRef);
        return servePdf(localBeReference, downloadFilename, download);
    }

    @GetMapping({"/be-files/{beRef}/metadata", "/api/be-files/{beRef}/metadata"})
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
        URI target = UriComponentsBuilder.fromUriString(beUrl.trim())
            .replaceQueryParam("download", download ? "1" : "0")
            .build(true)
            .toUri();
        log.info("Redirecting to cloud BE URL='{}' (download={})", target, download);
        return ResponseEntity.status(HttpStatus.FOUND)
            .header(HttpHeaders.LOCATION, target.toString())
            .build();
    }

    private ResponseEntity<Resource> servePdf(String beRef, String downloadFilename, boolean download) throws IOException {
        log.info("Local BE file request for beRef='{}', download={}, basePath='{}'",
            beRef, download, beFileService.getBasePathDisplay());
        Optional<Path> fileOpt = beFileService.resolvePdf(beRef);
        if (fileOpt.isPresent()) {
            return serveLocalPdf(fileOpt.get(), downloadFilename, download);
        }

        log.info("Local BE file not found for beRef='{}'. Trying OneDrive folder lookup.", beRef);
        Optional<OneDriveBatchImportService.OneDriveFileDescriptor> cloudFile =
            oneDriveBatchImportService.findPdfByReference(beRef);
        if (cloudFile.isPresent()) {
            return serveOneDrivePdf(cloudFile.get(), downloadFilename, download);
        }

        log.warn("BE file not found for beRef='{}' in local storage or OneDrive.", beRef);
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
        log.info("BE metadata request for beRef='{}', basePath='{}'", beRef, beFileService.getBasePathDisplay());
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

    private ResponseEntity<Resource> serveOneDrivePdf(
        OneDriveBatchImportService.OneDriveFileDescriptor fileDescriptor,
        String downloadFilename,
        boolean download
    ) throws IOException {
        log.info("Streaming BE PDF from OneDrive: name='{}', itemId='{}'", fileDescriptor.name(), fileDescriptor.itemId());
        byte[] payload;
        try (InputStream inputStream = oneDriveBatchImportService.downloadFile(fileDescriptor)) {
            payload = inputStream.readAllBytes();
        }
        if (payload.length == 0) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_PDF)
            .contentLength(payload.length)
            .header(HttpHeaders.CONTENT_DISPOSITION, buildDisposition(downloadFilename, download).toString())
            .body(new ByteArrayResource(payload));
    }

    private ResponseEntity<Resource> serveRemotePdf(String beUrl, String downloadFilename, boolean download) {
        URI target = UriComponentsBuilder.fromUriString(beUrl.trim())
            .replaceQueryParam("download", download ? "1" : "0")
            .build(true)
            .toUri();
        log.info("Streaming remote BE URL='{}' with download filename='{}'", target, downloadFilename);

        byte[] payload = restClient.get()
            .uri(target)
            .retrieve()
            .body(byte[].class);

        if (payload == null || payload.length == 0) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_PDF)
            .contentLength(payload.length)
            .header(HttpHeaders.CONTENT_DISPOSITION, buildDisposition(downloadFilename, download).toString())
            .body(new ByteArrayResource(payload));
    }

    private BeFileMetadataResponse resolveRemotePdfMetadata(String beUrl, String downloadFilename) {
        URI target = UriComponentsBuilder.fromUriString(beUrl.trim())
            .replaceQueryParam("download", "1")
            .build(true)
            .toUri();
        log.info("Fetching remote BE metadata for URL='{}'", target);
        return buildMetadataResponse(downloadFilename, fetchRemoteContentLength(target), "REMOTE");
    }

    private Long fetchRemoteContentLength(URI target) {
        try {
            return restClient.head()
                .uri(target)
                .exchange((request, response) -> {
                    if (response.getStatusCode().is3xxRedirection()) {
                        URI redirectLocation = response.getHeaders().getLocation();
                        return redirectLocation == null ? null : fetchRemoteContentLength(redirectLocation);
                    }
                    if (!response.getStatusCode().is2xxSuccessful()) {
                        return null;
                    }
                    return toNullableLength(response.getHeaders().getContentLength());
                });
        } catch (Exception ex) {
            log.warn("Remote BE metadata lookup failed for URL='{}': {}", target, ex.getMessage());
            return null;
        }
    }

    private BeFileMetadataResponse buildMetadataResponse(String fileName, Long contentLength, String source) {
        return new BeFileMetadataResponse(fileName, contentLength, source);
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
}
