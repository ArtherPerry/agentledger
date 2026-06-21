package com.agentledger;

import com.agentledger.db.Database;
import org.junit.jupiter.api.Test;

import java.nio.file.*;
import java.sql.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class MigrationTest extends TestBase {

    private boolean indexExists(Connection c) throws Exception {
        try (Statement s = c.createStatement();
             ResultSet rs = s.executeQuery(
                     "SELECT 1 FROM sqlite_master WHERE type='index' AND name='idx_ledger_created_at'")) {
            return rs.next();
        }
    }

    private boolean typeNameColumnExists(Connection c) throws Exception {
        try (Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("PRAGMA table_info(fee_rules)")) {
            while (rs.next()) if ("type_name".equals(rs.getString("name"))) return true;
            return false;
        }
    }

    private int version(Connection c) throws Exception {
        try (Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT value FROM app_meta WHERE key='schema_version'")) {
            return rs.next() ? Integer.parseInt(rs.getString(1)) : -1;
        }
    }

    @Test void freshDbIsAtCurrentVersionWithMigrationsApplied() throws Exception {
        Connection c = Database.get();
        assertEquals(Database.SCHEMA_VERSION, version(c));
        assertTrue(indexExists(c), "fresh DB should already have the v6 index");
        assertTrue(typeNameColumnExists(c), "fresh DB should already have the v7 type_name column");
    }

    @Test void oldDbMigratesInPlaceAndKeepsData() throws Exception {
        Connection c = Database.get();   // fresh, current version, seeded

        int branchesBefore;
        try (Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM branches")) {
            rs.next(); branchesBefore = rs.getInt(1);
        }
        assertTrue(branchesBefore > 0);

        // fabricate an "old v5" database: undo v6 + v7 artifacts + set version back to 5
        try (Statement s = c.createStatement()) {
            s.execute("DROP INDEX IF EXISTS idx_ledger_created_at");      // undo v6
            s.execute("ALTER TABLE fee_rules DROP COLUMN type_name");      // undo v7
            s.execute("UPDATE app_meta SET value='5' WHERE key='schema_version'");
        }
        assertEquals(5, version(c));
        assertFalse(indexExists(c));
        assertFalse(typeNameColumnExists(c));

        // reopen -> detects v5 < current, backs up, migrates in place
        Database.closePublic();
        Connection c2 = Database.get();

        assertEquals(Database.SCHEMA_VERSION, version(c2));
        assertTrue(indexExists(c2), "migration should re-create the index");
        assertTrue(typeNameColumnExists(c2), "migration should add the type_name column");

        try (Statement s = c2.createStatement();
             ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM branches")) {
            rs.next();
            assertEquals(branchesBefore, rs.getInt(1), "existing data must survive migration");
        }

        // a pre-migration backup was written next to the db
        Path dir = Database.dbFilePath().getParent();
        try (Stream<Path> files = Files.list(dir)) {
            assertTrue(files.anyMatch(p -> p.getFileName().toString().contains("pre-migration")),
                    "a pre-migration backup file should exist");
        }
    }
}
