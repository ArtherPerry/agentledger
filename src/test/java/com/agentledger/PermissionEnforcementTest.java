package com.agentledger;

import com.agentledger.model.Account;
import com.agentledger.model.User;
import com.agentledger.repo.AccountRepo;
import com.agentledger.repo.TxnTypeRepo;
import com.agentledger.service.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PermissionEnforcementTest extends TestBase {

    private Account wave() {
        return AccountRepo.listForBranch(1).stream()
                .filter(a -> "Wave Main".equals(a.name())).findFirst().orElseThrow();
    }

    private void actAs(String role) {
        Session.login(new User(99, 1, "Test " + role, "u_" + role, role));
        Session.setBranch(1, "ဆိုင်ခွဲ ၁");
    }

    @Test void cashierCannotReverse_evenViaService() throws Exception {
        // owner posts an entry
        PostingRequest r = new PostingRequest();
        r.type = TxnTypeRepo.byName(1, "Password ဖြင့် ထုတ်ယူ");
        r.account = wave();
        r.amountPya = 10_000_000L;
        long id = LedgerService.post(r);

        // cashier tries to reverse it
        actAs("cashier");
        assertThrows(IllegalStateException.class, () -> LedgerService.reverse(id));
    }

    @Test void cashierCannotTopUp_evenViaService() {
        actAs("cashier");
        assertThrows(IllegalStateException.class,
                () -> LedgerService.topUp(wave(), 5_000_000L, true, null));
    }

    @Test void managerCannotEditFeeRules_predicateGuards() {
        actAs("manager");
        assertFalse(Permissions.canEditFeeRules());  // the FeesController add/edit is gated on this
    }
}