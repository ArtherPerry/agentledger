package com.agentledger.service;

import com.agentledger.i18n.I18n;

import com.agentledger.db.Database;

import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public final class BackupService {
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private BackupService() {}

    /** Copy the (encrypted) DB to dest, record a backups row with its SHA-256. */
    public static void backupTo(File dest) throws Exception {
        if (!Permissions.canBackup()) throw new IllegalStateException(I18n.t("error.noPermission"));

        Database.checkpoint();                       // flush WAL into the main file
        Path src = Database.dbFilePath();
        Files.copy(src, dest.toPath(), StandardCopyOption.REPLACE_EXISTING);

        String hash = sha256(dest.toPath());
        long size = Files.size(dest.toPath());

        Connection c = Database.get();
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO backups(ts,location,size_bytes,sha256,verified,created_by) VALUES (?,?,?,?,1,?)")) {
            ps.setString(1, LocalDateTime.now().format(TS));
            ps.setString(2, dest.getAbsolutePath());
            ps.setLong(3, size);
            ps.setString(4, hash);
            ps.setInt(5, Session.user().id());
            ps.executeUpdate();
        }
        ActivityLog.record(c, Session.branchId(), Session.user().id(), "BACKUP", dest.getName());
    }

    /**
     * Restore: replace the live DB with the chosen file.
     * The app must reconnect afterwards, so we signal the caller to restart.
     */
    public static void restoreFrom(File source) throws Exception {
        if (!Permissions.canBackup()) throw new IllegalStateException(I18n.t("error.noPermission"));
        if (!source.exists()) throw new IllegalStateException(I18n.t("error.fileNotFound"));

        // sanity: must be a readable SQLite file we can open with our key
        // (a quick check happens on next launch; here we just swap the file)
        Database.checkpoint();
        Database.closePublic();                      // close the live connection

        Path live = Database.dbFilePath();
        // remove WAL/SHM so the restored file isn't mixed with old WAL
        Files.deleteIfExists(Path.of(live.toString() + "-wal"));
        Files.deleteIfExists(Path.of(live.toString() + "-shm"));
        Files.copy(source.toPath(), live, StandardCopyOption.REPLACE_EXISTING);
    }

    public static List<com.agentledger.model.BackupRow> history(int limit) {
        List<com.agentledger.model.BackupRow> out = new ArrayList<>();
        String sql = "SELECT b.ts, b.location, b.size_bytes, b.verified, COALESCE(u.name,'—') " +
                "FROM backups b LEFT JOIN users u ON u.id=b.created_by ORDER BY b.id DESC LIMIT ?";
        try {
            Connection c = Database.get();
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setInt(1, limit);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next())
                        out.add(new com.agentledger.model.BackupRow(
                                rs.getString(1), rs.getString(2), rs.getLong(3),
                                rs.getInt(4) == 1, rs.getString(5)));
                }
            }
        } catch (Exception e) { com.agentledger.utils.Log.error(e); }
        return out;
    }

    /** Latest backup timestamp string, or null if never. */
    public static String lastBackupTs() {
        try {
            Connection c = Database.get();
            try (Statement s = c.createStatement();
                 ResultSet rs = s.executeQuery("SELECT ts FROM backups ORDER BY id DESC LIMIT 1")) {
                return rs.next() ? rs.getString(1) : null;
            }
        } catch (Exception e) { return null; }
    }

    private static String sha256(Path file) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buf = new byte[8192]; int n;
            while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
        }
        StringBuilder sb = new StringBuilder();
        for (byte b : md.digest()) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}