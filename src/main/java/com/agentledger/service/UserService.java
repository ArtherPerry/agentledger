package com.agentledger.service;

import com.agentledger.i18n.I18n;

import at.favre.lib.crypto.bcrypt.BCrypt;
import com.agentledger.model.UserRow;
import com.agentledger.repo.UserRepo;

public final class UserService {
    private UserService() {}

    public static void create(String name, String username, String password, String role) throws Exception {
        if (!Permissions.canManageUsers()) throw new IllegalStateException(I18n.t("error.noPermission"));
        if (name == null || name.isBlank()) throw new IllegalArgumentException(I18n.t("error.nameRequired"));
        if (username == null || username.isBlank()) throw new IllegalArgumentException(I18n.t("error.usernameRequired"));
        if (password == null || password.length() < 4) throw new IllegalArgumentException(I18n.t("error.passwordMin"));
        int branch = Session.branchId();
        if (UserRepo.usernameExists(branch, username.trim(), -1))
            throw new IllegalArgumentException(I18n.t("error.usernameExists"));
        String hash = BCrypt.withDefaults().hashToString(12, password.toCharArray());
        UserRepo.insert(branch, name.trim(), username.trim(), hash, role);
        ActivityLog.record(com.agentledger.db.Database.get(), branch, Session.user().id(),
                "USER_CREATE", username + " (" + role + ")");
    }

    public static void update(UserRow existing, String name, String role, boolean active, String newPassword) throws Exception {
        if (!Permissions.canManageUsers()) throw new IllegalStateException(I18n.t("error.noPermission"));
        int branch = Session.branchId();

        // Safety: don't deactivate or demote the last active owner.
        boolean wasActiveOwner = existing.active() && "owner".equals(existing.role());
        boolean willBeActiveOwner = active && "owner".equals(role);
        if (wasActiveOwner && !willBeActiveOwner && UserRepo.countOwners(branch) <= 1)
            throw new IllegalStateException(I18n.t("error.lastOwner"));

        String hash = (newPassword == null || newPassword.isBlank())
                ? null : BCrypt.withDefaults().hashToString(12, newPassword.toCharArray());
        if (hash != null && newPassword.length() < 4)
            throw new IllegalArgumentException(I18n.t("error.passwordMin"));

        UserRepo.update(existing.id(), name.trim(), role, active, hash);
        ActivityLog.record(com.agentledger.db.Database.get(), branch, Session.user().id(),
                "USER_UPDATE", existing.username());
    }
}