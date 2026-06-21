package com.agentledger;

import com.agentledger.model.User;
import com.agentledger.service.AuthService;
import com.agentledger.service.UserService;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class AuthServiceTest extends TestBase {

    @Test void correctPassword_logsIn() {
        Optional<User> u = AuthService.login(1, "owner1", "owner123");
        assertTrue(u.isPresent());
        assertEquals("owner", u.get().role());
        assertEquals(1, u.get().branchId());
    }

    @Test void wrongPassword_fails() {
        assertTrue(AuthService.login(1, "owner1", "wrong").isEmpty());
    }

    @Test void unknownUser_fails() {
        assertTrue(AuthService.login(1, "ghost", "owner123").isEmpty());
    }

    @Test void usernameIsBranchScoped() {
        // owner1 exists in branch 1 but not branch 2
        assertTrue(AuthService.login(1, "owner1", "owner123").isPresent());
        assertTrue(AuthService.login(2, "owner1", "owner123").isEmpty());
    }

    @Test void inactiveUser_cannotLogIn() throws Exception {
        // create a cashier, then deactivate, then try to log in
        UserService.create("Test Cashier", "cash_x", "cash123", "cashier");
        var rows = com.agentledger.repo.UserRepo.listForBranch(1);
        var cashier = rows.stream().filter(r -> r.username().equals("cash_x")).findFirst().orElseThrow();
        UserService.update(cashier, "Test Cashier", "cashier", false, null); // active=false

        assertTrue(AuthService.login(1, "cash_x", "cash123").isEmpty());
    }
}