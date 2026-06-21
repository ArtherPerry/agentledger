package com.agentledger.model;

import com.agentledger.i18n.I18n;

public record UserRow(int id, String name, String username, String role, boolean active) {
    public String roleMm() {
        return switch (role) {
            case "owner" -> I18n.t("role.owner");
            case "manager" -> I18n.t("role.manager");
            case "cashier" -> I18n.t("role.cashier");
            default -> role;
        };
    }
}