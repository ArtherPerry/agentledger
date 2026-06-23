package com.agentledger;

import com.agentledger.db.Database;
import com.agentledger.model.TxnType;
import com.agentledger.repo.DailyCloseRepo;
import com.agentledger.repo.LedgerRepo;
import com.agentledger.service.BackupService;
import com.agentledger.service.FeeService;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Batch B — the audit fail-safes: error paths must surface (throw / refuse), not
 *  silently return a plausible-looking value (0, false) or destroy data. */
class AuditFailsafeTest extends TestBase {

    /** Force a broken DB state: close the shared connection, then point the path at a
     *  location that cannot be opened, so the next Database.get() inside the call fails. */
    private void breakDatabase() throws Exception {
        Database.closePublic();
        // point at a path that can't be a valid DB (a directory)
        Path dir = Files.createTempDirectory("agentledger-broken-");
        System.setProperty(Database.DB_PATH_PROPERTY, dir.toString());
    }

    // ---- #2  FeeService.compute: throws on a real DB error, NOT returns 0 ----

    @Test
    void feeCompute_throwsOnDbError_notSilentZero() throws Exception {
        breakDatabase();
        // platform+type+amount are valid; only the DB is broken -> must throw, not return FeeResult(0,0)
        assertThrows(Exception.class, () ->
                        FeeService.compute(1, TxnType.PASSWORD_WITHDRAW, "Wave Money", 1_000_000L),
                "a DB error in fee lookup must surface, not masquerade as a zero fee");
    }

    @Test
    void feeCompute_noRule_returnsZero_notThrow() throws Exception {
        // CONTRAST: a legitimately-missing rule is NOT an error — it returns 0 quietly.
        // (Use a platform with no seeded rule.)
        var fr = FeeService.compute(1, TxnType.PASSWORD_WITHDRAW, "NoSuchPlatform", 1_000_000L);
        assertEquals(0L, fr.feePya(), "no matching rule = legitimate zero fee (no throw)");
    }

    // ---- #3  isTodayClosed: fails closed (throws) on DB error, not silent 'open' ----

    @Test
    void isTodayClosed_throwsOnDbError_notSilentFalse() throws Exception {
        breakDatabase();
        assertThrows(Exception.class, () -> DailyCloseRepo.isTodayClosed(1),
                "a DB error must NOT be read as 'day is open' (which would allow posting into a closed day)");
    }

    // ---- #4  balance queries: throw on DB error, not silent 0 ----

    @Test
    void branchCashPya_throwsOnDbError_notSilentZero() throws Exception {
        breakDatabase();
        assertThrows(Exception.class, () -> LedgerRepo.branchCashPya(1),
                "a balance query must not return a fake 0 on DB error (close would reconcile against it)");
    }

    @Test
    void accountDigitalPya_throwsOnDbError_notSilentZero() throws Exception {
        breakDatabase();
        assertThrows(Exception.class, () -> LedgerRepo.accountDigitalPya(1),
                "digital balance query must not return a fake 0 on DB error");
    }

    // ---- #6  backup restore: refuses a corrupted backup (SHA-256 mismatch) ----

    @Test
    void restore_refusesCorruptedBackup_andKeepsLiveDb() throws Exception {
        // 1) make a valid backup (records its SHA-256 in the backups table)
        File backup = File.createTempFile("agentledger-bk-", ".db");
        BackupService.backupTo(backup);

        // 2) capture the current live balance so we can prove it's untouched after a refused restore
        long liveCashBefore = LedgerRepo.branchCashPya(1);

        // 3) corrupt the backup file (flip its bytes) so its hash no longer matches the recorded one
        byte[] bytes = Files.readAllBytes(backup.toPath());
        for (int i = 0; i < Math.min(bytes.length, 64); i++) bytes[i] ^= 0xFF;
        Files.write(backup.toPath(), bytes);

        // 4) restoring the corrupted file must be REFUSED (SHA-256 mismatch)
        assertThrows(Exception.class, () -> BackupService.restoreFrom(backup),
                "restore must refuse a backup whose SHA-256 doesn't match the recorded value");

        // 5) the live DB must still be intact and usable
        long liveCashAfter = LedgerRepo.branchCashPya(1);
        assertEquals(liveCashBefore, liveCashAfter,
                "a refused restore must leave the live DB untouched");

        backup.delete();
    }
}