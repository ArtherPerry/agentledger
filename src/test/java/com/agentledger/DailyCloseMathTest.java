package com.agentledger;

import com.agentledger.repo.DailyCloseRepo;
import com.agentledger.repo.LedgerRepo;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DailyCloseMathTest extends TestBase {

    @Test void closeWithZeroDiscrepancy_locksDay() throws Exception {
        long cash = LedgerRepo.branchCashPya(1);
        DailyCloseRepo.close(1, 1, cash, cash, List.of(), 0, null);
        assertTrue(DailyCloseRepo.isTodayClosed(1));
    }

    @Test void doubleClose_isRejected() throws Exception {
        long cash = LedgerRepo.branchCashPya(1);
        DailyCloseRepo.close(1, 1, cash, cash, List.of(), 0, null);
        // UNIQUE(branch_id, business_date) must block a second close today
        assertThrows(Exception.class,
                () -> DailyCloseRepo.close(1, 1, cash, cash, List.of(), 0, null));
    }

    @Test void closeStoresDiscrepancy() throws Exception {
        long expected = 10_000_000L, actual = 9_998_000L;     // counted 2,000 kyat short
        long diff = actual - expected;                        // -2,000 kyat = -200,000 pya
        DailyCloseRepo.close(1, 1, expected, actual, List.of(), diff, "miscount");

        var c = Database().createStatement().executeQuery(
                "SELECT discrepancy_pya, reason FROM daily_closes WHERE branch_id=1");
        assertTrue(c.next());
        assertEquals(-2_000L, c.getLong(1));
        assertEquals("miscount", c.getString(2));
    }

    // tiny helper to reach the connection
    private java.sql.Connection Database() throws Exception {
        return com.agentledger.db.Database.get();
    }
}