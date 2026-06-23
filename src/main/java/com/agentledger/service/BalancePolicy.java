package com.agentledger.service;

import com.agentledger.repo.SettingsRepo;

/** How the app reacts when a transaction would drive a balance negative. */
public enum BalancePolicy {
    ALLOW, WARN, BLOCK;

    /** Current policy for a branch. Defaults to WARN (owner-overridable in Settings). */
    public static BalancePolicy of(int branchId) {
        String v = SettingsRepo.get(branchId, SettingsRepo.KEY_BALANCE_POLICY, "warn");
        try { return BalancePolicy.valueOf(v.trim().toUpperCase()); }
        catch (Exception e) { return WARN; }
    }
}