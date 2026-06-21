package com.agentledger.repo;

import com.agentledger.db.Database;
import com.agentledger.model.Account;

import java.sql.*;
import java.util.*;

public final class AccountRepo {
    private AccountRepo() {}

    public static List<Account> listForBranch(int branchId) {
        List<Account> out = new ArrayList<>();
        String sql = "SELECT id,name,platform,acct_type FROM accounts WHERE branch_id=? AND active=1 ORDER BY acct_type DESC, name";
        try {
            Connection c = Database.get();
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setInt(1, branchId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next())
                        out.add(new Account(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getString(4)));
                }
            }
        } catch (Exception e) { com.agentledger.utils.Log.error(e); }
        return out;
    }

    public static List<com.agentledger.model.AccountBalance> balancesForBranch(int branchId) {
        List<com.agentledger.model.AccountBalance> out = new ArrayList<>();
        for (Account a : listForBranch(branchId)) {
            long digital = LedgerRepo.accountDigitalPya(a.id());
            long cash = a.isDigital() ? 0
                    : LedgerRepo.branchCashPya(branchId); // cash account shows branch cash
            out.add(new com.agentledger.model.AccountBalance(a, digital, cash));
        }
        return out;
    }

    /** Only the agent's digital wallets (used as the "to account" for wallet-to-wallet). */
    public static List<Account> digitalForBranch(int branchId) {
        List<Account> out = new ArrayList<>();
        for (Account a : listForBranch(branchId)) if (a.isDigital()) out.add(a);
        return out;
    }

    /** Insert a new account; returns the new id. platform may be null (for cash). */
    public static int insert(int branchId, String name, String platform, String acctType) throws Exception {
        Connection c = Database.get();
        String today = java.time.LocalDate.now().toString();
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO accounts(branch_id,name,platform,acct_type,created_at) VALUES (?,?,?,?,?)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, branchId);
            ps.setString(2, name);
            if (platform == null || platform.isBlank()) ps.setNull(3, Types.VARCHAR);
            else ps.setString(3, platform.trim());
            ps.setString(4, acctType);
            ps.setString(5, today);
            ps.executeUpdate();
            try (ResultSet k = ps.getGeneratedKeys()) { k.next(); return k.getInt(1); }
        }
    }

    /** Update name + platform of an existing account (type is NOT editable). */
    public static void update(int accountId, String name, String platform) throws Exception {
        Connection c = Database.get();
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE accounts SET name=?, platform=? WHERE id=?")) {
            ps.setString(1, name.trim());
            if (platform == null || platform.isBlank()) ps.setNull(2, Types.VARCHAR);
            else ps.setString(2, platform.trim());
            ps.setInt(3, accountId);
            ps.executeUpdate();
        }
    }

    /** Soft activate/deactivate. History is preserved; deactivated accounts hide from new txns. */
    public static void setActive(int accountId, boolean active) throws Exception {
        Connection c = Database.get();
        try (PreparedStatement ps = c.prepareStatement("UPDATE accounts SET active=? WHERE id=?")) {
            ps.setInt(1, active ? 1 : 0);
            ps.setInt(2, accountId);
            ps.executeUpdate();
        }
    }

    /** Account type ('cash'/'digital') for guard checks. */
    public static String acctType(int accountId) throws Exception {
        Connection c = Database.get();
        try (PreparedStatement ps = c.prepareStatement("SELECT acct_type FROM accounts WHERE id=?")) {
            ps.setInt(1, accountId);
            try (ResultSet rs = ps.executeQuery()) { return rs.next() ? rs.getString(1) : null; }
        }
    }

    /** Platform suggestions: known + fee-rule platforms + platforms of existing accounts. */
    public static List<String> platformSuggestions(int branchId) {
        LinkedHashSet<String> set = new LinkedHashSet<>();
        set.add("Wave Money");
        set.add("KBZ Pay");
        set.add("AYA Pay");
        set.add("CB Pay");
        set.add("TrueMoney");
        set.add("OK Dollar");
        set.add("Citizens Pay");
        set.add("Mytel Pay");
        set.add("ConnectNow");
        set.add("MPT Pay");
        addPlatforms(set, branchId, "SELECT DISTINCT platform FROM fee_rules WHERE branch_id=? AND platform IS NOT NULL");
        addPlatforms(set, branchId, "SELECT DISTINCT platform FROM accounts WHERE branch_id=? AND platform IS NOT NULL");
        return new ArrayList<>(set);
    }

    private static void addPlatforms(Set<String> set, int branchId, String sql) {
        try {
            Connection c = Database.get();
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setInt(1, branchId);
                try (ResultSet rs = ps.executeQuery()) { while (rs.next()) set.add(rs.getString(1)); }
            }
        } catch (Exception e) { com.agentledger.utils.Log.error(e); }
    }

    /** True if this platform has at least one fee rule (used for the "no fee rule" warning). */
    public static boolean platformHasFeeRule(int branchId, String platform) {
        if (platform == null || platform.isBlank()) return false;
        try {
            Connection c = Database.get();
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT 1 FROM fee_rules WHERE branch_id=? AND platform=? LIMIT 1")) {
                ps.setInt(1, branchId); ps.setString(2, platform.trim());
                try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
            }
        } catch (Exception e) { return false; }
    }
}