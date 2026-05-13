package com.example.situation.dto;

public record BeFileMetadataResponse(
    String fileName,
    Long contentLength,
    String source
) {
}
