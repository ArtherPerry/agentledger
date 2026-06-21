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

class CustomTxnTypeTest extends TestBase {

    private Account wave() {
        return AccountRepo.listForBranch(1).stream()
                .filter(a -> "Wave Main".equals(a.name())).findFirst().orElseThrow();
    }

    @Test
    void customType_movesMoneySameAsItsEffectTwin() throws Exception {
        // Built-in PASSWORD_WITHDRAW is (cash out, digital in). A custom type with the
        // same effects must move balances identically.
        Account wave = wave();
        long amount = 30_000_000L;

        // custom type with same effects as PASSWORD_WITHDRAW: out / in
        int customId = TxnTypeRepo.insertCustom(1, "ကိုယ်ပိုင် ထုတ်ယူ", "out", "in");
        TxnType custom = TxnTypeRepo.byId(customId);

        PostingRequest r = new PostingRequest();
        r.type = custom; r.account = wave; r.amountPya = amount;
        LedgerService.post(r);

        // identical direction to the built-in withdraw: digital +, cash -
        assertEquals(amount, LedgerRepo.accountDigitalPya(wave.id()), "digital should increase");
        assertEquals(-amount, LedgerRepo.branchCashPya(1), "cash should decrease");
    }

    @Test
    void customType_oppositeDirection_movesCorrectly() throws Exception {
        // custom type (cash in, digital out) — opposite of withdraw
        Account wave = wave();
        long amount = 10_000_000L;

        int customId = TxnTypeRepo.insertCustom(1, "ကိုယ်ပိုင် သွင်း", "in", "out");
        TxnType custom = TxnTypeRepo.byId(customId);

        PostingRequest r = new PostingRequest();
        r.type = custom; r.account = wave; r.amountPya = amount;
        LedgerService.post(r);

        assertEquals(-amount, LedgerRepo.accountDigitalPya(wave.id()), "digital should decrease");
        assertEquals(amount, LedgerRepo.branchCashPya(1), "cash should increase");
    }

    @Test
    void builtinCannotBeDeleted_customWithoutHistoryCan() throws Exception {
        TxnType builtin = TxnTypeRepo.byName(1, TxnType.PASSWORD_WITHDRAW);
        assertTrue(TxnTypeRepo.isBuiltin(builtin.id()), "seeded type should be builtin");
        assertThrows(IllegalStateException.class, () -> TxnTypeRepo.deleteCustom(builtin.id()),
                "builtin types must not be deletable");

        int customId = TxnTypeRepo.insertCustom(1, "ယာယီ", "out", "in");
        assertFalse(TxnTypeRepo.isBuiltin(customId), "custom type is not builtin");
        TxnTypeRepo.deleteCustom(customId);   // no history -> deletable
        assertNull(TxnTypeRepo.byId(customId), "custom type with no history should be deleted");
    }

    @Test
    void customWithHistory_cannotBeDeleted_onlyDeactivated() throws Exception {
        Account wave = wave();
        int customId = TxnTypeRepo.insertCustom(1, "သုံးပြီးသား", "out", "in");
        TxnType custom = TxnTypeRepo.byId(customId);

        PostingRequest r = new PostingRequest();
        r.type = custom; r.account = wave; r.amountPya = 5_000_000L;
        LedgerService.post(r);

        assertTrue(TxnTypeRepo.hasLedgerEntries(customId), "type now has history");
        assertThrows(IllegalStateException.class, () -> TxnTypeRepo.deleteCustom(customId),
                "type with history must not be hard-deleted");

        TxnTypeRepo.setActive(customId, false);
        boolean stillListed = TxnTypeRepo.listForBranch(1).stream().anyMatch(t -> t.id() == customId);
        assertFalse(stillListed, "deactivated type should not appear in the transaction dropdown");
    }

    @Test
    void activeCustomType_appearsInDropdown() throws Exception {
        int customId = TxnTypeRepo.insertCustom(1, "အသစ်", "in", "out");
        boolean listed = TxnTypeRepo.listForBranch(1).stream().anyMatch(t -> t.id() == customId);
        assertTrue(listed, "active custom type should appear in the transaction dropdown");
    }
}