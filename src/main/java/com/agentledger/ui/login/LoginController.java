package com.agentledger.ui.login;

import com.agentledger.model.User;
import com.agentledger.nav.Router;
import com.agentledger.nav.View;
import com.agentledger.service.AuthService;
import com.agentledger.service.Session;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import com.agentledger.i18n.I18n;

import java.util.Optional;

public class LoginController {

    @FXML private Label branchLabel;
    @FXML private Label error;
    @FXML private TextField username;
    @FXML private PasswordField password;
    @FXML private TextField passwordVisible;
    @FXML private CheckBox showPassword;

    @FXML
    public void initialize() {
        branchLabel.setText(Session.branchName());
        error.setText("");

        // Sync text one-way depending on which field the user edits — NO bidirectional bind.
        password.textProperty().addListener((o, was, now) -> {
            if (!showPassword.isSelected() && !now.equals(passwordVisible.getText()))
                passwordVisible.setText(now);
        });
        passwordVisible.textProperty().addListener((o, was, now) -> {
            if (showPassword.isSelected() && !now.equals(password.getText()))
                password.setText(now);
        });

        showPassword.selectedProperty().addListener((obs, was, now) -> {
            // copy current value into the field we're switching to, then swap visibility
            if (now) passwordVisible.setText(password.getText());
            else     password.setText(passwordVisible.getText());
            passwordVisible.setVisible(now);
            passwordVisible.setManaged(now);
            password.setVisible(!now);
            password.setManaged(!now);
        });

        username.setOnAction(e -> onLogin());
        password.setOnAction(e -> onLogin());
        passwordVisible.setOnAction(e -> onLogin());
    }

    /** Read from whichever password field is currently visible. */
    private String currentPassword() {
        return showPassword.isSelected() ? passwordVisible.getText() : password.getText();
    }

    @FXML
    private void onLogin() {
        String user = username.getText() == null ? "" : username.getText().trim();
        String pass = currentPassword() == null ? "" : currentPassword();

        Optional<User> u = AuthService.login(Session.branchId(), user, pass);
        if (u.isPresent()) {
            error.setText("");
            Session.login(u.get());
            Router.to(View.DASHBOARD);
        } else {
            error.setText(I18n.t("login.error.invalid"));
        }
    }

    @FXML
    private void onBack() {
        Session.logout();
        Router.to(View.BRANCH);
    }
    @FXML
    private void onForgot() {
        Router.to(View.RESET);
    }
}