package com.agentledger.license;

import com.agentledger.db.Database;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.sql.*;
import java.util.Base64;

/**
 * Owner password reset, authorized by YOU (signed with the same private key as licensing).
 * Flow: app makes a reset CODE (deviceId + nonce) -> you sign it -> app verifies + resets.
 */
public final class ResetService {
    private ResetService() {}

    /** Create (or reuse) a reset code: deviceId + a fresh random nonce stored in the DB. */
    public static String requestCode() throws Exception {
        byte[] n = new byte[9];
        new SecureRandom().nextBytes(n);
        String nonce = Base64.getUrlEncoder().withoutPadding().encodeToString(n);
        setNonce(nonce);
        return DeviceId.get() + ":" + nonce;
    }

    /**
     * Verify the reset key signs the current reset code, then set a new owner password.
     * Returns true on success. The nonce is cleared so the key can't be reused.
     */
    public static boolean applyReset(String resetKey, String newPassword) throws Exception {
        String nonce = getNonce();
        if (nonce == null || nonce.isBlank()) return false;       // no active request
        if (newPassword == null || newPassword.length() < 4) return false;

        String code = DeviceId.get() + ":" + nonce;
        if (!LicenseService.verify(code, resetKey)) return false;  // not signed by you, or wrong device/nonce

        // update the owner password (the owner of the current branch context, or the single owner)
        Connection c = Database.get();
        String hash = at.favre.lib.crypto.bcrypt.BCrypt.withDefaults()
                .hashToString(12, newPassword.toCharArray());
        boolean oldAuto = c.getAutoCommit();
        c.setAutoCommit(false);
        try {
            int updated;
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE users SET pwd_hash=? WHERE role='owner'")) {
                ps.setString(1, hash);
                updated = ps.executeUpdate();
            }
            clearNonce(c);
            c.commit();
            return updated > 0;
        } catch (Exception e) {
            c.rollback();
            throw e;
        } finally {
            c.setAutoCommit(oldAuto);
        }
    }

    // ---- nonce storage in app_meta ----

    private static void setNonce(String nonce) throws Exception {
        Connection c = Database.get();
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO app_meta(key,value) VALUES('reset_nonce',?) " +
                        "ON CONFLICT(key) DO UPDATE SET value=excluded.value")) {
            ps.setString(1, nonce);
            ps.executeUpdate();
        }
    }

    private static String getNonce() throws Exception {
        Connection c = Database.get();
        try (Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT value FROM app_meta WHERE key='reset_nonce'")) {
            return rs.next() ? rs.getString(1) : null;
        }
    }

    private static void clearNonce(Connection c) throws Exception {
        try (Statement s = c.createStatement()) {
            s.execute("DELETE FROM app_meta WHERE key='reset_nonce'");
        }
    }
}