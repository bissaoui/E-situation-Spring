package com.example.situation.service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class BeFileDownloadProgressService {

    private static final long RETAIN_COMPLETED_MILLIS = 5 * 60 * 1000L;

    private final Map<String, ProgressState> downloads = new ConcurrentHashMap<>();

    public void start(String downloadId, String actor, Long totalBytes) {
        if (!isValidDownloadId(downloadId)) {
            return;
        }
        cleanup();
        downloads.put(downloadId, new ProgressState(actor, totalBytes));
    }

    public void update(String downloadId, long bytesWritten) {
        ProgressState state = downloads.get(downloadId);
        if (state != null) {
            state.bytesWritten = Math.max(0, bytesWritten);
            state.updatedAtMs = System.currentTimeMillis();
        }
    }

    public void complete(String downloadId) {
        ProgressState state = downloads.get(downloadId);
        if (state != null) {
            if (state.totalBytes != null && state.totalBytes > 0) {
                state.bytesWritten = Math.max(state.bytesWritten, state.totalBytes);
            }
            state.done = true;
            state.updatedAtMs = System.currentTimeMillis();
        }
    }

    public void fail(String downloadId, String message) {
        ProgressState state = downloads.get(downloadId);
        if (state != null) {
            state.error = message == null || message.isBlank() ? "Download failed" : message;
            state.done = true;
            state.updatedAtMs = System.currentTimeMillis();
        }
    }

    public ProgressSnapshot get(String downloadId, String actor) {
        ProgressState state = downloads.get(downloadId);
        if (state == null || !state.actor.equals(actor)) {
            return null;
        }
        return new ProgressSnapshot(
            state.bytesWritten,
            state.totalBytes,
            state.startedAtMs,
            state.updatedAtMs,
            state.done,
            state.error
        );
    }

    public static boolean isValidDownloadId(String downloadId) {
        return downloadId != null && downloadId.matches("[a-zA-Z0-9_-]{12,80}");
    }

    private void cleanup() {
        long cutoff = System.currentTimeMillis() - RETAIN_COMPLETED_MILLIS;
        downloads.entrySet().removeIf(entry -> entry.getValue().done && entry.getValue().updatedAtMs < cutoff);
    }

    private static class ProgressState {
        private final String actor;
        private final Long totalBytes;
        private final long startedAtMs;
        private volatile long bytesWritten;
        private volatile long updatedAtMs;
        private volatile boolean done;
        private volatile String error;

        private ProgressState(String actor, Long totalBytes) {
            this.actor = actor == null ? "-" : actor;
            this.totalBytes = totalBytes != null && totalBytes > 0 ? totalBytes : null;
            this.startedAtMs = Instant.now().toEpochMilli();
            this.updatedAtMs = this.startedAtMs;
        }
    }

    public record ProgressSnapshot(
        long bytesWritten,
        Long totalBytes,
        long startedAtMs,
        long updatedAtMs,
        boolean done,
        String error
    ) {
    }
}
