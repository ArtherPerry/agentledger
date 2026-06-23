package com.agentledger;

import com.agentledger.model.Account;
import com.agentledger.model.FeeResult;
import com.agentledger.model.ReportSummary;
import com.agentledger.model.TxnType;
import com.agentledger.repo.AccountRepo;
import com.agentledger.repo.ReportRepo;
import com.agentledger.repo.TxnTypeRepo;
import com.agentledger.service.FeeService;
import com.agentledger.service.LedgerService;
import com.agentledger.service.PostingRequest;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/** Batch D — reports reconcile with what was posted, and reversed transactions are
 *  handled consistently. Documents the reversed-txn behavior (Q2). */
class ReportReconciliationTest extends TestBase {

    private Account wave() {
        return AccountRepo.listForBranch(1).stream()
                .filter(a -> "Wave Main".equals(a.name())).findFirst().orElseThrow();
    }
    private TxnType type(String n) { return TxnTypeRepo.byName(1, n); }
    private String today() { return LocalDate.now().toString(); }

    private long post(TxnType t, Account acc, long amountPya) throws Exception {
        FeeResult fr = FeeService.compute(1, t.name(),
                acc.isDigital() ? acc.platform() : null, amountPya);
        PostingRequest r = new PostingRequest();
        r.type = t; r.account = acc; r.amountPya = amountPya;
        r.feePya = fr.feePya(); r.commissionPya = fr.commissionPya();
        return LedgerService.post(r);
    }

    @Test
    void summary_reconcilesWithPostedTransactions() throws Exception {
        Account wave = wave();
        post(type(TxnType.TOPUP_DIGITAL), wave, 10_000_000L);   // internal type, excluded from report

        long fee1, comm1, fee2, comm2;
        { FeeResult fr = FeeService.compute(1, TxnType.PASSWORD_WITHDRAW, wave.platform(), 1_000_000L);
            fee1 = fr.feePya(); comm1 = fr.commissionPya(); }
        { FeeResult fr = FeeService.compute(1, TxnType.PASSWORD_WITHDRAW, wave.platform(), 2_000_000L);
            fee2 = fr.feePya(); comm2 = fr.commissionPya(); }
        post(type(TxnType.PASSWORD_WITHDRAW), wave, 1_000_000L);
        post(type(TxnType.PASSWORD_WITHDRAW), wave, 2_000_000L);

        ReportSummary s = ReportRepo.summary(1, today(), today());

        assertEquals(2, s.txnCount(), "two fee-bearing transactions counted");
        assertEquals(fee1 + fee2, s.totalFeePya(), "fees reconcile");
        assertEquals(comm1 + comm2, s.totalCommissionPya(), "commission reconciles");
        assertEquals(3_000_000L, s.totalAmountPya(), "amount = 1,000,000 + 2,000,000");
    }

    @Test
    void reversedTransaction_feesNetToZero_butCountBehavior_characterization() throws Exception {
        Account wave = wave();
        post(type(TxnType.TOPUP_DIGITAL), wave, 10_000_000L);

        long id = post(type(TxnType.PASSWORD_WITHDRAW), wave, 1_000_000L);

        ReportSummary before = ReportRepo.summary(1, today(), today());

        LedgerService.reverse(id);

        ReportSummary after = ReportRepo.summary(1, today(), today());

        assertEquals(0L, after.totalFeePya(), "reversed txn fee nets to zero in report");
        assertEquals(0L, after.totalCommissionPya(), "reversed txn commission nets to zero");
        assertEquals(0L, after.totalAmountPya(), "reversed txn amount nets to zero");

        System.out.println("[CHAR] report txnCount before reverse=" + before.txnCount()
                + " after reverse=" + after.txnCount());
        assertTrue(after.txnCount() == 0 || after.txnCount() == 1,
                "documents whether a reversed txn still counts in the report");
    }

    @Test
    void byPlatform_groupsCorrectly() throws Exception {
        Account wave = wave();
        post(type(TxnType.TOPUP_DIGITAL), wave, 10_000_000L);
        post(type(TxnType.PASSWORD_WITHDRAW), wave, 1_000_000L);

        var rows = ReportRepo.byPlatform(1, today(), today());
        boolean hasWave = rows.stream().anyMatch(r -> "Wave Money".equals(r.platform()));
        assertTrue(hasWave, "platform breakdown should include Wave Money");
    }

    @Test
    void emptyRange_returnsZeroSummary() {
        ReportSummary s = ReportRepo.summary(1, "2000-01-01", "2000-01-02");
        assertEquals(0, s.txnCount());
        assertEquals(0L, s.totalFeePya());
        assertEquals(0L, s.totalCommissionPya());
    }
}