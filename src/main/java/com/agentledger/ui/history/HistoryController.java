package com.agentledger.ui.history;

import com.agentledger.model.Account;
import com.agentledger.model.LedgerRow;
import com.agentledger.repo.AccountRepo;
import com.agentledger.repo.LedgerRepo;
import com.agentledger.service.LedgerService;
import com.agentledger.service.Permissions;
import com.agentledger.service.Session;
import com.agentledger.utils.Money;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import com.agentledger.i18n.I18n;
import com.agentledger.i18n.Fmt;

public class HistoryController {

    @FXML private TableView<LedgerRow> table;
    @FXML private TableColumn<LedgerRow, String> colId, colTime, colType, colAcct, colAmount, colFee, colBy, colStatus;
    @FXML private TableColumn<LedgerRow, Void> colAction;
    @FXML private Label cashLabel, digitalLabel;

    @FXML
    public void initialize() {
        colId.setCellValueFactory(d -> str("#" + d.getValue().id()));
        colTime.setCellValueFactory(d -> str(d.getValue().time()));
        colType.setCellValueFactory(d -> str(d.getValue().typeName()));
        colAcct.setCellValueFactory(d -> str(d.getValue().accountName()));
        colAmount.setCellValueFactory(d -> str(Money.format(d.getValue().amountPya())));
        colFee.setCellValueFactory(d -> str(Money.format(d.getValue().feePya())));
        colBy.setCellValueFactory(d -> str(d.getValue().createdBy()));
        colStatus.setCellValueFactory(d -> str(statusText(d.getValue())));

        addActionColumn();
        load();
    }

    private void load() {
        table.setItems(FXCollections.observableArrayList(LedgerRepo.recent(Session.branchId(), 200)));
        try {
            long cash = LedgerRepo.branchCashPya(Session.branchId());
            long digital = 0;
            for (Account a : AccountRepo.digitalForBranch(Session.branchId()))
                digital += LedgerRepo.accountDigitalPya(a.id());
            cashLabel.setText(Fmt.kyat(cash));
            digitalLabel.setText(Fmt.kyat(digital));
        } catch (Exception ex) {
            com.agentledger.utils.Log.error(ex);
            cashLabel.setText("—");
            digitalLabel.setText("—");
        }
    }

    private String statusText(LedgerRow r) {
        if (r.isReversal()) return I18n.t("status.reversal");
        if (r.reversed())   return I18n.t("status.reversed");
        return I18n.t("status.done");
    }

    private void addActionColumn() {
        colAction.setCellFactory(col -> new TableCell<>() {
            private final Button btn = new Button(I18n.t("history.btn.reverse"));
            {
                btn.setStyle("-fx-font-size:11px;-fx-text-fill:#791F1F;-fx-border-color:#E3B7B7;-fx-background-color:white;-fx-background-radius:6;-fx-border-radius:6;");
                btn.setOnAction(e -> onReverse(getTableView().getItems().get(getIndex())));
            }
            @Override protected void updateItem(Void v, boolean empty) {
                super.updateItem(v, empty);
                if (empty) { setGraphic(null); return; }
                LedgerRow r = getTableView().getItems().get(getIndex());
                boolean canReverse = Permissions.canReverse() && !r.isReversal() && !r.reversed();
                setGraphic(canReverse ? new HBox(btn) : null);
            }
        });
    }

    private void onReverse(LedgerRow r) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                I18n.t("history.reverse.confirm", r.id()), ButtonType.YES, ButtonType.NO);
        confirm.setHeaderText(null);
        confirm.showAndWait();
        if (confirm.getResult() != ButtonType.YES) return;
        try {
            LedgerService.reverse(r.id());
            load();
        } catch (Exception ex) {
            Alert a = new Alert(Alert.AlertType.ERROR, ex.getMessage(), ButtonType.OK);
            a.setHeaderText(null); a.showAndWait();
        }
    }

    @FXML private void onRefresh() { load(); }

    private SimpleStringProperty str(String s) { return new SimpleStringProperty(s); }
}