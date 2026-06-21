package com.agentledger.ui.settings;

import com.agentledger.model.FeeRule;
import com.agentledger.repo.FeeRuleRepo;
import com.agentledger.service.Permissions;
import com.agentledger.service.Session;
import com.agentledger.utils.Money;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import com.agentledger.i18n.I18n;

public class FeesController {

    @FXML private TableView<FeeRule> table;
    @FXML private TableColumn<FeeRule, String> colType, colPlatform, colMin, colMax, colFee, colMinFee, colComm;
    @FXML private TableColumn<FeeRule, Void> colAction;
    @FXML private Button addBtn;
    @FXML private Label hint;

    @FXML
    public void initialize() {
        colType.setCellValueFactory(d -> str(d.getValue().typeName()));
        colPlatform.setCellValueFactory(d -> str(d.getValue().platform()));
        colMin.setCellValueFactory(d -> str(Money.format(d.getValue().minAmountPya())));
        colMax.setCellValueFactory(d -> str(d.getValue().maxAmountPya() == null ? "—" : Money.format(d.getValue().maxAmountPya())));
        colFee.setCellValueFactory(d -> str(d.getValue().feePct() + "%"));
        colMinFee.setCellValueFactory(d -> str(Money.format(d.getValue().minFeePya())));
        colComm.setCellValueFactory(d -> str(d.getValue().commPct() + "%"));

        boolean canEdit = Permissions.canEditFeeRules();
        addBtn.setDisable(!canEdit);
        if (!canEdit) hint.setText(I18n.t("fees.hint.viewOnly"));
        addActionColumn(canEdit);
        load();
    }

    private void load() {
        table.setItems(FXCollections.observableArrayList(FeeRuleRepo.listForBranch(Session.branchId())));
    }

    private void addActionColumn(boolean canEdit) {
        colAction.setCellFactory(col -> new TableCell<>() {
            private final Button edit = new Button(I18n.t("common.edit"));
            private final Button del = new Button(I18n.t("common.delete"));
            private final HBox box = new HBox(6, edit, del);
            {
                edit.setStyle("-fx-font-size:11px;");
                del.setStyle("-fx-font-size:11px;-fx-text-fill:#791F1F;");
                edit.setOnAction(e -> openDialog(getTableView().getItems().get(getIndex())));
                del.setOnAction(e -> onDelete(getTableView().getItems().get(getIndex())));
            }
            @Override protected void updateItem(Void v, boolean empty) {
                super.updateItem(v, empty);
                setGraphic(empty || !canEdit ? null : box);
            }
        });
    }

    @FXML private void onAdd() { openDialog(null); }

    private void onDelete(FeeRule r) {
        Alert c = new Alert(Alert.AlertType.CONFIRMATION,
                I18n.t("fees.delete.confirm", r.platform()), ButtonType.YES, ButtonType.NO);
        c.setHeaderText(null); c.showAndWait();
        if (c.getResult() != ButtonType.YES) return;
        try { FeeRuleRepo.delete(r.id()); load(); } catch (Exception ex) { err(ex.getMessage()); }
    }

    /** Add (rule==null) or edit an existing rule. */
    private void openDialog(FeeRule rule) {
        Dialog<Void> dlg = new Dialog<>();
        dlg.setTitle(rule == null ? I18n.t("fees.dialog.add") : I18n.t("fees.dialog.edit"));
        ButtonType ok = new ButtonType(I18n.t("common.save"), ButtonBar.ButtonData.OK_DONE);
        dlg.getDialogPane().getButtonTypes().addAll(ok, ButtonType.CANCEL);

        ComboBox<String> type = new ComboBox<>();
        type.getItems().addAll(FeeRuleRepo.feeBearingTypes());
        type.setValue(rule == null ? FeeRuleRepo.feeBearingTypes().get(0) : rule.typeName());

        TextField platform = new TextField(rule == null ? "" : rule.platform());
        TextField min = new TextField(rule == null ? "0" : Money.format(rule.minAmountPya()));
        TextField max = new TextField(rule == null || rule.maxAmountPya() == null ? "" : Money.format(rule.maxAmountPya()));
        TextField fee = new TextField(rule == null ? "0.5" : String.valueOf(rule.feePct()));
        TextField minFee = new TextField(rule == null ? "500" : Money.format(rule.minFeePya()));
        TextField comm = new TextField(rule == null ? "0.3" : String.valueOf(rule.commPct()));

        javafx.scene.layout.GridPane g = new javafx.scene.layout.GridPane();
        g.setHgap(10); g.setVgap(10);
        g.addRow(0, new Label(I18n.t("fees.field.type")), type);
        g.addRow(1, new Label(I18n.t("fees.field.platform")), platform);
        g.addRow(2, new Label(I18n.t("fees.field.minAmount")), min);
        g.addRow(3, new Label(I18n.t("fees.field.maxAmount")), max);
        g.addRow(4, new Label(I18n.t("fees.field.feePct")), fee);
        g.addRow(5, new Label(I18n.t("fees.field.minFee")), minFee);
        g.addRow(6, new Label(I18n.t("fees.field.commPct")), comm);
        dlg.getDialogPane().setContent(g);

        dlg.setResultConverter(bt -> {
            if (bt == ok) {
                try {
                    Long maxPya = max.getText().isBlank() ? null : Money.parse(max.getText());
                    FeeRule r = new FeeRule(
                            rule == null ? 0 : rule.id(),
                            type.getValue(),
                            platform.getText().trim(),
                            Money.parse(min.getText()),
                            maxPya,
                            Double.parseDouble(fee.getText().trim()),
                            Money.parse(minFee.getText()),
                            Double.parseDouble(comm.getText().trim()),
                            true);
                    if (r.platform().isBlank()) throw new IllegalArgumentException(I18n.t("fees.err.platformRequired"));
                    if (rule == null) FeeRuleRepo.insert(Session.branchId(), r);
                    else FeeRuleRepo.update(r);
                    load();
                } catch (Exception ex) { err(ex.getMessage()); }
            }
            return null;
        });
        dlg.showAndWait();
    }

    private void err(String m) { Alert a = new Alert(Alert.AlertType.ERROR, m, ButtonType.OK); a.setHeaderText(null); a.showAndWait(); }
    private SimpleStringProperty str(String s) { return new SimpleStringProperty(s); }
}