package com.agentledger;

import com.agentledger.model.Account;
import com.agentledger.model.FeeResult;
import com.agentledger.model.TxnType;
import com.agentledger.repo.AccountRepo;
import com.agentledger.repo.LedgerRepo;
import com.agentledger.repo.TxnTypeRepo;
import com.agentledger.service.FeeService;
import com.agentledger.service.LedgerService;
import com.agentledger.service.PostingRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Batch A — money-movement edge cases beyond the basic posting tests. */
class MoneyEdgeCasesTest extends TestBase {

    private Account wave() {
        return AccountRepo.listForBranch(1).stream()
                .filter(a -> "Wave Main".equals(a.name())).findFirst().orElseThrow();
    }
    private Account kbz() {
        return AccountRepo.listForBranch(1).stream()
                .filter(a -> "KBZPay".equals(a.name())).findFirst().orElseThrow();
    }
    private TxnType type(String name) { return TxnTypeRepo.byName(1, name); }

    private long postSimple(TxnType t, Account acc, long amountPya) throws Exception {
        FeeResult fr = FeeService.compute(1, t.name(),
                acc.isDigital() ? acc.platform() : null, amountPya);
        PostingRequest r = new PostingRequest();
        r.type = t; r.account = acc; r.amountPya = amountPya;
        r.feePya = fr.feePya(); r.commissionPya = fr.commissionPya();
        return LedgerService.post(r);
    }

    // ---- wallet-to-wallet ----

    @Test
    void walletToWallet_movesDigitalBetweenAccounts_cashUntouched() throws Exception {
        Account wave = wave(), kbz = kbz();
        // load Wave first
        postSimple(type(TxnType.TOPUP_DIGITAL), wave, 5_000_000L);
        long cashBefore = LedgerRepo.branchCashPya(1);

        PostingRequest move = new PostingRequest();
        move.type = type(TxnType.WALLET_TO_WALLET);
        move.account = wave;       // source
        move.toAccount = kbz;      // destination
        move.amountPya = 2_000_000L;
        LedgerService.post(move);

        assertEquals(3_000_000L, LedgerRepo.accountDigitalPya(wave.id()), "source down");
        assertEquals(2_000_000L, LedgerRepo.accountDigitalPya(kbz.id()), "dest up");
        assertEquals(cashBefore, LedgerRepo.branchCashPya(1), "cash untouched by wallet-to-wallet");
    }

    @Test
    void reverseWalletToWallet_unwindsBothLegs() throws Exception {
        Account wave = wave(), kbz = kbz();
        postSimple(type(TxnType.TOPUP_DIGITAL), wave, 5_000_000L);

        PostingRequest move = new PostingRequest();
        move.type = type(TxnType.WALLET_TO_WALLET);
        move.account = wave; move.toAccount = kbz; move.amountPya = 2_000_000L;
        long id = LedgerService.post(move);

        LedgerService.reverse(id);

        assertEquals(5_000_000L, LedgerRepo.accountDigitalPya(wave.id()), "source restored");
        assertEquals(0L, LedgerRepo.accountDigitalPya(kbz.id()), "dest restored");
    }

    // ---- fee override ----

    @Test
    void feeOverride_storesOverriddenFee_notComputed() throws Exception {
        Account wave = wave();
        postSimple(type(TxnType.TOPUP_DIGITAL), wave, 5_000_000L);

        // compute the rule fee, then deliberately override with a different value
        FeeResult fr = FeeService.compute(1, TxnType.PASSWORD_WITHDRAW, wave.platform(), 1_000_000L);
        long overriddenFee = fr.feePya() + 12_345L;   // not the computed amount

        PostingRequest r = new PostingRequest();
        r.type = type(TxnType.PASSWORD_WITHDRAW);
        r.account = wave; r.amountPya = 1_000_000L;
        r.feePya = overriddenFee; r.commissionPya = fr.commissionPya();
        r.feeOverridden = true;
        long id = LedgerService.post(r);

        // read back the stored fee for this entry
        long storedFee;
        var c = com.agentledger.db.Database.get();
        try (var ps = c.prepareStatement("SELECT fee_pya FROM ledger_entries WHERE id=?")) {
            ps.setLong(1, id);
            try (var rs = ps.executeQuery()) { rs.next(); storedFee = rs.getLong(1); }
        }
        assertEquals(overriddenFee, storedFee, "overridden fee must be the one stored");
    }

    // ---- post / reverse / re-post consistency ----

    @Test
    void postReverseRepost_leavesConsistentBalances() throws Exception {
        Account wave = wave();
        postSimple(type(TxnType.TOPUP_DIGITAL), wave, 5_000_000L);
        long baseDigital = LedgerRepo.accountDigitalPya(wave.id());
        long baseCash = LedgerRepo.branchCashPya(1);

        long id = postSimple(type(TxnType.PASSWORD_WITHDRAW), wave, 1_000_000L);
        LedgerService.reverse(id);
        // after reverse, back to base
        assertEquals(baseDigital, LedgerRepo.accountDigitalPya(wave.id()));
        assertEquals(baseCash, LedgerRepo.branchCashPya(1));

        // re-post the same thing
        postSimple(type(TxnType.PASSWORD_WITHDRAW), wave, 1_000_000L);
        assertEquals(baseDigital + 1_000_000L, LedgerRepo.accountDigitalPya(wave.id()));
        assertEquals(baseCash - 1_000_000L, LedgerRepo.branchCashPya(1));
    }

    // ---- CHARACTERIZATION: insufficient balance (behavior unknown — documents current) ----

    @Test
    void withdrawMoreDigitalThanHeld_currentBehavior() throws Exception {
        Account wave = wave();
        postSimple(type(TxnType.TOPUP_DIGITAL), wave, 100_000L);   // only 100,000 in Wave

        PostingRequest r = new PostingRequest();
        r.type = type(TxnType.CASH_TO_ACCOUNT);   // cash in, digital OUT — pulls digital down
        r.account = wave; r.amountPya = 500_000L;  // more than held

        boolean threw = false;
        try { LedgerService.post(r); }
        catch (Exception e) { threw = true; }

        long digitalAfter = LedgerRepo.accountDigitalPya(wave.id());
        // This test DOCUMENTS behavior — it does not assert a policy.
        System.out.println("[CHAR] over-withdraw threw=" + threw + " digitalAfter=" + digitalAfter);
        // One of these is true; we record which so you can decide if it's desired:
        assertTrue(threw || digitalAfter < 0 || digitalAfter == 100_000L,
                "documents: either blocked (threw), or allowed-negative, or rejected-no-change");
    }
}