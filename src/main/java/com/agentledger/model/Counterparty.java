package com.agentledger.model;

public record Counterparty(int id, String name, String type, String phone) {
    @Override public String toString() {
        return name + (type != null
                ? "  ·  " + (type.equals("agent") ? com.agentledger.i18n.I18n.t("cp.agent") : com.agentledger.i18n.I18n.t("cp.customer"))
                : "");
    }
}