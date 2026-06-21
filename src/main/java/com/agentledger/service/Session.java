package com.agentledger.service;

import com.agentledger.model.User;

/** Single active user per laptop, so a simple static holder is fine. */
public final class Session {
    private static User user;
    private static int branchId = -1;
    private static String branchName = "";

    private Session() {}

    public static void login(User u) { user = u; }

    /** Full reset: clears user AND branch (used by "switch branch"). */
    public static void logout() { user = null; branchId = -1; branchName = ""; }

    /** Logout but keep the current branch (used by "logout" — returns to this branch's login). */
    public static void logoutKeepBranch() { user = null; }

    public static User user() { return user; }
    public static boolean loggedIn() { return user != null; }

    public static void setBranch(int id, String name) { branchId = id; branchName = name; }
    public static int branchId() { return branchId; }
    public static String branchName() { return branchName; }
}