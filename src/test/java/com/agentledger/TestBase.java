package com.agentledger;

import com.agentledger.db.Database;
import com.agentledger.model.User;
import com.agentledger.service.Session;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.nio.file.Files;
import java.nio.file.Path;

/** Base for tests: fresh DB in a temp file, standard fixture data, logged in as branch-1 owner. */
public abstract class TestBase {

    protected Path dbFile;

    @BeforeEach
    void setUpDb() throws Exception {
        dbFile = Files.createTempFile("agentledger-test-", ".db");
        Files.deleteIfExists(dbFile);                              // let Database create it fresh
        System.setProperty(Database.DB_PATH_PROPERTY, dbFile.toString());
        Database.closePublic();                                    // drop any connection from a prior test
        Database.get();                                            // schema only (production seed is empty now)
        TestFixtures.seedStandard();                               // recreate the data tests rely on

        // log in as the seeded owner of branch 1
        Session.login(new User(1, 1, "Owner 1", "owner1", "owner"));
        Session.setBranch(1, "ဆိုင်ခွဲ ၁");
    }

    @AfterEach
    void tearDownDb() throws Exception {
        Database.closePublic();
        Session.logout();
        System.clearProperty(Database.DB_PATH_PROPERTY);
        Files.deleteIfExists(dbFile);
        Files.deleteIfExists(Path.of(dbFile + "-wal"));
        Files.deleteIfExists(Path.of(dbFile + "-shm"));
    }
}