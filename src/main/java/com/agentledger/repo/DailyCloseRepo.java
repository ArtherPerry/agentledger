package com.agentledger.repo;

import com.agentledger.db.Database;

import java.sql.*;

public final class DailyCloseRepo {
    private DailyCloseRepo() {}

    /** True if today's close exists and is 'closed' for this branch.
     *  Throws on a real DB error rather than returning false — a DB hiccup must NOT
     *  be read as "day is open", which would let entries slip into a closed day. */
    public static boolean isTodayClosed(int branchId) {
        String sql = "SELECT 1 FROM daily_closes WHERE branch_id=? " +
                "AND business_date=date('now','localtime') AND status='closed'";
        try {
            Connection c = Database.get();
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setInt(1, branchId);
                try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
            }
        } catch (Exception e) {
            com.agentledger.utils.Log.error(e);
            throw new IllegalStateException("Cannot verify daily close status", e);
        }
    }
    /** Expected cash for the branch (sum of cash deltas) in pya. */
    public static long expectedCash(int branchId) {
        return LedgerRepo.branchCashPya(branchId);
    }

    /** Insert the close + per-account lines and lock the day, in one transaction. */
    public static void close(int branchId, int userId,
                             long expectedCash, long actualCash,
                             java.util.List<long[]> lines,  // each: {accountId, expected, actual}
                             long totalDiscrepancy, String reason) throws Exception {
        Connection c = Database.get();
        boolean oldAuto = c.getAutoCommit();
        c.setAutoCommit(false);
        try {
            long closeId;
            String ins = "INSERT INTO daily_closes(branch_id,business_date,status," +
                    "expected_cash_pya,actual_cash_pya,discrepancy_pya,reason,closed_at,closed_by) " +
                    "VALUES (?,date('now','localtime'),'closed',?,?,?,?," +
                    "strftime('%Y-%m-%d %H:%M:%S','now','localtime'),?)";
            try (PreparedStatement ps = c.prepareStatement(ins, Statement.RETURN_GENERATED_KEYS)) {
                ps.setInt(1, branchId);
                ps.setLong(2, expectedCash);
                ps.setLong(3, actualCash);
                ps.setLong(4, totalDiscrepancy);
                if (reason == null || reason.isBlank()) ps.setNull(5, Types.VARCHAR); else ps.setString(5, reason.trim());
                ps.setInt(6, userId);
                ps.executeUpdate();
                try (ResultSet k = ps.getGeneratedKeys()) { k.next(); closeId = k.getLong(1); }
            }
            String il = "INSERT INTO daily_close_lines(close_id,account_id,expected_pya,actual_pya,diff_pya) VALUES (?,?,?,?,?)";
            try (PreparedStatement ps = c.prepareStatement(il)) {
                for (long[] ln : lines) {
                    ps.setLong(1, closeId);
                    ps.setLong(2, ln[0]);
                    ps.setLong(3, ln[1]);
                    ps.setLong(4, ln[2]);
                    ps.setLong(5, ln[2] - ln[1]);
                    ps.executeUpdate();
                }
            }
            com.agentledger.service.ActivityLog.record(c, branchId, userId, "DAY_CLOSE",
                    "ကွာဟမှု " + com.agentledger.utils.Money.format(totalDiscrepancy) + " ကျပ်");
            c.commit();
        } catch (Exception e) {
            c.rollback();
            throw e;
        } finally {
            c.setAutoCommit(oldAuto);
        }
    }

    /** Owner reopen: flip today's close back to open. */
    public static void reopenToday(int branchId, int userId) throws Exception {
        Connection c = Database.get();
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE daily_closes SET status='open' WHERE branch_id=? AND business_date=date('now','localtime')")) {
            ps.setInt(1, branchId);
            ps.executeUpdate();
        }
        com.agentledger.service.ActivityLog.record(c, branchId, userId, "DAY_REOPEN", "ယနေ့");
    }
}