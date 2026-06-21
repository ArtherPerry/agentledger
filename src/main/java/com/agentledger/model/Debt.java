package com.agentledger.model;

public record Debt(
        long id, String kind, String counterpartyName, String counterpartyType,
        long originalPya, long paidPya, String status, String createdAt, Long sourceEntryId) {

    public long remainingPya() { return originalPya - paidPya; }
    public double progress() { return originalPya == 0 ? 1.0 : (double) paidPya / originalPya; }
}