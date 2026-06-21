package com.agentledger;

import com.agentledger.repo.AccountRepo;
import com.agentledger.service.AccountService;
import com.agentledger.service.Session;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AccountServiceTest extends TestBase {

    @Test void create_addsDigitalAccount() throws Exception {
        int before = AccountRepo.listForBranch(1).size();
        AccountService.create("My CB Pay", "CB Pay", true);   // a platform NOT in the hardcoded list
        assertEquals(before + 1, AccountRepo.listForBranch(1).size());
        assertTrue(AccountRepo.listForBranch(1).stream()
                .anyMatch(a -> "My CB Pay".equals(a.name()) && "CB Pay".equals(a.platform())));
    }

    @Test void create_digitalRequiresPlatform() {
        assertThrows(IllegalArgumentException.class,
                () -> AccountService.create("No Platform", "", true));
    }

    @Test void cannotDeactivateCashAccount() {
        // the seeded cash account id for branch 1
        var cash = AccountRepo.listForBranch(1).stream().filter(a -> !a.isDigital()).findFirst().orElseThrow();
        assertThrows(IllegalStateException.class, () -> AccountService.deactivate(cash.id()));
    }

    @Test void deactivate_hidesDigitalFromActiveList() throws Exception {
        AccountService.create("Temp Wallet", "Wave Money", true);
        var w = AccountRepo.listForBranch(1).stream()
                .filter(a -> "Temp Wallet".equals(a.name())).findFirst().orElseThrow();
        AccountService.deactivate(w.id());
        assertFalse(AccountRepo.listForBranch(1).stream().anyMatch(a -> a.id() == w.id()),
                "deactivated account should not appear in the active list");
    }
}