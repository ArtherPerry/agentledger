package com.agentledger.ui.txn;

import com.agentledger.model.TxnType;
import com.agentledger.i18n.Fmt;
import com.agentledger.i18n.I18n;
import com.agentledger.model.*;
import com.agentledger.repo.*;
import com.agentledger.service.*;
import com.agentledger.utils.Money;
import com.agentledger.utils.Numeric;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;

public class TxnController {

    @FXML private ComboBox<TxnType> typeBox;
    @FXML private ComboBox<Account> accountBox;
    @FXML private ComboBox<Account> toAccountBox;
    @FXML private VBox toAccountRow;
    @FXML private TextField senderPhone, receiverPhone, amount, refNo, note;
    @FXML private Label pvAmount, pvTotal, feeWarn, overrideHint;
    @FXML private TextField feeField, commField;
    @FXML private Button saveBtn, clearBtn;

    private boolean canOverride;   // owner only
    private boolean syncing;       // guard to avoid listener loops when we set fields programmatically
    private long computedFee, computedComm;

    @FXML
    public void initialize() {
        int branch = Session.branchId();
        canOverride = true;

        typeBox.setItems(FXCollections.observableArrayList(TxnTypeRepo.listForBranch(branch)));
        accountBox.setItems(FXCollections.observableArrayList(AccountRepo.listForBranch(branch)));
        toAccountBox.setItems(FXCollections.observableArrayList(AccountRepo.digitalForBranch(branch)));

        // English-only numeric entry (blocks Burmese digits / letters on any keyboard)
        Numeric.money(amount, feeField, commField);
        Numeric.phone(senderPhone, receiverPhone);

        typeBox.valueProperty().addListener((o, a, b) -> { updateToAccountVisibility(); recompute(); });
        accountBox.valueProperty().addListener((o, a, b) -> recompute());
        amount.textProperty().addListener((o, a, b) -> recompute());

        // fee/commission editable only for owner
        feeField.setEditable(canOverride);
        commField.setEditable(canOverride);
        feeField.setDisable(!canOverride);
        commField.setDisable(!canOverride);
        if (canOverride) {
            feeField.textProperty().addListener((o, a, b) -> onFeeEdited());
            commField.textProperty().addListener((o, a, b) -> onFeeEdited());
        }

        saveBtn.setDisable(false);
        saveBtn.setOnAction(e -> onSave());
        clearBtn.setOnAction(e -> onClear());

        if (!typeBox.getItems().isEmpty()) typeBox.getSelectionModel().selectFirst();
        accountBox.getItems().stream().filter(Account::isDigital).findFirst()
                .ifPresent(accountBox.getSelectionModel()::select);
        recompute();
    }

    private void updateToAccountVisibility() {
        TxnType t = typeBox.getValue();
        boolean walletToWallet = t != null && TxnType.WALLET_TO_WALLET.equals(t.name());
        toAccountRow.setVisible(walletToWallet);
        toAccountRow.setManaged(walletToWallet);
    }

    /** Recompute the rule-based fee, refill the (owner-editable) fields, show warning if no rule. */
    private void recompute() {
        long amt = Money.parse(amount.getText());
        Account acc = accountBox.getValue();
        String platform = (acc != null) ? acc.platform() : null;

        TxnType t = typeBox.getValue();
        String typeName = (t != null) ? t.name() : null;
        FeeResult fr;
        try {
            fr = FeeService.compute(Session.branchId(), typeName, platform, amt);
        } catch (FeeService.FeeLookupException ex) {
            // DB error computing the fee — make it visible and block a misleading zero.
            computedFee = 0; computedComm = 0;
            syncing = true;
            feeField.setText(Money.format(0));
            commField.setText(Money.format(0));
            syncing = false;
            feeWarn.setText(I18n.t("txn.feeError"));
            feeWarn.setVisible(true); feeWarn.setManaged(true);
            pvAmount.setText(Fmt.kyat(amt));
            updateTotal();
            return;
        }
        computedFee = fr.feePya();
        computedComm = fr.commissionPya();

        pvAmount.setText(Fmt.kyat(amt));

        // refill fee/comm fields with the computed values (this is the auto default)
        syncing = true;
        feeField.setText(Money.format(computedFee));
        commField.setText(Money.format(computedComm));
        syncing = false;

        // warning: digital account whose platform has no fee rule
        // warning: digital account + this type/platform has NO fee rule at all
        boolean digital = acc != null && acc.isDigital();
        boolean noRule = digital && !FeeService.hasRule(Session.branchId(), typeName, platform);
        feeWarn.setText(noRule ? I18n.t("txn.noFeeRuleWarn") : "");
        feeWarn.setVisible(noRule); feeWarn.setManaged(noRule);
        overrideHint.setVisible(false); overrideHint.setManaged(false);
        updateTotal();
    }

    /** Owner edited a fee/commission field manually. */
    private void onFeeEdited() {
        if (syncing) return;   // ignore our own programmatic refills
        boolean overridden = currentFee() != computedFee || currentComm() != computedComm;
        overrideHint.setText(overridden ? I18n.t("txn.overrideHint") : "");
        overrideHint.setVisible(overridden); overrideHint.setManaged(overridden);
        updateTotal();
    }

    private long currentFee() { return Money.parse(feeField.getText()); }
    private long currentComm() { return Money.parse(commField.getText()); }

    private void updateTotal() {
        long amt = Money.parse(amount.getText());
        pvTotal.setText(Fmt.kyat(amt + currentFee()));
    }

    private void onSave() {
        try {
            long amt = Money.parse(amount.getText());
            Account acc = accountBox.getValue();

            long fee = canOverride ? currentFee() : computedFee;
            long comm = canOverride ? currentComm() : computedComm;
            boolean overridden = canOverride && (fee != computedFee || comm != computedComm);

            PostingRequest r = new PostingRequest();
            r.type = typeBox.getValue();
            r.account = acc;
            r.toAccount = toAccountBox.getValue();
            r.amountPya = amt;
            r.feePya = fee;
            r.commissionPya = comm;
            r.feeOverridden = overridden;
            r.senderPhone = senderPhone.getText();
            r.receiverPhone = receiverPhone.getText();
            r.refNo = refNo.getText();
            r.note = note.getText();

            // Negative-balance policy: WARN asks for confirmation before overdrawing.
            // (BLOCK is enforced inside LedgerService.post and surfaces via the catch below;
            //  ALLOW posts silently.)
            if (BalancePolicy.of(Session.branchId()) == BalancePolicy.WARN) {
                LedgerService.Shortfall sf = LedgerService.projectedShortfall(r);
                if (sf != null) {
                    Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, sf.warnMessage(),
                            ButtonType.YES, ButtonType.NO);
                    confirm.setHeaderText(null);
                    confirm.showAndWait();
                    if (confirm.getResult() != ButtonType.YES) return;
                }
            }
            long id = LedgerService.post(r);
            info(I18n.t("txn.success", id));
            onClear();
        } catch (Exception ex) {
            error(ex.getMessage() == null ? ex.toString() : ex.getMessage());
        }
    }

    private void info(String msg) {
        Alert a = new Alert(Alert.AlertType.INFORMATION, msg, ButtonType.OK);
        a.setHeaderText(null); a.showAndWait();
    }

    private void error(String msg) {
        Alert a = new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK);
        a.setHeaderText(null); a.showAndWait();
    }

    @FXML
    private void onClear() {
        senderPhone.clear(); receiverPhone.clear(); amount.clear(); refNo.clear(); note.clear();
        recompute();
    }
}