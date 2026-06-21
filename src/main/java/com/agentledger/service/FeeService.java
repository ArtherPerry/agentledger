package com.agentledger.service;

import com.agentledger.db.Database;
import com.agentledger.model.FeeResult;

import java.sql.*;

public final class FeeService {
    private FeeService() {}

    /** platform may be null (cash) -> no fee. typeName identifies the transaction type. */
    public static FeeResult compute(int branchId, String typeName, String platform, long amountPya) {
        if (platform == null || typeName == null || amountPya <= 0) return new FeeResult(0, 0);

        String sql = "SELECT fee_pct, min_fee_pya, comm_pct FROM fee_rules " +
                "WHERE branch_id=? AND type_name=? AND platform=? AND active=1 " +
                "AND ? >= min_amount_pya AND (max_amount_pya IS NULL OR ? <= max_amount_pya) " +
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
                    if (!rs.next()) return new FeeResult(0, 0);
                    double feePct = rs.getDouble("fee_pct");
                    long minFee  = rs.getLong("min_fee_pya");
                    double commPct = rs.getDouble("comm_pct");
                    long fee = Math.round(amountPya * feePct / 100.0);
                    if (fee < minFee) fee = minFee;
                    long comm = Math.round(amountPya * commPct / 100.0);
                    return new FeeResult(fee, comm);
                }
            }
        } catch (Exception e) { com.agentledger.utils.Log.error(e); }
        return new FeeResult(0, 0);
    }
}
