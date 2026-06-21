package com.agentledger.ui.dashboard;

import com.agentledger.model.User;
import com.agentledger.nav.Router;
import com.agentledger.nav.View;
import com.agentledger.service.Permissions;
import com.agentledger.service.Session;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.ArrayList;
import java.util.List;
import com.agentledger.i18n.I18n;

public class DashboardController {

    @FXML private Label branchLabel;
    @FXML private Label userLabel;
    @FXML private VBox sidebar;
    @FXML private StackPane contentArea;

    private final List<Button> navButtons = new ArrayList<>();

    @FXML
    public void initialize() {
        branchLabel.setText(Session.branchName());
        User u = Session.user();
        userLabel.setText(u.name() + " (" + roleMm(u.role()) + ")");

        Router.setContentArea(contentArea);

        // main items (always visible)
        addNav(I18n.t("nav.home"), View.HOME);
        addNav(I18n.t("nav.txn"), View.TXN);
        addNav(I18n.t("nav.history"), View.HISTORY);
        addNav(I18n.t("nav.accounts"), View.ACCOUNTS);
        addNav(I18n.t("nav.payrec"), View.PAYREC);
        addNav(I18n.t("nav.close"), View.CLOSE);
        if (Permissions.canViewReports()) addNav(I18n.t("nav.reports"), View.REPORTS);

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);
        sidebar.getChildren().add(spacer);

        if (Permissions.canOpenSettings()) addNav(I18n.t("nav.settings"), View.SETTINGS);
        addBottomAction(I18n.t("nav.switchBranch"), () -> { Session.logout(); Router.to(View.BRANCH); });
        addBottomAction(I18n.t("nav.logout"), () -> { Session.logoutKeepBranch(); Router.to(View.LOGIN); });
        // open the default screen
        if (!navButtons.isEmpty()) select(navButtons.get(0), View.HOME);
    }

    private void addNav(String label, View view) {
        Button b = baseButton(label);
        b.setOnAction(e -> select(b, view));
        navButtons.add(b);
        sidebar.getChildren().add(b);
    }

    private void addBottomAction(String label, Runnable action) {
        Button b = baseButton(label);
        b.setOnAction(e -> action.run());
        sidebar.getChildren().add(b);
    }

    private Button baseButton(String label) {
        Button b = new Button(label);
        b.setMaxWidth(Double.MAX_VALUE);
        b.setAlignment(Pos.CENTER_LEFT);
        styleIdle(b);
        return b;
    }

    private void select(Button active, View view) {
        for (Button b : navButtons) styleIdle(b);
        styleActive(active);
        Router.show(view);
    }

    private void styleIdle(Button b) {
        b.setStyle("-fx-background-color:transparent;-fx-text-fill:#5C6967;-fx-font-size:13px;-fx-padding:10 12 10 12;-fx-background-radius:8;-fx-cursor:hand;");
    }

    private void styleActive(Button b) {
        b.setStyle("-fx-background-color:#DCEEEC;-fx-text-fill:#0C3B3C;-fx-font-size:13px;-fx-font-weight:bold;-fx-padding:10 12 10 12;-fx-background-radius:8;-fx-cursor:hand;");
    }

    private String roleMm(String role) {
        return switch (role) {
            case "owner" -> I18n.t("role.owner");
            case "manager" -> I18n.t("role.manager");
            case "cashier" -> I18n.t("role.cashier");
            default -> role;
        };
    }
}