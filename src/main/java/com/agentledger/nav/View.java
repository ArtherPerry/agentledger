package com.agentledger.nav;

public enum View {
    BRANCH("/fxml/branch.fxml"),
    LOGIN("/fxml/login.fxml"),
    DASHBOARD("/fxml/dashboard.fxml"),

    // feature screens loaded into the dashboard center
    HOME("/fxml/home.fxml"),
    TXN("/fxml/txn.fxml"),
    HISTORY("/fxml/history.fxml"),
    ACCOUNTS("/fxml/accounts.fxml"),
    PAYREC("/fxml/payrec.fxml"),
    CLOSE("/fxml/close.fxml"),
    REPORTS("/fxml/reports.fxml"),
    ACTIVATION("/fxml/activation.fxml"),
    SETUP("/fxml/setup.fxml"),
    RESET("/fxml/reset.fxml"),
    SETTINGS("/fxml/settings.fxml");


    private final String path;
    View(String path) { this.path = path; }
    public String path() { return path; }
}