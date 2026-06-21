package com.agentledger.service;

import com.agentledger.model.User;

public final class Permissions {

    private Permissions() {}

    public static boolean canViewReports() { return ownerOrManager(); }
    public static boolean canOpenSettings() { return ownerOrManager(); }

    private static boolean ownerOrManager() {
        User u = Session.user();
        return u != null && (u.isOwner() || u.isManager());
    }
    public static boolean canReverse() {
        User u = Session.user();
        return u != null && (u.isOwner() || u.isManager());
    }
    public static boolean canTopUp() {
        User u = Session.user();
        return u != null && (u.isOwner() || u.isManager());
    }
    public static boolean canClose() {
        User u = Session.user();
        return u != null && (u.isOwner() || u.isManager());
    }

    public static boolean canReopen() {
        User u = Session.user();
        return u != null && u.isOwner();
    }
    public static boolean canManageDebts() {
        User u = Session.user();
        return u != null;   // all roles may record debts & repayments
    }
    public static boolean canEditFeeRules() {
        User u = Session.user();
        return u != null && u.isOwner();   // Owner only, per the matrix
    }
    public static boolean canManageUsers() {
        User u = Session.user();
        return u != null && u.isOwner();
    }
    public static boolean canManageBranches() {
        User u = Session.user();
        return u != null && u.isOwner();
    }
    public static boolean canViewActivityLog() {
        User u = Session.user();
        return u != null && (u.isOwner() || u.isManager());
    }
    public static boolean canBackup() {
        User u = Session.user();
        return u != null && (u.isOwner() || u.isManager());
    }
}