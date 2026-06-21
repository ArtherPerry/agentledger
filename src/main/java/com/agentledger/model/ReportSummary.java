package com.agentledger.model;

public record ReportSummary(int txnCount, long totalAmountPya, long totalFeePya, long totalCommissionPya) {}