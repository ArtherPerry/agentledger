package com.agentledger.ui.accounts;

import com.agentledger.i18n.Fmt;
import com.agentledger.i18n.I18n;
import com.agentledger.model.Account;
import com.agentledger.model.AccountBalance;
import com.agentledger.model.TxnType;
import com.agentledger.repo.AccountRepo;
import com.agentledger.repo.LedgerRepo;
import com.agentledger.service.AccountService;
import com.agentledger.service.LedgerService;
import com.agentledger.service.Permissions;
import com.agentledger.service.Session;
import com.agentledger.utils.Money;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;

public class AccountsController {

    @FXML private TableView<AccountBalance> table;
    @FXML private TableColumn<AccountBalance, String> colName, colPlatform, colBalance;
    @FXML private TableColumn<AccountBalance, Void> colAction;
    @FXML private Label cashTotal, digitalTotal;
    @FXML private Button addBtn;

    @FXML
    public void initialize() {
        colName.setCellValueFactory(d -> str(d.getValue().account().name()));
        colPlatform.setCellValueFactory(d -> str(d.getValue().account().platform() == null
                ? "—" : d.getValue().account().platform()));
        colBalance.setCellValueFactory(d -> {
            AccountBalance ab = d.getValue();
            long bal = ab.account().isDigital() ? ab.digitalPya() : ab.cashPya();
            return str(Fmt.kyat(bal));
        });
        addActionColumn();
        addBtn.setVisible(Permissions.canTopUp());
        addBtn.setManaged(Permissions.canTopUp());
        load();
    }

    private void load() {
        try {
            table.setItems(FXCollections.observableArrayList(AccountRepo.balancesForBranch(Session.branchId())));
            cashTotal.setText(Fmt.kyat(LedgerRepo.branchCashPya(Session.branchId())));
            digitalTotal.setText(Fmt.kyat(LedgerRepo.branchDigitalTotalPya(Session.branchId())));
        } catch (Exception ex) {
            com.agentledger.utils.Log.error(ex);
            cashTotal.setText("—");
            digitalTotal.setText("—");
        }
    }

    private void addActionColumn() {
        colAction.setCellFactory(col -> new TableCell<>() {
            private final Button top = new Button(I18n.t("accounts.btn.topup"));
            private final Button edit = new Button(I18n.t("common.edit"));
            private final Button off = new Button(I18n.t("accounts.btn.deactivate"));
            private final HBox box = new HBox(6, top, edit, off);
            {
                String mini = "-fx-font-size:11px;-fx-background-color:white;-fx-border-color:#d2d8d6;-fx-background-radius:6;-fx-border-radius:6;";
                top.setStyle(mini); edit.setStyle(mini);
                off.setStyle(mini + "-fx-text-fill:#791F1F;");
                top.setOnAction(e -> onTopUp(getTableView().getItems().get(getIndex())));
                edit.setOnAction(e -> onEdit(getTableView().getItems().get(getIndex())));
                off.setOnAction(e -> onDeactivate(getTableView().getItems().get(getIndex())));
            }
            @Override protected void updateItem(Void v, boolean empty) {
                super.updateItem(v, empty);
                if (empty || !Permissions.canTopUp()) { setGraphic(null); return; }
                Account acc = getTableView().getItems().get(getIndex()).account();
                // cash account: top-up + edit, but no deactivate
                off.setVisible(acc.isDigital());
                off.setManaged(acc.isDigital());
                setGraphic(box);
            }
        });
    }

    @FXML
    private void onAddAccount() {
        showAccountDialog(null);
    }

    private void onEdit(AccountBalance ab) {
        showAccountDialog(ab.account());
    }

