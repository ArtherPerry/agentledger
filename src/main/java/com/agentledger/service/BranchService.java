package com.agentledger.service;

import com.agentledger.i18n.I18n;

import com.agentledger.db.Database;
import com.agentledger.db.SeedData;
import com.agentledger.model.Branch;

import java.sql.*;
import java.util.*;

public final class BranchService {

    private BranchService() {}

    public static List<Branch> listActive() {
        List<Branch> out = new ArrayList<>();
        try {
            Connection c = Database.get();
            try (Statement s = c.createStatement();
                 ResultSet rs = s.executeQuery("SELECT id, name FROM branches WHERE active = 1 ORDER BY id")) {
                while (rs.next()) out.add(new Branch(rs.getInt(1), rs.getString(2)));
            }
        } catch (Exception e) {
            com.agentledger.utils.Log.error(e);
        }
        return out;
    }

    public static void create(String branchName, String ownerName, String ownerUsername, String ownerPassword) throws Exception {
        if (!Permissions.canManageBranches()) throw new IllegalStateException(I18n.t("error.noPermission"));
        if (branchName == null || branchName.isBlank()) throw new IllegalArgumentException(I18n.t("error.branchNameRequired"));
        if (ownerName == null || ownerName.isBlank()) throw new IllegalArgumentException(I18n.t("error.ownerNameRequired"));
        if (ownerUsername == null || ownerUsername.isBlank()) throw new IllegalArgumentException(I18n.t("error.usernameRequired"));
        if (ownerPassword == null || ownerPassword.length() < 4) throw new IllegalArgumentException(I18n.t("error.passwordMin"));

        Connection c = Database.get();
        boolean oldAuto = c.getAutoCommit();
        c.setAutoCommit(false);
        try {
            String today = java.time.LocalDate.now().toString();
            int branchId;
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO branches(name,created_at) VALUES (?,?)",
                    Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, branchName.trim()); ps.setString(2, today);
                ps.executeUpdate();
                try (ResultSet k = ps.getGeneratedKeys()) { k.next(); branchId = k.getInt(1); }
            }
            String hash = at.favre.lib.crypto.bcrypt.BCrypt.withDefaults()
                    .hashToString(12, ownerPassword.toCharArray());
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO users(branch_id,name,username,pwd_hash,role,created_at) VALUES (?,?,?,?, 'owner', ?)")) {
                ps.setInt(1, branchId); ps.setString(2, ownerName.trim());
                ps.setString(3, ownerUsername.trim()); ps.setString(4, hash); ps.setString(5, today);
                ps.executeUpdate();
            }
            // seed default cash account + the standard transaction types so the branch is usable immediately
            try (Statement s = c.createStatement()) {
                s.executeUpdate("INSERT INTO accounts(branch_id,name,platform,acct_type,created_at) VALUES (" + branchId + ",'ငွေသား',NULL,'cash','" + today + "')");
            }
            SeedData.insertTxnTypes(c, branchId);

            ActivityLog.record(c, Session.branchId(), Session.user().id(),
                    "BRANCH_CREATE", branchName + " / owner " + ownerUsername);
            c.commit();
        } catch (Exception e) {
            c.rollback();
            throw e;
        } finally {
            c.setAutoCommit(oldAuto);
        }
    }

    public static void rename(int branchId, String newName) throws Exception {
        if (!Permissions.canManageBranches()) throw new IllegalStateException(I18n.t("error.noPermission"));
        if (newName == null || newName.isBlank()) throw new IllegalArgumentException(I18n.t("error.nameRequired"));
        Connection c = Database.get();
        try (PreparedStatement ps = c.prepareStatement("UPDATE branches SET name=? WHERE id=?")) {
            ps.setString(1, newName.trim()); ps.setInt(2, branchId); ps.executeUpdate();
        }
        ActivityLog.record(c, Session.branchId(), Session.user().id(),
                "BRANCH_RENAME", branchId + " -> " + newName);
    }

    /**
     * First-run only: create the very first branch + owner with NO permission check,
     * allowed solely when the database has no users yet. After that, normal create() applies.
     */
    public static void createFirstBranch(String branchName, String ownerName,
                                         String ownerUsername, String ownerPassword) throws Exception {
        if (branchName == null || branchName.isBlank()) throw new IllegalArgumentException(I18n.t("error.branchNameRequired"));
        if (ownerName == null || ownerName.isBlank()) throw new IllegalArgumentException(I18n.t("error.ownerNameRequired"));
        if (ownerUsername == null || ownerUsername.isBlank()) throw new IllegalArgumentException(I18n.t("error.usernameRequired"));
        if (ownerPassword == null || ownerPassword.length() < 4) throw new IllegalArgumentException(I18n.t("error.passwordMin"));

        Connection c = Database.get();

        // guard: only allowed when there are no users at all
        try (Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM users")) {
            rs.next();
            if (rs.getInt(1) > 0) throw new IllegalStateException("Setup already completed.");
        }

        boolean oldAuto = c.getAutoCommit();
        c.setAutoCommit(false);
        try {
            String today = java.time.LocalDate.now().toString();
            int branchId;
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO branches(name,created_at) VALUES (?,?)",
                    Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, branchName.trim()); ps.setString(2, today);
                ps.executeUpdate();
                try (ResultSet k = ps.getGeneratedKeys()) { k.next(); branchId = k.getInt(1); }
            }
            String hash = at.favre.lib.crypto.bcrypt.BCrypt.withDefaults()
                    .hashToString(12, ownerPassword.toCharArray());
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO users(branch_id,name,username,pwd_hash,role,created_at) VALUES (?,?,?,?, 'owner', ?)")) {
                ps.setInt(1, branchId); ps.setString(2, ownerName.trim());
                ps.setString(3, ownerUsername.trim()); ps.setString(4, hash); ps.setString(5, today);
                ps.executeUpdate();
            }
            try (Statement s = c.createStatement()) {
                s.executeUpdate("INSERT INTO accounts(branch_id,name,platform,acct_type,created_at) VALUES (" + branchId + ",'ငွေသား',NULL,'cash','" + today + "')");
            }
            SeedData.insertTxnTypes(c, branchId);
            // Note: NO fee_rules seeded — the owner sets their own rates in Settings.
            // (If you want default Wave/KBZ/AYA rules, tell me and I'll add them here.)

            c.commit();
        } catch (Exception e) {
            c.rollback();
            throw e;
        } finally {
            c.setAutoCommit(oldAuto);
        }
    }
}