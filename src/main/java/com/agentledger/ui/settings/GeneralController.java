package com.agentledger.ui.settings;

import com.agentledger.i18n.I18n;
import com.agentledger.repo.SettingsRepo;
import com.agentledger.service.Session;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleGroup;

/** General branch settings. Currently: the negative-balance policy (Allow / Warn / Block). */
public class GeneralController {

    @FXML private RadioButton policyAllow, policyWarn, policyBlock;
    @FXML private Label savedHint;

    private final ToggleGroup policyGroup = new ToggleGroup();

    @FXML
    public void initialize() {
        policyAllow.setToggleGroup(policyGroup);
        policyWarn.setToggleGroup(policyGroup);
        policyBlock.setToggleGroup(policyGroup);
        policyAllow.setUserData("allow");
        policyWarn.setUserData("warn");
        policyBlock.setUserData("block");

        String current = SettingsRepo.get(Session.branchId(),
                SettingsRepo.KEY_BALANCE_POLICY, "warn");
        switch (current) {
            case "allow" -> policyAllow.setSelected(true);
            case "block" -> policyBlock.setSelected(true);
            default -> policyWarn.setSelected(true);
        }

        if (savedHint != null) { savedHint.setVisible(false); savedHint.setManaged(false); }

        // register AFTER setting the initial selection, so load doesn't trigger a spurious save
        policyGroup.selectedToggleProperty().addListener((o, a, b) -> onPolicyChanged(b));
    }

    private void onPolicyChanged(Toggle selected) {
        if (selected == null) return;
        String value = (String) selected.getUserData();
        try {
            SettingsRepo.set(Session.branchId(), SettingsRepo.KEY_BALANCE_POLICY, value);
            if (savedHint != null) {
                savedHint.setText(I18n.t("settings.saved"));
                savedHint.setVisible(true); savedHint.setManaged(true);
            }
        } catch (Exception e) {
            com.agentledger.utils.Log.error(e);
        }
    }
}
