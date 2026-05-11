package com.example.situation.service;

import com.azure.core.credential.AccessToken;
import com.azure.core.credential.TokenRequestContext;
import com.azure.identity.ClientSecretCredential;
import com.azure.identity.ClientSecretCredentialBuilder;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatusCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriUtils;

@Service
public class OneDriveBatchImportService {

    private static final String GRAPH_BASE_URL = "https://graph.microsoft.com/v1.0";
    private static final String GRAPH_SCOPE = "https://graph.microsoft.com/.default";

    private final RestClient restClient;
    private final String clientId;
    private final String clientSecret;
    private final String tenantId;
    private final String userEmail;
    private final String filePath;
    private final String folderPath;

    public OneDriveBatchImportService(
        @Value("${azure.client-id:}") String clientId,
        @Value("${azure.client-secret:}") String clientSecret,
        @Value("${azure.tenant-id:}") String tenantId,
        @Value("${onedrive.user-email:}") String userEmail,
        @Value("${onedrive.file-path:}") String filePath,
        @Value("${onedrive.folder-path:}") String folderPath
    ) {
        this.restClient = RestClient.builder().build();
        this.clientId = normalizeValue(clientId);
        this.clientSecret = normalizeValue(clientSecret);
        this.tenantId = normalizeValue(tenantId);
        this.userEmail = normalizeValue(userEmail);
        this.filePath = normalizeValue(filePath);
        this.folderPath = normalizeValue(folderPath);
    }

    public boolean isConfigured() {
        return StringUtils.hasText(clientId)
            && StringUtils.hasText(clientSecret)
            && StringUtils.hasText(tenantId)
            && StringUtils.hasText(userEmail)
            && (StringUtils.hasText(filePath) || StringUtils.hasText(folderPath));
    }

    public Optional<OneDriveFileDescriptor> findLatestImportFile(String filePrefix) {
        if (!isConfigured()) {
            return Optional.empty();
        }

        Optional<ConfiguredFileSelection> configuredFile = configuredFileSelection();
        if (configuredFile.isPresent()) {
            ConfiguredFileSelection selection = configuredFile.get();
            Optional<OneDriveFileDescriptor> exactFile = listFolderChildren(selection.folderPath()).stream()
                .filter(GraphDriveItem::isFile)
                .filter(item -> item.hasName(selection.filename()))
                .map(GraphDriveItem::toDescriptor)
                .findFirst();
            if (exactFile.isEmpty()) {
                throw new IllegalStateException("Configured OneDrive file not found: " + filePath);
            }
            return exactFile;
        }

        String normalizedPrefix = normalizeValue(filePrefix).toLowerCase(Locale.ROOT);
        return listFolderChildren(normalizeVirtualPath(folderPath)).stream()
            .filter(GraphDriveItem::isFile)
            .map(GraphDriveItem::toDescriptor)
            .filter(this::isExcelFile)
            .filter(file -> normalizedPrefix.isBlank()
                || file.name().toLowerCase(Locale.ROOT).startsWith(normalizedPrefix))
            .max(Comparator.comparing(
                OneDriveFileDescriptor::lastModifiedDateTime,
                Comparator.nullsLast(Comparator.naturalOrder())
            ));
    }

    public Optional<OneDriveFileDescriptor> findPdfByReference(String beValue) {
        String lookupFolder = resolveLookupFolder();
        if (!StringUtils.hasText(lookupFolder) || !StringUtils.hasText(beValue)) {
            return Optional.empty();
        }

        Set<String> candidates = BeFileNameResolver.buildCandidates(beValue);
        Optional<OneDriveFileDescriptor> directMatch = listFolderChildren(lookupFolder).stream()
            .filter(GraphDriveItem::isFile)
            .filter(item -> isPdfFile(item.name()))
            .filter(item -> matchesCandidate(item.name(), candidates))
            .map(GraphDriveItem::toDescriptor)
            .findFirst();
        if (directMatch.isPresent()) {
            return directMatch;
        }

        String canonicalInput = BeFileNameResolver.canonicalBeToken(beValue);
        return listFolderChildren(lookupFolder).stream()
            .filter(GraphDriveItem::isFile)
            .filter(item -> isPdfFile(item.name()))
            .map(GraphDriveItem::toDescriptor)
            .filter(file -> BeFileNameResolver.canonicalBeToken(file.name()).equals(canonicalInput))
            .findFirst();
    }

    public InputStream downloadFile(OneDriveFileDescriptor fileDescriptor) {
        byte[] payload = restClient.get()
            .uri(buildContentUri(fileDescriptor.itemId()))
            .headers(headers -> headers.setBearerAuth(accessToken()))
            .exchange((request, response) -> readContentPayload(response.getStatusCode(), response.getHeaders().getLocation(), response.getBody()));

        if (payload == null || payload.length == 0) {
            throw new IllegalStateException("OneDrive returned an empty payload for file: " + fileDescriptor.name());
        }

        return new ByteArrayInputStream(payload);
    }

    private byte[] readContentPayload(HttpStatusCode statusCode, URI redirectLocation, InputStream responseBody) {
        if (statusCode.is3xxRedirection()) {
            if (redirectLocation == null) {
                throw new IllegalStateException("OneDrive content request redirected without a download URL.");
            }
            return downloadFromRedirect(redirectLocation);
        }
        if (!statusCode.is2xxSuccessful()) {
            throw new IllegalStateException("OneDrive content request failed with status: " + statusCode.value());
        }
        try {
            return StreamUtils.copyToByteArray(responseBody);
        } catch (Exception ex) {
            throw new IllegalStateException("Could not read OneDrive file response body.", ex);
        }
    }

