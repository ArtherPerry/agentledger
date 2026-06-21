package com.agentledger.ui.license;

import com.agentledger.i18n.I18n;
import com.agentledger.license.DeviceId;
import com.agentledger.license.LicenseService;
import com.agentledger.license.LicenseStore;
import com.agentledger.nav.Router;
import com.agentledger.nav.View;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;

public class ActivationController {

    @FXML private TextField deviceIdField;
    @FXML private TextField keyField;
    @FXML private Button copyBtn;
    @FXML private Button activateBtn;
    @FXML private Label message;

    private String deviceId;

    @FXML
    public void initialize() {
        deviceId = DeviceId.get();
        deviceIdField.setText(deviceId);
    }

    @FXML
    private void onCopy() {
        ClipboardContent content = new ClipboardContent();
        content.putString(deviceId);
        Clipboard.getSystemClipboard().setContent(content);
        show(I18n.t("license.copied"), false);
    }

    @FXML
    private void onActivate() {
        String key = keyField.getText() == null ? "" : keyField.getText().trim();
        if (LicenseService.verify(deviceId, key)) {
            try {
                LicenseStore.save(key);
            } catch (Exception e) {
                show(I18n.t("license.invalid"), true);
                return;
            }
            // activated — proceed into the app
            Router.to(View.SETUP);
        } else {
            show(I18n.t("license.invalid"), true);
        }
    }

    private void show(String text, boolean error) {
        message.setText(text);
        message.setStyle(error ? "-fx-font-size:12px;-fx-text-fill:#791F1F;"
                : "-fx-font-size:12px;-fx-text-fill:#0B5B5A;");
        message.setVisible(true);
        message.setManaged(true);
    }
}