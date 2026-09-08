package com.example;

import java.time.Instant;

/**
 * Legacy Audit Logger component exclusive to the baseline existing codebase.
 * Demonstrates preservation of existing files during merge operations.
 */
public class LegacyAuditLogger {
    private String logDestination;
    private boolean synchronous;

    public LegacyAuditLogger() {
        this.logDestination = "var/log/legacy_audit.log";
        this.synchronous = true;
    }

    public void logEvent(String eventType, String payload) {
        System.out.println("[" + Instant.now() + "] [LEGACY-AUDIT] " + eventType + ": " + payload);
    }

    public String getLogDestination() {
        return this.logDestination;
    }

    public boolean isSynchronous() {
        return this.synchronous;
    }
}
