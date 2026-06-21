package com.agentledger.ui.settings;

import com.agentledger.service.Permissions;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;
import com.agentledger.i18n.I18n;

public class SettingsController {

    @FXML private VBox subNav;
    @FXML private StackPane settingsContent;

    private final List<Button> buttons = new ArrayList<>();

    @FXML
    public void initialize() {
        addTab(I18n.t("settings.tab.fees"), "/fxml/settings/fees.fxml");
        if (Permissions.canManageTxnTypes())
            addTab(I18n.t("settings.tab.txntypes"), "/fxml/settings/txntypes.fxml");
        addTab(I18n.t("settings.tab.users"), "/fxml/settings/users.fxml");
        addTab(I18n.t("settings.tab.branches"), "/fxml/settings/branches.fxml");
        addTab(I18n.t("settings.tab.activity"), "/fxml/settings/activity.fxml");
        addTab(I18n.t("settings.tab.backup"), "/fxml/settings/backup.fxml");
    }

    private void addTab(String label, String fxml) {
        Button b = new Button(label);
        b.setMaxWidth(Double.MAX_VALUE);
        b.setAlignment(Pos.CENTER_LEFT);
        styleIdle(b);
        b.setOnAction(e -> select(b, fxml));
        buttons.add(b);
        subNav.getChildren().add(b);
    }

    private void select(Button active, String fxml) {
        for (Button b : buttons) styleIdle(b);
        styleActive(active);
        try {
            Parent node = new FXMLLoader(getClass().getResource(fxml), I18n.bundle()).load();
            settingsContent.getChildren().setAll(node);
        } catch (Exception e) {
            settingsContent.getChildren().setAll(new Label(I18n.t("settings.loadError", e.getMessage())));
            com.agentledger.utils.Log.error(e);
        }
    }

    private void styleIdle(Button b) {
        b.setStyle("-fx-background-color:transparent;-fx-text-fill:#5C6967;-fx-font-size:13px;-fx-padding:10 12 10 12;-fx-background-radius:8;-fx-cursor:hand;");
    }
    private void styleActive(Button b) {
        b.setStyle("-fx-background-color:#DCEEEC;-fx-text-fill:#0C3B3C;-fx-font-size:13px;-fx-font-weight:bold;-fx-padding:10 12 10 12;-fx-background-radius:8;-fx-cursor:hand;");
    }
}