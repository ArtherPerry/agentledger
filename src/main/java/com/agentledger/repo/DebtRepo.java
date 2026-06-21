package com.agentledger.repo;

import com.agentledger.db.Database;
import com.agentledger.model.Debt;

import java.sql.*;
import java.util.*;

public final class DebtRepo {
    private DebtRepo() {}

    /** kind = 'receivable' or 'payable'; settledFilter: null=open only, "settled"=settled only. */
    public static List<Debt> list(int branchId, String kind, boolean settled) {
        List<Debt> out = new ArrayList<>();
        String sql =
                "SELECT d.id, d.kind, cp.name, cp.cp_type, d.original_pya, " +
                        "       COALESCE((SELECT SUM(p.amount_pya) FROM debt_payments p WHERE p.debt_id=d.id),0) AS paid, " +
                        "       d.status, d.created_at, d.source_entry_id " +
                        "FROM debts d JOIN counterparties cp ON cp.id=d.counterparty_id " +
                        "WHERE d.branch_id=? AND d.kind=? AND d.status=? " +
                        "ORDER BY d.created_at DESC";
        try {
            Connection c = Database.get();
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setInt(1, branchId);
                ps.setString(2, kind);
                ps.setString(3, settled ? "settled" : "open");
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        long src = rs.getLong("source_entry_id");
                        out.add(new Debt(rs.getLong("id"), rs.getString("kind"),
                                rs.getString("name"), rs.getString("cp_type"),
                                rs.getLong("original_pya"), rs.getLong("paid"),
                                rs.getString("status"), rs.getString("created_at"),
                                rs.wasNull() ? null : src));
                    }
                }
            }
        } catch (Exception e) { com.agentledger.utils.Log.error(e); }
        return out;
    }

    public static long totalRemaining(int branchId, String kind) {
        String sql = "SELECT COALESCE(SUM(d.original_pya - " +
                "COALESCE((SELECT SUM(p.amount_pya) FROM debt_payments p WHERE p.debt_id=d.id),0)),0) " +
                "FROM debts d WHERE d.branch_id=? AND d.kind=? AND d.status='open'";
        try {
            Connection c = Database.get();
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setInt(1, branchId); ps.setString(2, kind);
                try (ResultSet rs = ps.executeQuery()) { return rs.next() ? rs.getLong(1) : 0L; }
            }
        } catch (Exception e) { com.agentledger.utils.Log.error(e); return 0L; }
    }
}