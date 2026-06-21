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
        // All nine seeded types are built-in (display_name defaults to the canonical name;
        // owner can rename via Settings, but built-ins can never be deleted).
        String sql = "INSERT INTO txn_types(branch_id,name,display_name,cash_effect,digital_effect,active,sort_order,is_builtin) " +
                "VALUES (?,?,?,?,?,1,?,1)";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            int sort = 1;
            for (String[] t : types) {
                ps.setInt(1, branchId);
                ps.setString(2, t[0]);           // name (immutable key)
                ps.setString(3, t[0]);           // display_name = name initially
                ps.setString(4, t[1]);           // cash_effect
                ps.setString(5, t[2]);           // digital_effect
                ps.setInt(6, sort++);            // sort_order 1..9
                ps.executeUpdate();
            }
        }
    }
}