    /** Add (existing==null) or edit an account. */
    private void showAccountDialog(Account existing) {
        boolean editing = existing != null;
        Dialog<Void> dlg = new Dialog<>();
        dlg.setTitle(editing ? I18n.t("accounts.edit.title") : I18n.t("accounts.add.title"));
        ButtonType ok = new ButtonType(editing ? I18n.t("common.save") : I18n.t("common.create"),
                ButtonBar.ButtonData.OK_DONE);
        dlg.getDialogPane().getButtonTypes().addAll(ok, ButtonType.CANCEL);

        TextField name = new TextField(editing ? existing.name() : "");
        name.setPromptText(I18n.t("accounts.add.namePrompt"));

        ComboBox<String> type = new ComboBox<>();
        type.getItems().addAll(I18n.t("accounts.type.digital"), I18n.t("accounts.type.cash"));
        boolean digitalInit = editing ? existing.isDigital() : true;
        type.getSelectionModel().select(digitalInit ? 0 : 1);
        type.setDisable(editing);   // type locked once created

        ComboBox<String> platform = new ComboBox<>();
        platform.setEditable(true);                 // OPEN — any platform allowed
        platform.getItems().addAll(AccountRepo.platformSuggestions(Session.branchId()));
        platform.setPromptText(I18n.t("accounts.add.platformPrompt"));
        if (editing && existing.platform() != null) platform.setValue(existing.platform());

        // cash type -> platform not applicable
        type.valueProperty().addListener((o, was, now) -> {
            boolean digital = now.equals(I18n.t("accounts.type.digital"));
            platform.setDisable(!digital);
        });
        platform.setDisable(!digitalInit);   // initialize disabled state for cash

        GridPane g = new GridPane();
        g.setHgap(10); g.setVgap(10);
        g.addRow(0, new Label(I18n.t("accounts.add.name")), name);
        g.addRow(1, new Label(I18n.t("accounts.add.type")), type);
        g.addRow(2, new Label(I18n.t("accounts.add.platform")), platform);
        dlg.getDialogPane().setContent(g);

        dlg.setResultConverter(bt -> {
            if (bt == ok) {
                try {
                    boolean digital = type.getValue().equals(I18n.t("accounts.type.digital"));
                    String p = platform.getEditor().getText();
                    if (editing) AccountService.rename(existing.id(), name.getText(), p);
                    else AccountService.create(name.getText(), p, digital);
                    load();
                } catch (Exception ex) { error(ex.getMessage()); }
            }
            return null;
        });
        dlg.showAndWait();
    }

    private void onDeactivate(AccountBalance ab) {
        Account acc = ab.account();
        Alert c = new Alert(Alert.AlertType.CONFIRMATION,
                I18n.t("accounts.deactivate.confirm", acc.name()), ButtonType.YES, ButtonType.NO);
        c.setHeaderText(null); c.showAndWait();
        if (c.getResult() != ButtonType.YES) return;
        try { AccountService.deactivate(acc.id()); load(); }
        catch (Exception ex) { error(ex.getMessage()); }
    }

    private void onTopUp(AccountBalance ab) {
        Account acc = ab.account();
        boolean digital = acc.isDigital();
        String what = digital ? TxnType.TOPUP_DIGITAL : TxnType.TOPUP_CASH;

        TextInputDialog dlg = new TextInputDialog();
        dlg.setTitle(what);
        dlg.setHeaderText(acc.name() + " — " + what);
        dlg.setContentText(I18n.t("accounts.topup.amount"));
        com.agentledger.utils.Numeric.money(dlg.getEditor());
        dlg.showAndWait().ifPresent(input -> {
            try {
                long amt = Money.parse(input);
                LedgerService.topUp(acc, amt, digital, null);
                load();
            } catch (Exception ex) { error(ex.getMessage()); }
        });
    }

    @FXML private void onRefresh() { load(); }

    private void error(String m) {
        Alert a = new Alert(Alert.AlertType.ERROR, m, ButtonType.OK);
        a.setHeaderText(null); a.showAndWait();
    }

    private SimpleStringProperty str(String s) { return new SimpleStringProperty(s); }
}