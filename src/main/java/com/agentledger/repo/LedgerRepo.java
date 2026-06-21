package com.agentledger.repo;

import com.agentledger.db.Database;
import com.agentledger.model.LedgerRow;
import com.agentledger.model.TodayStats;

import java.sql.*;
import java.util.*;

public final class LedgerRepo {
    private LedgerRepo() {}

    /** Recent entries for a branch, newest first. */
    public static List<LedgerRow> recent(int branchId, int limit) {
        List<LedgerRow> out = new ArrayList<>();
        String sql =
                "SELECT e.id, e.created_at, t.name AS type_name, a.name AS acct_name, " +
                        "       e.amount_pya, e.fee_pya, e.commission_pya, e.reverses_id, " +
                        "       u.name AS by_name, " +
                        "       EXISTS(SELECT 1 FROM ledger_entries r WHERE r.reverses_id = e.id) AS is_reversed " +
                        "FROM ledger_entries e " +
                        "JOIN txn_types t ON t.id = e.type_id " +
                        "JOIN accounts  a ON a.id = e.account_id " +
                        "JOIN users     u ON u.id = e.created_by " +
                        "WHERE e.branch_id = ? " +
                        "ORDER BY e.id DESC LIMIT ?";
        try {
            Connection c = Database.get();
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setInt(1, branchId);
                ps.setInt(2, limit);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        long rev = rs.getLong("reverses_id");
                        Long reversesId = rs.wasNull() ? null : rev;
                        out.add(new LedgerRow(
                                rs.getLong("id"), rs.getString("created_at"),
                                rs.getString("type_name"), rs.getString("acct_name"),
                                rs.getLong("amount_pya"), rs.getLong("fee_pya"),
                                rs.getLong("commission_pya"), reversesId,
                                rs.getInt("is_reversed") == 1, rs.getString("by_name")));
                    }
                }
            }
        } catch (Exception e) { com.agentledger.utils.Log.error(e); }
        return out;
    }

    public static long branchCashPya(int branchId) {
        return sum("SELECT COALESCE(SUM(cash_delta_pya),0) FROM ledger_entries WHERE branch_id=?", branchId);
    }

    public static long accountDigitalPya(int accountId) {
        return sum("SELECT COALESCE(SUM(digital_delta_pya),0) FROM ledger_entries WHERE account_id=?", accountId);
    }

    private static long sum(String sql, int id) {
        try {
            Connection c = Database.get();
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setInt(1, id);
                try (ResultSet rs = ps.executeQuery()) { return rs.next() ? rs.getLong(1) : 0L; }
            }
        } catch (Exception e) { com.agentledger.utils.Log.error(e); return 0L; }
    }

    /** Today's activity for the branch (count of postings, net fees, net commission). */
    public static TodayStats todayStats(int branchId) {
        String sql =
                "SELECT COUNT(CASE WHEN reverses_id IS NULL AND (to_account_id IS NULL OR digital_delta_pya <= 0) THEN 1 END), " +
                        "       COALESCE(SUM(fee_pya),0), COALESCE(SUM(commission_pya),0) " +
                        "FROM ledger_entries " +
                        "WHERE branch_id = ? AND date(created_at) = date('now','localtime')";
        try {
            java.sql.Connection c = Database.get();
            try (java.sql.PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setInt(1, branchId);
                try (java.sql.ResultSet rs = ps.executeQuery()) {
                    if (rs.next())
                        return new com.agentledger.model.TodayStats(rs.getInt(1), rs.getLong(2), rs.getLong(3));
                }
            }
        } catch (Exception e) { com.agentledger.utils.Log.error(e); }
        return new com.agentledger.model.TodayStats(0, 0, 0);
    }

    /** Total digital balance across all the branch's wallets. */
    public static long branchDigitalTotalPya(int branchId) {
        return sum("SELECT COALESCE(SUM(digital_delta_pya),0) FROM ledger_entries WHERE branch_id=?", branchId);
    }
}