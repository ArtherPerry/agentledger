package com.agentledger.repo;

import com.agentledger.db.Database;
import com.agentledger.model.TxnType;

import java.sql.*;
import java.util.*;

public final class TxnTypeRepo {
    private TxnTypeRepo() {}

    private static final String EXCLUDE_INTERNAL =
            " AND name NOT IN (" + TxnType.internalSqlList() + ") ";

    private static final String COLS = "id,name,display_name,cash_effect,digital_effect,active,is_builtin";

    private static TxnType map(ResultSet rs) throws SQLException {
        return new TxnType(rs.getInt(1), rs.getString(2), rs.getString(3),
                rs.getString(4), rs.getString(5),
                rs.getInt(6) == 1, rs.getInt(7) == 1);
    }

    /** Active, user-facing types for the transaction dropdown (internal excluded). */
    public static List<TxnType> listForBranch(int branchId) {
        List<TxnType> out = new ArrayList<>();
        String sql = "SELECT " + COLS + " FROM txn_types " +
                "WHERE branch_id=? AND active=1" + EXCLUDE_INTERNAL + "ORDER BY sort_order, id";
        try {
            Connection c = Database.get();
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setInt(1, branchId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) out.add(map(rs));
                }
            }
        } catch (Exception e) { com.agentledger.utils.Log.error(e); }
        return out;
    }

    /** ALL user-facing types incl. inactive, for the management screen (internal excluded). */
    public static List<TxnType> listAllForBranch(int branchId) {
        List<TxnType> out = new ArrayList<>();
        String sql = "SELECT " + COLS + " FROM txn_types " +
                "WHERE branch_id=?" + EXCLUDE_INTERNAL + "ORDER BY sort_order, id";
        try {
            Connection c = Database.get();
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setInt(1, branchId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) out.add(map(rs));
                }
            }
        } catch (Exception e) { com.agentledger.utils.Log.error(e); }
        return out;
    }

    public static TxnType byName(int branchId, String name) {
        String sql = "SELECT " + COLS + " FROM txn_types WHERE branch_id=? AND name=? AND active=1";
        try {
            Connection c = Database.get();
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setInt(1, branchId);
                ps.setString(2, name);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return map(rs);
                }
            }
        } catch (Exception e) { com.agentledger.utils.Log.error(e); }
        return null;
    }

    public static TxnType byId(int id) {
        String sql = "SELECT " + COLS + " FROM txn_types WHERE id=?";
        try {
            Connection c = Database.get();
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setInt(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return map(rs);
                }
            }
        } catch (Exception e) { com.agentledger.utils.Log.error(e); }
        return null;
    }

    public static boolean isBuiltin(int id) {
        try {
            Connection c = Database.get();
            try (PreparedStatement ps = c.prepareStatement("SELECT is_builtin FROM txn_types WHERE id=?")) {
                ps.setInt(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() && rs.getInt(1) == 1;
                }
            }
        } catch (Exception e) { com.agentledger.utils.Log.error(e); }
        return false;
    }

    /** Insert a CUSTOM single-leg type. Generates a stable, unique internal name. Returns new id. */
    public static int insertCustom(int branchId, String displayName, String cashEffect, String digitalEffect) throws Exception {
        Connection c = Database.get();
        String name = "CUSTOM_" + System.currentTimeMillis();
        int nextSort;
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT COALESCE(MAX(sort_order),0)+1 FROM txn_types WHERE branch_id=?")) {
            ps.setInt(1, branchId);
            try (ResultSet rs = ps.executeQuery()) { rs.next(); nextSort = rs.getInt(1); }
        }
        String sql = "INSERT INTO txn_types(branch_id,name,display_name,cash_effect,digital_effect,active,sort_order,is_builtin) " +
                "VALUES (?,?,?,?,?,1,?,0)";
        try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, branchId);
            ps.setString(2, name);
            ps.setString(3, displayName);
            ps.setString(4, cashEffect);
            ps.setString(5, digitalEffect);
            ps.setInt(6, nextSort);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) { keys.next(); return keys.getInt(1); }
        }
    }

    public static void updateDisplayName(int id, String displayName) throws Exception {
        Connection c = Database.get();
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE txn_types SET display_name=? WHERE id=?")) {
            ps.setString(1, displayName);
            ps.setInt(2, id);
            ps.executeUpdate();
        }
    }

    public static void setActive(int id, boolean active) throws Exception {
        Connection c = Database.get();
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE txn_types SET active=? WHERE id=?")) {
            ps.setInt(1, active ? 1 : 0);
            ps.setInt(2, id);
            ps.executeUpdate();
        }
    }

    public static void setSortOrder(int id, int sortOrder) throws Exception {
        Connection c = Database.get();
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE txn_types SET sort_order=? WHERE id=?")) {
            ps.setInt(1, sortOrder);
            ps.setInt(2, id);
            ps.executeUpdate();
        }
    }

    /** True if any ledger entry references this type (so it must not be hard-deleted). */
    public static boolean hasLedgerEntries(int id) {
        try {
            Connection c = Database.get();
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT 1 FROM ledger_entries WHERE type_id=? LIMIT 1")) {
                ps.setInt(1, id);
                try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
            }
        } catch (Exception e) { com.agentledger.utils.Log.error(e); }
        return true; // fail safe: if unsure, treat as referenced (don't delete)
    }

    /** Delete a CUSTOM type. Refuses builtins and types with history (deactivate those instead). */
    public static void deleteCustom(int id) throws Exception {
        if (isBuiltin(id)) throw new IllegalStateException("builtin types cannot be deleted");
        if (hasLedgerEntries(id)) throw new IllegalStateException("type has history; deactivate instead");
        Connection c = Database.get();
        try (PreparedStatement ps = c.prepareStatement("DELETE FROM txn_types WHERE id=? AND is_builtin=0")) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }
    public static boolean isActive(int id) {
        try {
            Connection c = Database.get();
            try (PreparedStatement ps = c.prepareStatement("SELECT active FROM txn_types WHERE id=?")) {
                ps.setInt(1, id);
                try (ResultSet rs = ps.executeQuery()) { return rs.next() && rs.getInt(1) == 1; }
            }
        } catch (Exception e) { com.agentledger.utils.Log.error(e); }
        return false;
    }

    public static int sortOrderOf(int id) {
        try {
            Connection c = Database.get();
            try (PreparedStatement ps = c.prepareStatement("SELECT sort_order FROM txn_types WHERE id=?")) {
                ps.setInt(1, id);
                try (ResultSet rs = ps.executeQuery()) { return rs.next() ? rs.getInt(1) : 0; }
            }
        } catch (Exception e) { com.agentledger.utils.Log.error(e); }
        return 0;
    }
    /** Map a canonical type name to its current display name (for showing in other screens). */
    public static String displayNameFor(int branchId, String canonicalName) {
        try {
            Connection c = Database.get();
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT display_name FROM txn_types WHERE branch_id=? AND name=?")) {
                ps.setInt(1, branchId);
                ps.setString(2, canonicalName);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) { String d = rs.getString(1); return d != null ? d : canonicalName; }
                }
            }
        } catch (Exception e) { com.agentledger.utils.Log.error(e); }
        return canonicalName;
    }
}