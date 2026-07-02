package com.agentledger.model;

public record FeeRule(
        int id,
        String typeName,
        String platform,
        long minAmountPya,
        Long maxAmountPya,
        double feePct,
        long minFeePya,
        double commPct,
        long minCommPya,
        boolean active
) {}