package com.agentledger;

import com.agentledger.db.Database;
import com.agentledger.model.Account;
import com.agentledger.repo.AccountRepo;
import com.agentledger.repo.TxnTypeRepo;
import com.agentledger.service.LedgerService;
import com.agentledger.service.PostingRequest;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

class AppendOnlyTest extends TestBase {

    private long postOne() throws Exception {
        Account wave = AccountRepo.listForBranch(1).stream()
                .filter(a -> "Wave Main".equals(a.name())).findFirst().orElseThrow();
        PostingRequest r = new PostingRequest();
        r.type = TxnTypeRepo.byName(1, "Password ဖြင့် ထုတ်ယူ");
        r.account = wave;
        r.amountPya = 10_000_000L;
        return LedgerService.post(r);
    }

    @Test void updateOnLedgerIsBlocked() throws Exception {
        long id = postOne();
        Connection c = Database.get();
        try (Statement s = c.createStatement()) {
            assertThrows(Exception.class,
                    () -> s.executeUpdate("UPDATE ledger_entries SET amount_pya = 1 WHERE id = " + id),
                    "ledger_entries must reject UPDATE");
        }
    }

    @Test void deleteOnLedgerIsBlocked() throws Exception {
        long id = postOne();
        Connection c = Database.get();
        try (Statement s = c.createStatement()) {
            assertThrows(Exception.class,
                    () -> s.executeUpdate("DELETE FROM ledger_entries WHERE id = " + id),
                    "ledger_entries must reject DELETE");
        }
    }

    @Test void entrySurvivesBlockedMutation() throws Exception {
        long id = postOne();
        Connection c = Database.get();
        // attempt a blocked update, swallow the error, then confirm the row is intact
        try (Statement s = c.createStatement()) {
            try { s.executeUpdate("UPDATE ledger_entries SET amount_pya = 999 WHERE id = " + id); }
            catch (Exception ignore) { /* expected */ }
        }
        try (Statement s = c.createStatement();
             var rs = s.executeQuery("SELECT amount_pya FROM ledger_entries WHERE id = " + id)) {
            assertTrue(rs.next());
            assertEquals(10_000_000L, rs.getLong(1), "amount must be unchanged after blocked update");
        }
    }
}