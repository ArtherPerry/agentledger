package com.agentledger;

import com.agentledger.model.User;
import com.agentledger.service.Permissions;
import com.agentledger.service.Session;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PermissionsTest extends TestBase {

    private void actAs(String role) {
        Session.login(new User(99, 1, "Test " + role, "u_" + role, role));
        Session.setBranch(1, "ဆိုင်ခွဲ ၁");
    }

    // ---- reverse / correction ----
    @Test void reverse_ownerYes_managerYes_cashierNo() {
        actAs("owner");   assertTrue(Permissions.canReverse());
        actAs("manager"); assertTrue(Permissions.canReverse());
        actAs("cashier"); assertFalse(Permissions.canReverse());
    }

    // ---- fee rules: owner only ----
    @Test void editFeeRules_ownerOnly() {
        actAs("owner");   assertTrue(Permissions.canEditFeeRules());
        actAs("manager"); assertFalse(Permissions.canEditFeeRules());
        actAs("cashier"); assertFalse(Permissions.canEditFeeRules());
    }

    // ---- reports: owner + manager ----
    @Test void viewReports_ownerAndManager() {
        actAs("owner");   assertTrue(Permissions.canViewReports());
        actAs("manager"); assertTrue(Permissions.canViewReports());
        actAs("cashier"); assertFalse(Permissions.canViewReports());
    }

    // ---- settings: owner + manager ----
    @Test void openSettings_ownerAndManager() {
        actAs("owner");   assertTrue(Permissions.canOpenSettings());
        actAs("manager"); assertTrue(Permissions.canOpenSettings());
        actAs("cashier"); assertFalse(Permissions.canOpenSettings());
    }

    // ---- users: owner only ----
    @Test void manageUsers_ownerOnly() {
        actAs("owner");   assertTrue(Permissions.canManageUsers());
        actAs("manager"); assertFalse(Permissions.canManageUsers());
        actAs("cashier"); assertFalse(Permissions.canManageUsers());
    }

    // ---- branches: owner only ----
    @Test void manageBranches_ownerOnly() {
        actAs("owner");   assertTrue(Permissions.canManageBranches());
        actAs("manager"); assertFalse(Permissions.canManageBranches());
        actAs("cashier"); assertFalse(Permissions.canManageBranches());
    }

    // ---- close: owner + manager ----
    @Test void close_ownerAndManager_notCashier() {
        actAs("owner");   assertTrue(Permissions.canClose());
        actAs("manager"); assertTrue(Permissions.canClose());
        actAs("cashier"); assertFalse(Permissions.canClose());
    }

    // ---- reopen: owner only ----
    @Test void reopen_ownerOnly() {
        actAs("owner");   assertTrue(Permissions.canReopen());
        actAs("manager"); assertFalse(Permissions.canReopen());
        actAs("cashier"); assertFalse(Permissions.canReopen());
    }

    // ---- backup: owner + manager ----
    @Test void backup_ownerAndManager() {
        actAs("owner");   assertTrue(Permissions.canBackup());
        actAs("manager"); assertTrue(Permissions.canBackup());
        actAs("cashier"); assertFalse(Permissions.canBackup());
    }

    // ---- top-up: owner + manager ----
    @Test void topUp_ownerAndManager() {
        actAs("owner");   assertTrue(Permissions.canTopUp());
        actAs("manager"); assertTrue(Permissions.canTopUp());
        actAs("cashier"); assertFalse(Permissions.canTopUp());
    }
}