    private byte[] downloadFromRedirect(URI redirectLocation) {
        byte[] payload = restClient.get()
            .uri(redirectLocation)
            .retrieve()
            .body(byte[].class);

        if (payload == null || payload.length == 0) {
            throw new IllegalStateException("OneDrive redirected to an empty download payload.");
        }
        return payload;
    }

    private List<GraphDriveItem> listFolderChildren(String normalizedFolderPath) {
        GraphDriveChildrenResponse response = restClient.get()
            .uri(buildChildrenUri(normalizedFolderPath))
            .headers(headers -> headers.setBearerAuth(accessToken()))
            .retrieve()
            .body(GraphDriveChildrenResponse.class);

        if (response == null || response.value() == null) {
            return List.of();
        }
        return response.value();
    }

    private URI buildChildrenUri(String normalizedFolderPath) {
        String encodedUser = UriUtils.encodePathSegment(userEmail, StandardCharsets.UTF_8);
        StringBuilder uri = new StringBuilder(GRAPH_BASE_URL)
            .append("/users/")
            .append(encodedUser)
            .append("/drive");

        if (normalizedFolderPath.isBlank()) {
            uri.append("/root/children");
        } else {
            uri.append("/root:/")
                .append(encodeVirtualPath(normalizedFolderPath))
                .append(":/children");
        }

        uri.append("?$select=id,name,eTag,lastModifiedDateTime,file");
        return URI.create(uri.toString());
    }

    private URI buildContentUri(String itemId) {
        String encodedUser = UriUtils.encodePathSegment(userEmail, StandardCharsets.UTF_8);
        String encodedItemId = UriUtils.encodePathSegment(itemId, StandardCharsets.UTF_8);
        return URI.create(GRAPH_BASE_URL
            + "/users/" + encodedUser
            + "/drive/items/" + encodedItemId
            + "/content");
    }

    private String accessToken() {
        ClientSecretCredential credential = new ClientSecretCredentialBuilder()
            .clientId(clientId)
            .clientSecret(clientSecret)
            .tenantId(tenantId)
            .build();

        AccessToken token = credential.getToken(new TokenRequestContext().addScopes(GRAPH_SCOPE)).block();
        if (token == null || !StringUtils.hasText(token.getToken())) {
            throw new IllegalStateException("Could not acquire a Microsoft Graph access token.");
        }
        return token.getToken();
    }

    private Optional<ConfiguredFileSelection> configuredFileSelection() {
        String normalizedFilePath = normalizeVirtualPath(filePath);
        if (normalizedFilePath.isBlank()) {
            return Optional.empty();
        }

        int separatorIndex = normalizedFilePath.lastIndexOf('/');
        String resolvedFolderPath = separatorIndex < 0 ? "" : normalizedFilePath.substring(0, separatorIndex);
        String resolvedFilename = separatorIndex < 0 ? normalizedFilePath : normalizedFilePath.substring(separatorIndex + 1);
        if (!StringUtils.hasText(resolvedFilename)) {
            return Optional.empty();
        }
        return Optional.of(new ConfiguredFileSelection(resolvedFolderPath, resolvedFilename));
    }

    private String resolveLookupFolder() {
        Optional<ConfiguredFileSelection> configuredFile = configuredFileSelection();
        if (configuredFile.isPresent()) {
            return configuredFile.get().folderPath();
        }
        return normalizeVirtualPath(folderPath);
    }

    private boolean isExcelFile(OneDriveFileDescriptor fileDescriptor) {
        if (!StringUtils.hasText(fileDescriptor.name())) {
            return false;
        }
        String lowerName = fileDescriptor.name().toLowerCase(Locale.ROOT);
        return lowerName.endsWith(".xlsx") || lowerName.endsWith(".xls");
    }

    private boolean isPdfFile(String filename) {
        return StringUtils.hasText(filename) && filename.toLowerCase(Locale.ROOT).endsWith(".pdf");
    }

    private boolean matchesCandidate(String filename, Set<String> candidates) {
        if (!StringUtils.hasText(filename)) {
            return false;
        }
        int idx = filename.lastIndexOf('.');
        String basename = idx > 0 ? filename.substring(0, idx) : filename;
        return candidates.stream().anyMatch(candidate -> candidate.equalsIgnoreCase(basename));
    }

    private static String encodeVirtualPath(String normalizedPath) {
        return Arrays.stream(normalizedPath.split("/"))
            .filter(StringUtils::hasText)
            .map(segment -> UriUtils.encodePathSegment(segment, StandardCharsets.UTF_8))
            .collect(Collectors.joining("/"));
    }

    private static String normalizeVirtualPath(String rawPath) {
        String normalized = normalizeValue(rawPath).replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private static String normalizeValue(String value) {
        return value == null ? "" : value.trim();
    }

    private record ConfiguredFileSelection(String folderPath, String filename) {
    }

    public record OneDriveFileDescriptor(
        String itemId,
        String name,
        String eTag,
        OffsetDateTime lastModifiedDateTime
    ) {
        public String importSignature() {
            String modified = lastModifiedDateTime == null ? "" : lastModifiedDateTime.toString();
            return itemId + "|" + eTag + "|" + modified;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GraphDriveChildrenResponse(List<GraphDriveItem> value) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GraphDriveItem(
        String id,
        String name,
        String eTag,
        OffsetDateTime lastModifiedDateTime,
        GraphFileFacet file
    ) {
        private boolean isFile() {
            return file != null;
        }

        private boolean hasName(String expectedName) {
            return name != null && name.equalsIgnoreCase(expectedName);
        }

        private OneDriveFileDescriptor toDescriptor() {
            return new OneDriveFileDescriptor(id, name, eTag, lastModifiedDateTime);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GraphFileFacet(String mimeType) {
    }
}
