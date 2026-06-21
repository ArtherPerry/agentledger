package com.agentledger;

import com.agentledger.model.Account;
import com.agentledger.model.TxnType;
import com.agentledger.repo.AccountRepo;
import com.agentledger.repo.LedgerRepo;
import com.agentledger.repo.TxnTypeRepo;
import com.agentledger.service.LedgerService;
import com.agentledger.service.PostingRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LedgerServiceTest extends TestBase {

    private Account wave() {
        return AccountRepo.listForBranch(1).stream()
                .filter(a -> "Wave Main".equals(a.name())).findFirst().orElseThrow();
    }
    private Account cash() {
        return AccountRepo.listForBranch(1).stream()
                .filter(a -> !a.isDigital()).findFirst().orElseThrow();
    }
    private TxnType type(String name) {
        // byName ignores the internal-exclusion, so it can fetch any type
        return TxnTypeRepo.byName(1, name);
    }

    @Test void posting_movesCashAndDigitalInOppositeDirections() throws Exception {
        Account wave = wave();
        PostingRequest r = new PostingRequest();
        r.type = type("Password ဖြင့် ထုတ်ယူ");   // cash out, digital in
        r.account = wave;
        r.amountPya = 30_000_000L;                   // 300,000 kyat

        LedgerService.post(r);

        assertEquals(30_000_000L, LedgerRepo.accountDigitalPya(wave.id())); // digital +
        assertEquals(-30_000_000L, LedgerRepo.branchCashPya(1));            // cash -
    }

    @Test void reversal_returnsBalancesToZero() throws Exception {
        Account wave = wave();
        PostingRequest r = new PostingRequest();
        r.type = type("Password ဖြင့် ထုတ်ယူ");
        r.account = wave;
        r.amountPya = 30_000_000L;
        long id = LedgerService.post(r);

        LedgerService.reverse(id);

        assertEquals(0L, LedgerRepo.accountDigitalPya(wave.id()));
        assertEquals(0L, LedgerRepo.branchCashPya(1));
    }

    @Test void doubleReversal_isBlocked() throws Exception {
        PostingRequest r = new PostingRequest();
        r.type = type("Password ဖြင့် ထုတ်ယူ");
        r.account = wave();
        r.amountPya = 10_000_000L;
        long id = LedgerService.post(r);

        LedgerService.reverse(id);
        assertThrows(Exception.class, () -> LedgerService.reverse(id));   // already reversed
    }

    @Test void walletToWallet_movesBetweenTwoAccounts() throws Exception {
        Account wave = wave();
        Account kbz = AccountRepo.listForBranch(1).stream()
                .filter(a -> "KBZPay".equals(a.name())).findFirst().orElseThrow();

        // first load Wave so it has balance to move
        PostingRequest load = new PostingRequest();
        load.type = type("ဒစ်ဂျစ်တယ် ဖြည့်သွင်း"); load.account = wave; load.amountPya = 50_000_000L;
        LedgerService.post(load);

        PostingRequest move = new PostingRequest();
        move.type = type("အကောင့်မှ အကောင့်");      // digital out of source
        move.account = wave;
        move.toAccount = kbz;
        move.amountPya = 20_000_000L;
        LedgerService.post(move);

        assertEquals(30_000_000L, LedgerRepo.accountDigitalPya(wave.id())); // 50 - 20
        assertEquals(20_000_000L, LedgerRepo.accountDigitalPya(kbz.id()));  // +20
        assertEquals(0L, LedgerRepo.branchCashPya(1));                      // cash untouched
    }

    @Test void zeroAmount_isRejected() {
        PostingRequest r = new PostingRequest();
        r.type = type("Password ဖြင့် ထုတ်ယူ");
        r.account = wave();
        r.amountPya = 0L;
        assertThrows(Exception.class, () -> LedgerService.post(r));
    }
}