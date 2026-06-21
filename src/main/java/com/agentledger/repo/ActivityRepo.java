package com.agentledger.repo;

import com.agentledger.db.Database;
import com.agentledger.model.ActivityRow;

import java.sql.*;
import java.util.*;

public final class ActivityRepo {
    private ActivityRepo() {}

    public static List<ActivityRow> recent(int branchId, int limit) {
        List<ActivityRow> out = new ArrayList<>();
        String sql = "SELECT l.ts, COALESCE(u.name,'—') AS uname, l.action, COALESCE(l.detail,'') AS detail " +
                "FROM activity_log l LEFT JOIN users u ON u.id = l.user_id " +
                "WHERE l.branch_id = ? ORDER BY l.id DESC LIMIT ?";
        try {
            Connection c = Database.get();
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setInt(1, branchId);
                ps.setInt(2, limit);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next())
                        out.add(new ActivityRow(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4)));
                }
            }
        } catch (Exception e) { com.agentledger.utils.Log.error(e); }
        return out;
    }
}