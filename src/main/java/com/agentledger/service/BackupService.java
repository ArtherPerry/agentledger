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
     * Safety order: verify the source's SHA-256 (if known) BEFORE touching the live DB;
     * keep a .pre-restore copy of the current live DB so it is always recoverable.
     * The app must reconnect afterwards, so the caller should restart.
     */
    public static void restoreFrom(File source) throws Exception {
        if (!Permissions.canBackup()) throw new IllegalStateException(I18n.t("error.noPermission"));
        if (!source.exists()) throw new IllegalStateException(I18n.t("error.fileNotFound"));

        // 1) VERIFY FIRST — before we close or overwrite anything.
        // If we recorded a SHA-256 for this backup file, the file on disk must still match it.
        String expected = storedSha256For(source.getAbsolutePath());
        if (expected != null) {
            String actual = sha256(source.toPath());
            if (!expected.equalsIgnoreCase(actual)) {
                throw new IllegalStateException(I18n.t("backup.err.corrupt"));   // refuse; live DB untouched
            }
        }

        // 2) Now it's safe to take the live DB offline.
        Database.checkpoint();
        Database.closePublic();

        Path live = Database.dbFilePath();

        // 3) SAFETY NET: copy the current live DB aside before we overwrite it, so a
        //    failure (or a bad source we couldn't verify) is always recoverable.
        if (Files.exists(live)) {
            String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
            Path safety = live.resolveSibling(live.getFileName() + ".pre-restore-" + stamp + ".bak");
            Files.copy(live, safety, StandardCopyOption.REPLACE_EXISTING);
            com.agentledger.utils.Log.info("[RESTORE] pre-restore safety copy: " + safety);
        }

        // 4) Remove WAL/SHM so the restored file isn't shadowed by stale WAL.
        Files.deleteIfExists(Path.of(live.toString() + "-wal"));
        Files.deleteIfExists(Path.of(live.toString() + "-shm"));

        // 5) Swap in the chosen backup.
        Files.copy(source.toPath(), live, StandardCopyOption.REPLACE_EXISTING);
        com.agentledger.utils.Log.info("[RESTORE] restored live DB from " + source.getAbsolutePath());
    }

    /** The SHA-256 we recorded for a backup at this absolute path, or null if we have no record. */
    private static String storedSha256For(String absolutePath) {
        String sql = "SELECT sha256 FROM backups WHERE location=? ORDER BY id DESC LIMIT 1";
        try {
            Connection c = Database.get();
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, absolutePath);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? rs.getString(1) : null;
                }
            }
        } catch (Exception e) {
            com.agentledger.utils.Log.error(e);
            return null;   // no record available -> we'll still keep the pre-restore safety copy
        }
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