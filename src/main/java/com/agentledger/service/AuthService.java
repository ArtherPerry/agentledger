package com.agentledger.service;

import at.favre.lib.crypto.bcrypt.BCrypt;
import com.agentledger.db.Database;
import com.agentledger.model.User;

import java.sql.*;
import java.util.Optional;

public final class AuthService {

    private AuthService() {}

    public static Optional<User> login(int branchId, String username, String password) {
        String sql = "SELECT id, branch_id, name, username, role, pwd_hash " +
                "FROM users WHERE branch_id = ? AND username = ? AND active = 1";
        try {
            Connection c = Database.get();
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setInt(1, branchId);
                ps.setString(2, username);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) return Optional.empty();
                    BCrypt.Result r = BCrypt.verifyer()
                            .verify(password.toCharArray(), rs.getString("pwd_hash"));
                    if (!r.verified) return Optional.empty();
                    return Optional.of(new User(
                            rs.getInt("id"), rs.getInt("branch_id"),
                            rs.getString("name"), rs.getString("username"), rs.getString("role")));
                }
            }
        } catch (Exception e) {
            com.agentledger.utils.Log.error(e);
            return Optional.empty();
        }
    }
}