package com.agentledger.model;

public record BackupRow(String ts, String location, long sizeBytes, boolean verified, String byName) {
    public String sizeKb() { return String.format("%.1f KB", sizeBytes / 1024.0); }
}