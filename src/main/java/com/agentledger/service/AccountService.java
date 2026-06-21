package com.agentledger.service;

import com.agentledger.i18n.I18n;
import com.agentledger.repo.AccountRepo;

public final class AccountService {
    private AccountService() {}

    public static void create(String name, String platform, boolean digital) throws Exception {
        if (!Permissions.canTopUp()) throw new IllegalStateException(I18n.t("error.noPermission"));
        if (name == null || name.isBlank()) throw new IllegalArgumentException(I18n.t("error.nameRequired"));
        if (digital && (platform == null || platform.isBlank()))
            throw new IllegalArgumentException(I18n.t("error.platformRequired"));

        AccountRepo.insert(Session.branchId(), name.trim(),
                digital ? platform.trim() : null, digital ? "digital" : "cash");

        ActivityLog.record(com.agentledger.db.Database.get(), Session.branchId(), Session.user().id(),
                "ACCOUNT_CREATE", name + (digital ? " / " + platform : " (cash)"));
    }

    public static void rename(int accountId, String name, String platform) throws Exception {
        if (!Permissions.canTopUp()) throw new IllegalStateException(I18n.t("error.noPermission"));
        if (name == null || name.isBlank()) throw new IllegalArgumentException(I18n.t("error.nameRequired"));

        boolean digital = "digital".equals(AccountRepo.acctType(accountId));
        if (digital && (platform == null || platform.isBlank()))
            throw new IllegalArgumentException(I18n.t("error.platformRequired"));

        AccountRepo.update(accountId, name, digital ? platform : null);
        ActivityLog.record(com.agentledger.db.Database.get(), Session.branchId(), Session.user().id(),
                "ACCOUNT_UPDATE", accountId + " -> " + name);
    }

    /** Deactivate (soft). Guards: cannot deactivate a cash account. */
    public static void deactivate(int accountId) throws Exception {
        if (!Permissions.canTopUp()) throw new IllegalStateException(I18n.t("error.noPermission"));
        if ("cash".equals(AccountRepo.acctType(accountId)))
            throw new IllegalStateException(I18n.t("error.cannotDeactivateCash"));

        AccountRepo.setActive(accountId, false);
        ActivityLog.record(com.agentledger.db.Database.get(), Session.branchId(), Session.user().id(),
                "ACCOUNT_DEACTIVATE", String.valueOf(accountId));
    }

    public static void reactivate(int accountId) throws Exception {
        if (!Permissions.canTopUp()) throw new IllegalStateException(I18n.t("error.noPermission"));
        AccountRepo.setActive(accountId, true);
        ActivityLog.record(com.agentledger.db.Database.get(), Session.branchId(), Session.user().id(),
                "ACCOUNT_REACTIVATE", String.valueOf(accountId));
    }
}