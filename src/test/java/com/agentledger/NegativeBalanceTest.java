package com.agentledger;

import com.agentledger.model.Account;
import com.agentledger.model.TxnType;
import com.agentledger.repo.AccountRepo;
import com.agentledger.repo.LedgerRepo;
import com.agentledger.repo.SettingsRepo;
import com.agentledger.repo.TxnTypeRepo;
import com.agentledger.service.AccountService;
import com.agentledger.service.BalancePolicy;
import com.agentledger.service.LedgerService;
import com.agentledger.service.PostingRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Negative-balance policy: default WARN, BLOCK enforces at the service, WARN/ALLOW don't. */
class NegativeBalanceTest extends TestBase {

    private Account wave() {
        return AccountRepo.listForBranch(1).stream()
                .filter(a -> "Wave Main".equals(a.name())).findFirst().orElseThrow();
    }
    private void setPolicy(String p) throws Exception {
        SettingsRepo.set(1, SettingsRepo.KEY_BALANCE_POLICY, p);
    }
    /** A custom type that only takes digital OUT, so we can overdraw a wallet directly. */
    private TxnType outType() throws Exception {
        return TxnTypeRepo.byId(TxnTypeRepo.insertCustom(1, "Out Only", "none", "out"));
    }
    private PostingRequest req(TxnType t, Account a, long amt) {
        PostingRequest r = new PostingRequest();
        r.type = t; r.account = a; r.amountPya = amt;
        return r;
    }

    @Test void defaultPolicy_isWarn() {
        assertEquals(BalancePolicy.WARN, BalancePolicy.of(1), "default policy is WARN");
    }

    @Test void block_overdrawDigital_throws() throws Exception {
        setPolicy("block");
        assertThrows(IllegalStateException.class,
                () -> LedgerService.post(req(outType(), wave(), 1_000_000L)),
                "BLOCK must reject a posting that drives the wallet negative");
    }

    @Test void block_withinBalance_succeeds() throws Exception {
        LedgerService.topUp(wave(), 5_000_000L, true, null);   // fund the wallet first
        setPolicy("block");
        assertDoesNotThrow(() -> LedgerService.post(req(outType(), wave(), 3_000_000L)));
        assertEquals(2_000_000L, LedgerRepo.accountDigitalPya(wave().id()), "5M - 3M = 2M");
    }

    @Test void warn_overdraw_doesNotThrowAtService() throws Exception {
        setPolicy("warn");   // service must NOT block; the UI shows the prompt
        assertDoesNotThrow(() -> LedgerService.post(req(outType(), wave(), 1_000_000L)));
        assertEquals(-1_000_000L, LedgerRepo.accountDigitalPya(wave().id()),
                "WARN lets it through (negative allowed at service layer)");
    }

    @Test void projectedShortfall_flagsOverdraw_thenClearsWhenFunded() throws Exception {
        PostingRequest r = req(outType(), wave(), 1_000_000L);
        assertNotNull(LedgerService.projectedShortfall(r), "empty wallet overdraw is flagged");
        LedgerService.topUp(wave(), 1_000_000L, true, null);
        assertNull(LedgerService.projectedShortfall(r), "exactly funded -> no shortfall");
    }

    @Test void block_coversWalletToWalletSource() throws Exception {
        setPolicy("block");
        AccountService.create("Dest Wallet", "KBZPay", true);
        Account dest = AccountRepo.listForBranch(1).stream()
                .filter(a -> "Dest Wallet".equals(a.name())).findFirst().orElseThrow();
        PostingRequest r = req(TxnTypeRepo.byName(1, TxnType.WALLET_TO_WALLET), wave(), 1_000_000L);
        r.toAccount = dest;
        assertThrows(IllegalStateException.class, () -> LedgerService.post(r),
                "BLOCK must reject a w2w that overdraws the source wallet");
    }
}