package com.agentledger.repo;

import com.agentledger.db.Database;
import com.agentledger.model.TxnType;

import java.sql.*;
import java.util.*;

public final class TxnTypeRepo {
    private TxnTypeRepo() {}

    private static final String EXCLUDE_INTERNAL =
            " AND name NOT IN (" + TxnType.internalSqlList() + ") ";

    public static List<TxnType> listForBranch(int branchId) {
        List<TxnType> out = new ArrayList<>();
        String sql = "SELECT id,name,cash_effect,digital_effect FROM txn_types " +
                "WHERE branch_id=? AND active=1" + EXCLUDE_INTERNAL + "ORDER BY id";
        try {
            Connection c = Database.get();
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setInt(1, branchId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next())
                        out.add(new TxnType(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getString(4)));
                }
            }
        } catch (Exception e) { com.agentledger.utils.Log.error(e); }
        return out;
    }

    public static TxnType byName(int branchId, String name) {
        String sql = "SELECT id,name,cash_effect,digital_effect FROM txn_types WHERE branch_id=? AND name=? AND active=1";
        try {
            Connection c = Database.get();
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setInt(1, branchId);
                ps.setString(2, name);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next())
                        return new TxnType(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getString(4));
                }
            }
        } catch (Exception e) { com.agentledger.utils.Log.error(e); }
        return null;
    }
}