package com.agentledger.repo;

import com.agentledger.db.Database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

/** Branch-scoped key/value settings. First user of the (previously unused) settings table. */
public final class SettingsRepo {
    private SettingsRepo() {}

    public static final String KEY_BALANCE_POLICY = "balance_policy";

    /** Read a setting; returns def if absent/null/error. */
    public static String get(int branchId, String key, String def) {
        String sql = "SELECT value FROM settings WHERE branch_id=? AND key=?";
        try {
            Connection c = Database.get();
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setInt(1, branchId);
                ps.setString(2, key);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        String v = rs.getString(1);
                        return v != null ? v : def;
                    }
                }
            }
        } catch (Exception e) { com.agentledger.utils.Log.error(e); }
        return def;
    }

    /** Upsert a setting (portable: UPDATE, else INSERT — no reliance on ON CONFLICT). */
    public static void set(int branchId, String key, String value) throws Exception {
        Connection c = Database.get();
        try (PreparedStatement up = c.prepareStatement(
                "UPDATE settings SET value=? WHERE branch_id=? AND key=?")) {
            up.setString(1, value);
            up.setInt(2, branchId);
            up.setString(3, key);
            if (up.executeUpdate() > 0) return;
        }
        try (PreparedStatement in = c.prepareStatement(
                "INSERT INTO settings(branch_id,key,value) VALUES(?,?,?)")) {
            in.setInt(1, branchId);
            in.setString(2, key);
            in.setString(3, value);
            in.executeUpdate();
        }
    }
}