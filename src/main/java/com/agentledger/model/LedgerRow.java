package com.agentledger.model;

public record LedgerRow(
        long id, String time, String typeName, String accountName,
        long amountPya, long feePya, long commissionPya,
        Long reversesId, boolean reversed, String createdBy) {

    public boolean isReversal() { return reversesId != null; }
}