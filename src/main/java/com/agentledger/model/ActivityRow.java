package com.agentledger.model;

import com.agentledger.i18n.I18n;

public record ActivityRow(String ts, String userName, String action, String detail) {
    /** Friendly Burmese label for the action code. */
    public String actionMm() {
        return I18n.t("activity.action." + action);
    }
}