package com.agentledger;

import com.agentledger.model.Account;
import com.agentledger.model.Debt;
import com.agentledger.model.TxnType;
import com.agentledger.repo.AccountRepo;
import com.agentledger.repo.DebtRepo;
import com.agentledger.repo.DailyCloseRepo;
import com.agentledger.repo.LedgerRepo;
import com.agentledger.repo.TxnTypeRepo;
import com.agentledger.service.DebtService;
import com.agentledger.model.FeeResult;
import com.agentledger.service.FeeService;
import com.agentledger.service.LedgerService;
import com.agentledger.service.PostingRequest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The whole business cycle as one automated check: cashier posts a day of work,
 * manager reverses + handles a debt, then the day is closed. Every balance is
 * asserted against hand-computed values. If any service miscomputes money, this fails.
 *
 * Amounts are in pya (1 kyat = 100 pya). Uses the seeded fixture (branch 1, Wave Main
 * account with a Wave Money fee rule for PASSWORD_WITHDRAW).
 */
class FullCycleTest extends TestBase {

    private Account wave() {
        return AccountRepo.listForBranch(1).stream()
                .filter(a -> "Wave Main".equals(a.name())).findFirst().orElseThrow();
    }

    private TxnType type(String name) { return TxnTypeRepo.byName(1, name); }

    /** Post helper using the rule-computed fee/commission (what the UI would default to). */
    private long post(TxnType t, Account acc, long amountPya) throws Exception {
        FeeResult fr = FeeService.compute(1, t.name(),
                acc.isDigital() ? acc.platform() : null, amountPya);
        PostingRequest r = new PostingRequest();
        r.type = t; r.account = acc; r.amountPya = amountPya;
        r.feePya = fr.feePya(); r.commissionPya = fr.commissionPya();
        return LedgerService.post(r);
    }

    @Test
    void fullBusinessCycle_balancesReconcile() throws Exception {
        Account wave = wave();

        // ===== PHASE 2 — cashier's day =====
        // Starting balances are 0 (fresh seeded branch with no ledger entries).
        assertEquals(0L, LedgerRepo.branchCashPya(1));
        assertEquals(0L, LedgerRepo.accountDigitalPya(wave.id()));

        // T1 — top up Wave digital +1,000,000 pya
        post(type(TxnType.TOPUP_DIGITAL), wave, 1_000_000L);
        assertEquals(1_000_000L, LedgerRepo.accountDigitalPya(wave.id()), "after T1 Wave digital");

        // T2 — top up cash +500,000 pya
        // TOPUP_CASH is a cash-in / digital-none type; post it against the wave account
        // (account is irrelevant for a cash-only leg, but post() needs one).
        post(type(TxnType.TOPUP_CASH), wave, 500_000L);
        assertEquals(500_000L, LedgerRepo.branchCashPya(1), "after T2 cash");

        // T3 — Password withdraw on Wave, 100,000 pya (cash out, digital in)
        long feeT3, commT3;
        {
            FeeResult fr = FeeService.compute(1, TxnType.PASSWORD_WITHDRAW, wave.platform(), 100_000L);
            feeT3 = fr.feePya(); commT3 = fr.commissionPya();
        }
        post(type(TxnType.PASSWORD_WITHDRAW), wave, 100_000L);
        assertEquals(500_000L - 100_000L, LedgerRepo.branchCashPya(1), "after T3 cash");
        assertEquals(1_000_000L + 100_000L, LedgerRepo.accountDigitalPya(wave.id()), "after T3 Wave");

        // T4 — Password withdraw on Wave, 200,000 pya
        long t4Id = post(type(TxnType.PASSWORD_WITHDRAW), wave, 200_000L);
        assertEquals(400_000L - 200_000L, LedgerRepo.branchCashPya(1), "after T4 cash");      // 200,000
        assertEquals(1_100_000L + 200_000L, LedgerRepo.accountDigitalPya(wave.id()), "after T4 Wave"); // 1,300,000

        // ===== PHASE 3 — manager operations =====
        // Reverse T4 → balances return to the post-T3 state
        LedgerService.reverse(t4Id);
        assertEquals(400_000L, LedgerRepo.branchCashPya(1), "after reverse cash");
        assertEquals(1_100_000L, LedgerRepo.accountDigitalPya(wave.id()), "after reverse Wave");

        // Can't double-reverse
        assertThrows(Exception.class, () -> LedgerService.reverse(t4Id));

        // Debt — receivable 50,000 pya, partial 20,000 then full 30,000
        long cashBeforeDebt = LedgerRepo.branchCashPya(1);            // 400,000
        DebtService.create("receivable", "ဖောက်သည်", "customer", null, 50_000L, null);
        Debt d = DebtRepo.list(1, "receivable", false).get(0);
        assertEquals(50_000L, d.remainingPya());

        DebtService.repay(d.id(), 20_000L);                          // partial
        Debt afterPartial = DebtRepo.list(1, "receivable", false).get(0);
        assertEquals(30_000L, afterPartial.remainingPya(), "remaining after partial");
        assertEquals(cashBeforeDebt + 20_000L, LedgerRepo.branchCashPya(1), "cash after partial repay");

        DebtService.repay(afterPartial.id(), 30_000L);               // full
        assertTrue(DebtRepo.list(1, "receivable", false).isEmpty(), "debt settled");
        assertEquals(cashBeforeDebt + 50_000L, LedgerRepo.branchCashPya(1), "cash after full repay"); // 450,000

        // ===== PHASE 4 — daily close =====
        long expectedCash = LedgerRepo.branchCashPya(1);
        assertEquals(450_000L, expectedCash, "expected cash at close");

        DailyCloseRepo.close(1, 1, expectedCash, expectedCash, List.of(), 0, null);
        assertTrue(DailyCloseRepo.isTodayClosed(1), "day should be closed");

        // Posting after close must be blocked
        assertThrows(Exception.class, () -> post(type(TxnType.TOPUP_CASH), wave, 10_000L),
                "posting into a closed day must be blocked");
    }
}