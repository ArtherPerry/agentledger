package com.agentledger.service;

import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** Append-only audit trail. Call within or after a posting. */
public final class ActivityLog {
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private ActivityLog() {}

    public static void record(Connection c, Integer branchId, Integer userId,
                              String action, String detail) throws SQLException {
        String sql = "INSERT INTO activity_log(branch_id,ts,user_id,action,detail) VALUES (?,?,?,?,?)";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            if (branchId != null) ps.setInt(1, branchId); else ps.setNull(1, Types.INTEGER);
            ps.setString(2, LocalDateTime.now().format(TS));
            if (userId != null) ps.setInt(3, userId); else ps.setNull(3, Types.INTEGER);
            ps.setString(4, action);
            ps.setString(5, detail);
            ps.executeUpdate();
        }
    }
}