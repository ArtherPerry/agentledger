package com.agentledger.ui.login;

import com.agentledger.i18n.I18n;
import com.agentledger.license.ResetService;
import com.agentledger.nav.Router;
import com.agentledger.nav.View;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;

public class ResetController {

    @FXML private TextField codeField;
    @FXML private TextField keyField;
    @FXML private PasswordField newPassword;
    @FXML private PasswordField newPasswordConfirm;
    @FXML private Label message;

    private String code;

    @FXML
    public void initialize() {
        try {
            code = ResetService.requestCode();
            codeField.setText(code);
        } catch (Exception e) {
            show(e.getMessage(), true);
        }
    }

    @FXML
    private void onCopy() {
        ClipboardContent content = new ClipboardContent();
        content.putString(code);
        Clipboard.getSystemClipboard().setContent(content);
        show(I18n.t("license.copied"), false);
    }

    @FXML
    private void onApply() {
        String p1 = newPassword.getText() == null ? "" : newPassword.getText();
        String p2 = newPasswordConfirm.getText() == null ? "" : newPasswordConfirm.getText();
        if (!p1.equals(p2)) { show(I18n.t("setup.passwordMismatch"), true); return; }
        try {
            boolean ok = ResetService.applyReset(keyField.getText().trim(), p1);
            if (ok) {
                show(I18n.t("reset.success"), false);
                Router.to(View.LOGIN);
            } else {
                show(I18n.t("reset.invalid"), true);
            }
        } catch (Exception e) {
            show(e.getMessage(), true);
        }
    }

    @FXML
    private void onBack() {
        Router.to(View.LOGIN);
    }

    private void show(String text, boolean error) {
        message.setText(text);
        message.setStyle(error ? "-fx-font-size:12px;-fx-text-fill:#791F1F;"
                : "-fx-font-size:12px;-fx-text-fill:#0B5B5A;");
        message.setVisible(true);
        message.setManaged(true);
    }
}