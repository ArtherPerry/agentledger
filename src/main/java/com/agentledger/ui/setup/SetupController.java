package com.agentledger.ui.setup;

import com.agentledger.i18n.I18n;
import com.agentledger.nav.Router;
import com.agentledger.nav.View;
import com.agentledger.service.BranchService;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

public class SetupController {

    @FXML private TextField shopName;
    @FXML private TextField ownerName;
    @FXML private TextField username;
    @FXML private PasswordField password;
    @FXML private PasswordField passwordConfirm;
    @FXML private Label message;

    @FXML
    private void onCreate() {
        String pw = password.getText() == null ? "" : password.getText();
        String pw2 = passwordConfirm.getText() == null ? "" : passwordConfirm.getText();
        if (!pw.equals(pw2)) {
            error(I18n.t("setup.passwordMismatch"));
            return;
        }
        try {
            BranchService.createFirstBranch(
                    shopName.getText(), ownerName.getText(),
                    username.getText(), pw);
            Router.to(View.BRANCH);   // setup done -> normal flow (branch select -> login)
        } catch (Exception e) {
            error(e.getMessage());
        }
    }

    private void error(String text) {
        message.setText(text);
        message.setVisible(true);
        message.setManaged(true);
    }
}