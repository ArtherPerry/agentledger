package com.agentledger.service;

import com.agentledger.db.Database;
import com.agentledger.model.FeeResult;

import java.sql.*;

public final class FeeService {
    private FeeService() {}

    /** Thrown when the fee/rule lookup hits a real DB error (as opposed to simply finding no rule).
     *  Callers must surface this rather than silently treating it as a zero fee. */
    public static class FeeLookupException extends RuntimeException {
        public FeeLookupException(Throwable cause) { super("Fee lookup failed", cause); }
    }

    /** platform may be null (cash) -> no fee. typeName identifies the transaction type.
     *  Returns FeeResult(0,0) when NO rule matches (legitimate). Throws FeeLookupException on a real DB error. */
    public static FeeResult compute(int branchId, String typeName, String platform, long amountPya) {
        if (platform == null || typeName == null || amountPya <= 0) return new FeeResult(0, 0);

        // A max of NULL or <= 0 means "no upper limit" (a 0 max would otherwise match nothing).
        // Platform match is space/case-insensitive so "Wave Money" and "WaveMoney" are the same.
        String sql = "SELECT fee_pct, min_fee_pya, comm_pct FROM fee_rules " +
                "WHERE branch_id=? AND type_name=? " +
                "AND REPLACE(LOWER(platform),' ','') = REPLACE(LOWER(?),' ','') AND active=1 " +
                "AND ? >= min_amount_pya AND (max_amount_pya IS NULL OR max_amount_pya <= 0 OR ? <= max_amount_pya) " +
                "ORDER BY min_amount_pya DESC LIMIT 1";
        try {
            Connection c = Database.get();
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setInt(1, branchId);
                ps.setString(2, typeName);
                ps.setString(3, platform);
                ps.setLong(4, amountPya);
                ps.setLong(5, amountPya);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) return new FeeResult(0, 0);   // no matching rule = legitimately zero
                    double feePct = rs.getDouble("fee_pct");
                    long minFee  = rs.getLong("min_fee_pya");
                    double commPct = rs.getDouble("comm_pct");
                    long fee = Math.round(amountPya * feePct / 100.0);
                    if (fee < minFee) fee = minFee;
                    long comm = Math.round(amountPya * commPct / 100.0);
                    return new FeeResult(fee, comm);
                }
            }
        } catch (Exception e) {
            // A real DB error must NOT masquerade as a zero fee (that would silently lose revenue).
            com.agentledger.utils.Log.error(e);
            throw new FeeLookupException(e);
        }
    }

    /** True if any active fee rule exists for this branch+type+platform (any amount range).
     *  Uses the SAME space/case-insensitive platform match as compute(), so the "no fee rule"
     *  warning is consistent with whether a fee will actually be charged. */
    public static boolean hasRule(int branchId, String typeName, String platform) {
        if (platform == null || typeName == null) return false;
        String sql = "SELECT 1 FROM fee_rules WHERE branch_id=? AND type_name=? " +
                "AND REPLACE(LOWER(platform),' ','') = REPLACE(LOWER(?),' ','') AND active=1 LIMIT 1";
        try {
            Connection c = Database.get();
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setInt(1, branchId);
                ps.setString(2, typeName);
                ps.setString(3, platform);
                try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
            }
        } catch (Exception e) { com.agentledger.utils.Log.error(e); }
        return false;
    }
}