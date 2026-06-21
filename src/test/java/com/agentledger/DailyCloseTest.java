package com.agentledger;

import com.agentledger.repo.AccountRepo;
import com.agentledger.repo.DailyCloseRepo;
import com.agentledger.repo.LedgerRepo;
import com.agentledger.service.LedgerService;
import com.agentledger.service.PostingRequest;
import com.agentledger.repo.TxnTypeRepo;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DailyCloseTest extends TestBase {

    @Test void afterClose_postingIsBlocked() throws Exception {
        // close today with no discrepancy
        long cash = LedgerRepo.branchCashPya(1);
        DailyCloseRepo.close(1, 1, cash, cash, List.of(), 0, null);

        assertTrue(DailyCloseRepo.isTodayClosed(1));

        PostingRequest r = new PostingRequest();
        r.type = TxnTypeRepo.byName(1, "Password ဖြင့် ထုတ်ယူ");
        r.account = AccountRepo.listForBranch(1).stream().filter(a -> a.isDigital()).findFirst().orElseThrow();
        r.amountPya = 10_000_000L;

        assertThrows(Exception.class, () -> LedgerService.post(r));  // hard close blocks it
    }

    @Test void reopen_allowsPostingAgain() throws Exception {
        long cash = LedgerRepo.branchCashPya(1);
        DailyCloseRepo.close(1, 1, cash, cash, List.of(), 0, null);
        DailyCloseRepo.reopenToday(1, 1);

        assertFalse(DailyCloseRepo.isTodayClosed(1));
    }
}