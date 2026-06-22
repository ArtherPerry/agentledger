package com.agentledger.repo;

import com.agentledger.db.Database;
import com.agentledger.model.FeeRule;

import java.sql.*;
import java.util.*;

public final class FeeRuleRepo {
    private FeeRuleRepo() {}

    public static List<FeeRule> listForBranch(int branchId) {
        List<FeeRule> out = new ArrayList<>();
        String sql = "SELECT id,type_name,platform,min_amount_pya,max_amount_pya,fee_pct,min_fee_pya,comm_pct,active " +
                "FROM fee_rules WHERE branch_id=? ORDER BY type_name, platform, min_amount_pya";
        try {
            Connection c = Database.get();
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setInt(1, branchId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        long max = rs.getLong("max_amount_pya");
                        Long maxBox = rs.wasNull() ? null : max;
                        out.add(new FeeRule(rs.getInt("id"), rs.getString("type_name"), rs.getString("platform"),
                                rs.getLong("min_amount_pya"), maxBox, rs.getDouble("fee_pct"),
                                rs.getLong("min_fee_pya"), rs.getDouble("comm_pct"),
                                rs.getInt("active") == 1));
                    }
                }
            }
        } catch (Exception e) { com.agentledger.utils.Log.error(e); }
        return out;
    }

    public static void insert(int branchId, FeeRule r) throws Exception {
        Connection c = Database.get();
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO fee_rules(branch_id,type_name,platform,min_amount_pya,max_amount_pya,fee_pct,min_fee_pya,comm_pct,active) " +
                        "VALUES (?,?,?,?,?,?,?,?,1)")) {
            ps.setInt(1, branchId);
            ps.setString(2, r.typeName());
            ps.setString(3, r.platform());
            ps.setLong(4, r.minAmountPya());
            if (r.maxAmountPya() == null) ps.setNull(5, Types.INTEGER); else ps.setLong(5, r.maxAmountPya());
            ps.setDouble(6, r.feePct());
            ps.setLong(7, r.minFeePya());
            ps.setDouble(8, r.commPct());
            ps.executeUpdate();
        }
    }

    public static void update(FeeRule r) throws Exception {
        Connection c = Database.get();
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE fee_rules SET type_name=?,platform=?,min_amount_pya=?,max_amount_pya=?,fee_pct=?,min_fee_pya=?,comm_pct=? WHERE id=?")) {
            ps.setString(1, r.typeName());
            ps.setString(2, r.platform());
            ps.setLong(3, r.minAmountPya());
            if (r.maxAmountPya() == null) ps.setNull(4, Types.INTEGER); else ps.setLong(4, r.maxAmountPya());
            ps.setDouble(5, r.feePct());
            ps.setLong(6, r.minFeePya());
            ps.setDouble(7, r.commPct());
            ps.setInt(8, r.id());
            ps.executeUpdate();
        }
    }

    public static void delete(int id) throws Exception {
        Connection c = Database.get();
        try (PreparedStatement ps = c.prepareStatement("DELETE FROM fee_rules WHERE id=?")) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }

    /** Active types (for a branch) that can bear a platform fee — i.e. have a digital leg.
     *  Returns canonical names (fee rules match on the canonical name). Includes custom types. */
    public static List<String> feeBearingTypes(int branchId) {
        List<String> out = new ArrayList<>();
        String sql = "SELECT name FROM txn_types WHERE branch_id=? AND active=1 " +
                "AND digital_effect IN ('in','out') " +
                "AND name NOT IN (" + com.agentledger.model.TxnType.internalSqlList() + ") " +
                "ORDER BY sort_order, id";
        try {
            Connection c = Database.get();
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setInt(1, branchId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) out.add(rs.getString(1));
                }
            }
        } catch (Exception e) { com.agentledger.utils.Log.error(e); }
        return out;
    }
}