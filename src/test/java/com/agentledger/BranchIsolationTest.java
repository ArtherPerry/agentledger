package com.agentledger;

import com.agentledger.model.Account;
import com.agentledger.model.User;
import com.agentledger.repo.AccountRepo;
import com.agentledger.repo.LedgerRepo;
import com.agentledger.repo.TxnTypeRepo;
import com.agentledger.service.LedgerService;
import com.agentledger.service.PostingRequest;
import com.agentledger.service.Session;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BranchIsolationTest extends TestBase {

    @Test void postingInBranch1_doesNotAffectBranch2() throws Exception {
        // baseline: branch 2 cash
        long b2CashBefore = LedgerRepo.branchCashPya(2);

        // post in branch 1 (session is branch 1 from TestBase)
        Account wave1 = AccountRepo.listForBranch(1).stream()
                .filter(a -> "Wave Main".equals(a.name())).findFirst().orElseThrow();
        PostingRequest r = new PostingRequest();
        r.type = TxnTypeRepo.byName(1, "Password ဖြင့် ထုတ်ယူ");
        r.account = wave1;
        r.amountPya = 25_000_000L;
        LedgerService.post(r);

        // branch 1 changed
        assertEquals(-25_000_000L, LedgerRepo.branchCashPya(1));
        // branch 2 untouched
        assertEquals(b2CashBefore, LedgerRepo.branchCashPya(2));
    }

    @Test void accountListsAreSeparatePerBranch() {
        var b1 = AccountRepo.listForBranch(1);
        var b2 = AccountRepo.listForBranch(2);
        // same names seeded, but different account ids (no overlap)
        for (Account a1 : b1)
            for (Account a2 : b2)
                assertNotEquals(a1.id(), a2.id(), "accounts must not be shared across branches");
    }
}