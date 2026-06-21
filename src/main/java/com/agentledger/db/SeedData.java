package com.agentledger.db;

import com.agentledger.model.TxnType;

import java.sql.Connection;
import java.sql.PreparedStatement;

/** Centralized seed inserts shared by Database (initial) and BranchService (new branch). */
public final class SeedData {
    private SeedData() {}

    /** Insert the 9 standard transaction types for a branch, using canonical names. */
    public static void insertTxnTypes(Connection c, int branchId) throws Exception {
        String[][] types = {
                {TxnType.PASSWORD_WITHDRAW, "out", "in"},
                {TxnType.PASSWORD_TRANSFER, "in", "out"},
                {TxnType.WALLET_TO_WALLET,  "none", "out"},
                {TxnType.ACCOUNT_WITHDRAW,  "out", "in"},
                {TxnType.CASH_TO_ACCOUNT,   "in", "out"},
                {TxnType.TOPUP_DIGITAL,     "none", "in"},
                {TxnType.TOPUP_CASH,        "in", "none"},
                {TxnType.REPAY_RECEIVABLE,  "in", "none"},
                {TxnType.REPAY_PAYABLE,     "out", "none"},
        };
        String sql = "INSERT INTO txn_types(branch_id,name,cash_effect,digital_effect) VALUES (?,?,?,?)";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (String[] t : types) {
                ps.setInt(1, branchId);
                ps.setString(2, t[0]);
                ps.setString(3, t[1]);
                ps.setString(4, t[2]);
                ps.executeUpdate();
            }
        }
    }
}