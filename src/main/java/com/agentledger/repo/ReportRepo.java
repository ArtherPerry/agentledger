package com.agentledger.repo;

import com.agentledger.db.Database;
import com.agentledger.model.PlatformRow;
import com.agentledger.model.ReportSummary;
import com.agentledger.model.TxnType;

import java.sql.*;
import java.util.*;

public final class ReportRepo {
    private ReportRepo() {}

    /** Inclusive date range, e.g. 2026-06-01 .. 2026-06-13. Excludes internal top-up/repayment types. */
    private static final String EXCLUDE =
            " AND t.name NOT IN (" + TxnType.internalSqlList() + ") ";

    public static ReportSummary summary(int branchId, String from, String to) {
        String sql = "SELECT COUNT(CASE WHEN e.reverses_id IS NULL AND (e.to_account_id IS NULL OR e.digital_delta_pya <= 0) THEN 1 END), " +
                "COALESCE(SUM(e.amount_pya),0), COALESCE(SUM(e.fee_pya),0), COALESCE(SUM(e.commission_pya),0) " +
                "FROM ledger_entries e JOIN txn_types t ON t.id=e.type_id " +
                "WHERE e.branch_id=? AND date(e.created_at) BETWEEN ? AND ?" + EXCLUDE;
        try {
            Connection c = Database.get();
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setInt(1, branchId); ps.setString(2, from); ps.setString(3, to);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next())
                        return new ReportSummary(rs.getInt(1), rs.getLong(2), rs.getLong(3), rs.getLong(4));
                }
            }
        } catch (Exception e) { com.agentledger.utils.Log.error(e); }
        return new ReportSummary(0, 0, 0, 0);
    }

    public static List<PlatformRow> byPlatform(int branchId, String from, String to) {
        List<PlatformRow> out = new ArrayList<>();
        String sql = "SELECT COALESCE(a.platform,'(ငွေသား)') AS platform, " +
                "COUNT(CASE WHEN e.reverses_id IS NULL AND (e.to_account_id IS NULL OR e.digital_delta_pya <= 0) THEN 1 END), " +
                "COALESCE(SUM(e.amount_pya),0), COALESCE(SUM(e.commission_pya),0) " +
                "FROM ledger_entries e JOIN txn_types t ON t.id=e.type_id " +
                "LEFT JOIN accounts a ON a.id=e.account_id " +
                "WHERE e.branch_id=? AND date(e.created_at) BETWEEN ? AND ?" + EXCLUDE +
                "GROUP BY COALESCE(a.platform,'(ငွေသား)') ORDER BY 3 DESC";
        try {
            Connection c = Database.get();
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setInt(1, branchId); ps.setString(2, from); ps.setString(3, to);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next())
                        out.add(new PlatformRow(rs.getString(1), rs.getInt(2), rs.getLong(3), rs.getLong(4)));
                }
            }
        } catch (Exception e) { com.agentledger.utils.Log.error(e); }
        return out;
    }

    /** Outstanding debts snapshot for the PayRec report type (open only). */
    public static List<com.agentledger.model.Debt> outstandingDebts(int branchId) {
        List<com.agentledger.model.Debt> out = new ArrayList<>();
        out.addAll(DebtRepo.list(branchId, "receivable", false));
        out.addAll(DebtRepo.list(branchId, "payable", false));
        return out;
    }
}