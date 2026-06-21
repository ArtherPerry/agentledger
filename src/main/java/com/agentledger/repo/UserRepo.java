package com.agentledger.repo;

import com.agentledger.db.Database;
import com.agentledger.model.UserRow;

import java.sql.*;
import java.time.LocalDate;
import java.util.*;

public final class UserRepo {
    private UserRepo() {}

    public static List<UserRow> listForBranch(int branchId) {
        List<UserRow> out = new ArrayList<>();
        String sql = "SELECT id,name,username,role,active FROM users WHERE branch_id=? ORDER BY role,name";
        try {
            Connection c = Database.get();
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setInt(1, branchId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next())
                        out.add(new UserRow(rs.getInt(1), rs.getString(2), rs.getString(3),
                                rs.getString(4), rs.getInt(5) == 1));
                }
            }
        } catch (Exception e) { com.agentledger.utils.Log.error(e); }
        return out;
    }

    public static boolean usernameExists(int branchId, String username, int excludeId) {
        String sql = "SELECT 1 FROM users WHERE branch_id=? AND username=? AND id<>?";
        try {
            Connection c = Database.get();
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setInt(1, branchId); ps.setString(2, username); ps.setInt(3, excludeId);
                try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
            }
        } catch (Exception e) { com.agentledger.utils.Log.error(e); return false; }
    }

    public static void insert(int branchId, String name, String username, String pwdHash, String role) throws Exception {
        Connection c = Database.get();
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO users(branch_id,name,username,pwd_hash,role,created_at) VALUES (?,?,?,?,?,?)")) {
            ps.setInt(1, branchId); ps.setString(2, name); ps.setString(3, username);
            ps.setString(4, pwdHash); ps.setString(5, role); ps.setString(6, LocalDate.now().toString());
            ps.executeUpdate();
        }
    }

    /** Update name/role/active. If newPwdHash != null, also change the password. */
    public static void update(int id, String name, String role, boolean active, String newPwdHash) throws Exception {
        Connection c = Database.get();
        String sql = newPwdHash == null
                ? "UPDATE users SET name=?,role=?,active=? WHERE id=?"
                : "UPDATE users SET name=?,role=?,active=?,pwd_hash=? WHERE id=?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, name); ps.setString(2, role); ps.setInt(3, active ? 1 : 0);
            if (newPwdHash == null) {
                ps.setInt(4, id);
            } else {
                ps.setString(4, newPwdHash); ps.setInt(5, id);
            }
            ps.executeUpdate();
        }
    }

    public static int countOwners(int branchId) {
        String sql = "SELECT COUNT(*) FROM users WHERE branch_id=? AND role='owner' AND active=1";
        try {
            Connection c = Database.get();
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setInt(1, branchId);
                try (ResultSet rs = ps.executeQuery()) { return rs.next() ? rs.getInt(1) : 0; }
            }
        } catch (Exception e) { com.agentledger.utils.Log.error(e); return 0; }
    }
}