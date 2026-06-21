package com.agentledger.repo;

import com.agentledger.db.Database;
import com.agentledger.model.Counterparty;

import java.sql.*;
import java.util.*;

public final class CounterpartyRepo {
    private CounterpartyRepo() {}

    public static List<Counterparty> listForBranch(int branchId) {
        List<Counterparty> out = new ArrayList<>();
        String sql = "SELECT id,name,cp_type,phone FROM counterparties WHERE branch_id=? ORDER BY name";
        try {
            Connection c = Database.get();
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setInt(1, branchId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next())
                        out.add(new Counterparty(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getString(4)));
                }
            }
        } catch (Exception e) { com.agentledger.utils.Log.error(e); }
        return out;
    }

    /** Find by name or create; returns the id. */
    public static int findOrCreate(int branchId, String name, String type, String phone) throws Exception {
        Connection c = Database.get();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT id FROM counterparties WHERE branch_id=? AND name=?")) {
            ps.setInt(1, branchId); ps.setString(2, name);
            try (ResultSet rs = ps.executeQuery()) { if (rs.next()) return rs.getInt(1); }
        }
        String now = java.time.LocalDate.now().toString();
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO counterparties(branch_id,name,cp_type,phone,created_at) VALUES (?,?,?,?,?)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, branchId); ps.setString(2, name);
            ps.setString(3, type); ps.setString(4, phone == null || phone.isBlank() ? null : phone);
            ps.setString(5, now);
            ps.executeUpdate();
            try (ResultSet k = ps.getGeneratedKeys()) { k.next(); return k.getInt(1); }
        }
    }
}