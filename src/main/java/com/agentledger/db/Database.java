package com.agentledger.db;

import at.favre.lib.crypto.bcrypt.BCrypt;
import com.agentledger.model.TxnType;
import org.sqlite.mc.SQLiteMCConfig;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public final class Database {

    // TODO (hardening, step 2): replace with a password entered at first run + recovery key.
    private static final String DEV_KEY = "wQa3awhw8q4Ngxe0VGW3GtqzDAicSjBEz36UKM6yjrI";

    /** Optional override for the DB location (used by tests). */
    public static final String DB_PATH_PROPERTY = "agentledger.db.path";

    /**
     * Frozen baseline schema version. schema.sql represents THIS version and must never
     * be edited again. To change the schema, APPEND a Migration below — never touch
     * schema.sql or edit/reorder an existing migration.
     */
    private static final int BASELINE = 5;

    @FunctionalInterface
    private interface Migration { void apply(Connection c) throws Exception; }

    /**
     * Ordered, in-place schema upgrades. Step at index i upgrades a DB from version
     * (BASELINE + i) to (BASELINE + i + 1). Append-only. Adding a step automatically
     * raises SCHEMA_VERSION, so version and migrations can never drift apart.
     */
    private static final Migration[] MIGRATIONS = {
            // v5 -> v6: speed up date-range reports.
            conn -> {
                try (Statement s = conn.createStatement()) {
                    s.executeUpdate("CREATE INDEX IF NOT EXISTS idx_ledger_created_at ON ledger_entries(created_at)");
                }
            },
            // v6 -> v7: fee rules become per-transaction-type. Idempotent: only adds the
            // column if absent, so a re-run (e.g. partial/interrupted upgrade) is safe.
            conn -> {
                try (Statement s = conn.createStatement()) {
                    boolean hasCol = false;
                    try (ResultSet rs = s.executeQuery("PRAGMA table_info(fee_rules)")) {
                        while (rs.next()) if ("type_name".equals(rs.getString("name"))) hasCol = true;
                    }
                    if (!hasCol) s.executeUpdate("ALTER TABLE fee_rules ADD COLUMN type_name TEXT");
                    s.executeUpdate("UPDATE fee_rules SET type_name='" + TxnType.PASSWORD_WITHDRAW + "' WHERE type_name IS NULL");
                }
            },
            // v7 -> v8: transaction types get an editable display name, ordering, and a
            // builtin flag so the owner can rename/disable/reorder and add custom types.
            // Idempotent: only adds columns if absent; backfills once.
            conn -> {
                try (Statement s = conn.createStatement()) {
                    java.util.Set<String> cols = new java.util.HashSet<>();
                    try (ResultSet rs = s.executeQuery("PRAGMA table_info(txn_types)")) {
                        while (rs.next()) cols.add(rs.getString("name"));
                    }
                    if (!cols.contains("display_name"))
                        s.executeUpdate("ALTER TABLE txn_types ADD COLUMN display_name TEXT");
                    if (!cols.contains("sort_order"))
                        s.executeUpdate("ALTER TABLE txn_types ADD COLUMN sort_order INTEGER");
                    if (!cols.contains("is_builtin"))
                        s.executeUpdate("ALTER TABLE txn_types ADD COLUMN is_builtin INTEGER NOT NULL DEFAULT 0");
                    s.executeUpdate("UPDATE txn_types SET display_name = name WHERE display_name IS NULL");
                    s.executeUpdate("UPDATE txn_types SET sort_order = id WHERE sort_order IS NULL");
                    String builtinList = "'" + String.join("','",
                            com.agentledger.model.TxnType.PASSWORD_WITHDRAW,
                            com.agentledger.model.TxnType.PASSWORD_TRANSFER,
                            com.agentledger.model.TxnType.WALLET_TO_WALLET,
                            com.agentledger.model.TxnType.ACCOUNT_WITHDRAW,
                            com.agentledger.model.TxnType.CASH_TO_ACCOUNT,
                            com.agentledger.model.TxnType.TOPUP_DIGITAL,
                            com.agentledger.model.TxnType.TOPUP_CASH,
                            com.agentledger.model.TxnType.REPAY_RECEIVABLE,
                            com.agentledger.model.TxnType.REPAY_PAYABLE) + "'";
                    s.executeUpdate("UPDATE txn_types SET is_builtin = 1 WHERE name IN (" + builtinList + ")");
                }
            },
            // v8 -> v9: fee rules get a minimum-commission floor (mirrors min_fee_pya) so a flat
            // per-band commission can be represented as comm_pct=0 + min_comm_pya=<flat>. Idempotent.
            conn -> {
                try (Statement s = conn.createStatement()) {
                    boolean hasCol = false;
                    try (ResultSet rs = s.executeQuery("PRAGMA table_info(fee_rules)")) {
                        while (rs.next()) if ("min_comm_pya".equals(rs.getString("name"))) hasCol = true;
                    }
                    if (!hasCol) s.executeUpdate("ALTER TABLE fee_rules ADD COLUMN min_comm_pya INTEGER NOT NULL DEFAULT 0");
                }
            },
    };

    /** Schema version the code expects = baseline + number of migrations. */
    public static final int SCHEMA_VERSION = BASELINE + MIGRATIONS.length;

    private static Connection conn;

    private Database() {}

    public static synchronized Connection get() throws Exception {
        if (conn != null) return conn;

        Path dbFile = resolveDbFile();
        Path parent = dbFile.getParent();
        if (parent != null) Files.createDirectories(parent);

        boolean fresh = !Files.exists(dbFile);

        String url = "jdbc:sqlite:" + dbFile.toAbsolutePath();
        Properties props = new SQLiteMCConfig.Builder().withKey(DEV_KEY).build().toProperties();
        conn = DriverManager.getConnection(url, props);

        try (Statement s = conn.createStatement()) {
            s.execute("PRAGMA foreign_keys = ON");
            s.execute("PRAGMA journal_mode = WAL");
            s.execute("PRAGMA synchronous = NORMAL");
        }

        if (fresh) {
            runScript(loadResource("/schema.sql"));   // baseline schema (v5)
            seed();
            setSchemaVersion(BASELINE);
            if (SCHEMA_VERSION > BASELINE) migrate(BASELINE);   // bring new DB up to current
            com.agentledger.utils.Log.info("[DB] created database at v" + SCHEMA_VERSION + " (" + dbFile + ")");
        } else {
            int dbVersion = readSchemaVersion();
            if (dbVersion > SCHEMA_VERSION) {
                // DB was written by a NEWER app. Refuse rather than risk corrupting it.
                throw new IllegalStateException(
                        "Database (v" + dbVersion + ") is newer than this app (v" + SCHEMA_VERSION +
                                "). Please install the latest AgentLedger.");
            } else if (dbVersion < SCHEMA_VERSION) {
                backupBeforeMigrate(dbFile, dbVersion);
                migrate(dbVersion);
                com.agentledger.utils.Log.info("[DB] migrated v" + dbVersion + " -> v" + SCHEMA_VERSION);
            } else {
                com.agentledger.utils.Log.info("[DB] opened database at v" + dbVersion);
            }
        }
        return conn;
    }

    // ---- schema version + migrations ----

    private static int readSchemaVersion() throws SQLException {
        try (Statement s = conn.createStatement();
             ResultSet rs = s.executeQuery("SELECT value FROM app_meta WHERE key='schema_version'")) {
            return rs.next() ? Integer.parseInt(rs.getString(1)) : 0;
        }
    }

    private static void setSchemaVersion(int v) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE app_meta SET value=? WHERE key='schema_version'")) {
            ps.setString(1, String.valueOf(v));
            if (ps.executeUpdate() == 0) {   // row not present yet -> insert it
                try (PreparedStatement ins = conn.prepareStatement(
                        "INSERT INTO app_meta(key,value) VALUES('schema_version',?)")) {
                    ins.setString(1, String.valueOf(v));
                    ins.executeUpdate();
                }
            }
        }
    }

    /** Apply each migration from fromVersion up to SCHEMA_VERSION, all-or-nothing. */
    private static void migrate(int fromVersion) throws Exception {
        boolean oldAuto = conn.getAutoCommit();
        conn.setAutoCommit(false);
        try {
            for (int v = fromVersion; v < SCHEMA_VERSION; v++) {
                MIGRATIONS[v - BASELINE].apply(conn);
                setSchemaVersion(v + 1);
            }
            conn.commit();
        } catch (Exception e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(oldAuto);
        }
    }

    /** Copy the DB before migrating, so the original is recoverable if a migration fails. */
    private static void backupBeforeMigrate(Path dbFile, int fromVersion) throws Exception {
        try (Statement s = conn.createStatement()) {
            s.execute("PRAGMA wal_checkpoint(TRUNCATE)");   // fold WAL in so the copy is complete
        }
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        Path backup = dbFile.resolveSibling(
                dbFile.getFileName() + ".pre-migration-v" + fromVersion + "-" + stamp + ".bak");
        Files.copy(dbFile, backup, StandardCopyOption.REPLACE_EXISTING);
        com.agentledger.utils.Log.info("[DB] pre-migration backup: " + backup);
    }

    // ---- paths ----

    private static Path resolveDbFile() {
        String override = System.getProperty(DB_PATH_PROPERTY);
        if (override != null && !override.isBlank()) return Path.of(override);
        return appDataDir().resolve("data.db");
    }

    public static Path appDataDir() {
        String appData = System.getenv("APPDATA");
        Path base = (appData != null && !appData.isBlank())
                ? Path.of(appData) : Path.of(System.getProperty("user.home"));
        return base.resolve("AgentLedger");
    }

    private static String loadResource(String path) throws Exception {
        try (InputStream in = Database.class.getResourceAsStream(path)) {
            if (in == null) throw new IllegalStateException("Resource not found: " + path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** Runs a multi-statement SQL script, keeping CREATE TRIGGER ... BEGIN ... END; intact. */
    private static void runScript(String script) throws SQLException {
        List<String> statements = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inBlock = false;
        for (String line : script.split("\n")) {
            String t = line.trim();
            if (t.isEmpty() || t.startsWith("--")) continue;
            cur.append(line).append("\n");
            String up = t.toUpperCase();
            if (up.contains("BEGIN")) inBlock = true;
            if (inBlock) {
                if (up.endsWith("END;")) { inBlock = false; statements.add(cur.toString()); cur.setLength(0); }
            } else if (t.endsWith(";")) {
                statements.add(cur.toString()); cur.setLength(0);
            }
        }
        try (Statement s = conn.createStatement()) {
            for (String sql : statements) s.execute(sql);
        }
    }

    private static void closeIfOpen() {
        try { if (conn != null) { conn.close(); conn = null; } } catch (Exception ignore) {}
    }

    /**
     * Fresh-database seed. Production: NO demo data. The first branch + owner are created
     * by the first-run Setup screen (SetupController -> BranchService.createFirstBranch).
     * We only ensure the schema_version row exists; get() sets the value.
     */
    private static void seed() throws Exception {
        // intentionally empty — a fresh DB has schema + triggers only.
        // schema_version is written by get() via setSchemaVersion(BASELINE).
    }

    public static Path dbFilePath() { return resolveDbFile(); }

    public static void checkpoint() throws Exception {
        try (Statement s = get().createStatement()) {
            s.execute("PRAGMA wal_checkpoint(TRUNCATE)");
        }
    }

    public static synchronized void closePublic() { closeIfOpen(); }
}
