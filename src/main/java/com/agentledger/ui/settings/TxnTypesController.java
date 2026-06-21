package com.agentledger.ui.settings;

import com.agentledger.i18n.I18n;
import com.agentledger.model.TxnType;
import com.agentledger.repo.TxnTypeRepo;
import com.agentledger.service.Permissions;
import com.agentledger.service.Session;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;

import java.util.LinkedHashMap;
import java.util.Map;

public class TxnTypesController {

    @FXML private TableView<TxnType> table;
    @FXML private TableColumn<TxnType, String> colName, colCash, colDigital, colStatus;
    @FXML private TableColumn<TxnType, Void> colAction;
    @FXML private Button addBtn;
    @FXML private Label hint;

    // Named single-leg money directions the owner can pick for a custom type.
    // Key = Burmese label shown; value = {cash_effect, digital_effect}.
    // The two-leg wallet-to-wallet behavior is intentionally NOT offered.
    private static Map<String, String[]> effectChoices() {
        Map<String, String[]> m = new LinkedHashMap<>();
        m.put(I18n.t("txntype.effect.cashOutDigitalIn"),  new String[]{"out", "in"});   // like withdraw
        m.put(I18n.t("txntype.effect.cashInDigitalOut"),  new String[]{"in", "out"});   // like cash-to-account
        m.put(I18n.t("txntype.effect.cashInOnly"),        new String[]{"in", "none"});
        m.put(I18n.t("txntype.effect.cashOutOnly"),       new String[]{"out", "none"});
        m.put(I18n.t("txntype.effect.digitalInOnly"),     new String[]{"none", "in"});
        m.put(I18n.t("txntype.effect.digitalOutOnly"),    new String[]{"none", "out"});
        return m;
    }

    @FXML
    public void initialize() {
        colName.setCellValueFactory(d -> str(d.getValue().displayName()));
        colCash.setCellValueFactory(d -> str(effectName(d.getValue().cashEffect())));
        colDigital.setCellValueFactory(d -> str(effectName(d.getValue().digitalEffect())));
        colStatus.setCellValueFactory(d -> str(""));   // filled by cell factory below

        boolean canEdit = Permissions.canManageTxnTypes();
        addBtn.setDisable(!canEdit);
        if (!canEdit) hint.setText(I18n.t("txntype.hint.viewOnly"));

        addStatusColumn();
        addActionColumn(canEdit);
        load();
    }

    private String effectName(String e) {
        return switch (e) {
            case "in" -> I18n.t("txntype.effect.in");
            case "out" -> I18n.t("txntype.effect.out");
            default -> I18n.t("txntype.effect.none");
        };
    }

    private void load() {
        table.setItems(FXCollections.observableArrayList(
                TxnTypeRepo.listAllForBranch(Session.branchId())));
        table.refresh();
    }

    /** Status column reads the record's own active/builtin flags — no repo calls, no index lookups. */
    private void addStatusColumn() {
        colStatus.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String v, boolean empty) {
                super.updateItem(v, empty);
                TxnType t = (getTableRow() == null) ? null : (TxnType) getTableRow().getItem();
                if (empty || t == null) { setText(null); setStyle(""); return; }
                String s = t.active() ? I18n.t("txntype.status.active") : I18n.t("txntype.status.inactive");
                if (t.builtin()) s += " · " + I18n.t("txntype.status.builtin");
                setText(s);
                setStyle(t.active() ? "" : "-fx-text-fill:#9aa5a3;");
            }
        });
    }

    private void addActionColumn(boolean canEdit) {
        colAction.setCellFactory(col -> new TableCell<>() {
            private final Button rename = new Button(I18n.t("txntype.btn.rename"));
            private final Button toggle = new Button();
            private final Button up = new Button("↑");
            private final Button down = new Button("↓");
            private final Button del = new Button(I18n.t("common.delete"));
            private final HBox box = new HBox(6, rename, toggle, up, down, del);
            {
                for (Button b : new Button[]{rename, toggle, up, down, del}) b.setStyle("-fx-font-size:11px;");
                del.setStyle("-fx-font-size:11px;-fx-text-fill:#791F1F;");
                rename.setOnAction(e -> { TxnType t = current(); if (t != null) onRename(t); });
                toggle.setOnAction(e -> { TxnType t = current(); if (t != null) onToggle(t); });
                up.setOnAction(e   -> { TxnType t = current(); if (t != null) onMove(t, -1); });
                down.setOnAction(e -> { TxnType t = current(); if (t != null) onMove(t, +1); });
                del.setOnAction(e  -> { TxnType t = current(); if (t != null) onDelete(t); });
            }
            /** Read the row's item live at click time — never a captured index. */
            private TxnType current() {
                return (getTableRow() == null) ? null : (TxnType) getTableRow().getItem();
            }
            @Override protected void updateItem(Void v, boolean empty) {
                super.updateItem(v, empty);
                TxnType t = current();
                if (empty || !canEdit || t == null) { setGraphic(null); return; }
                toggle.setText(t.active() ? I18n.t("txntype.btn.disable") : I18n.t("txntype.btn.enable"));
                del.setDisable(t.builtin());   // built-ins can never be deleted
                setGraphic(box);
            }
        });
    }

    @FXML private void onAdd() {
        Dialog<Void> dlg = new Dialog<>();
        dlg.setTitle(I18n.t("txntype.dialog.add"));
        ButtonType ok = new ButtonType(I18n.t("common.save"), ButtonBar.ButtonData.OK_DONE);
        dlg.getDialogPane().getButtonTypes().addAll(ok, ButtonType.CANCEL);

        TextField name = new TextField();
        ComboBox<String> effect = new ComboBox<>();
        effect.getItems().addAll(effectChoices().keySet());
        effect.setValue(effect.getItems().get(0));

        GridPane g = new GridPane();
        g.setHgap(10); g.setVgap(10);
        g.addRow(0, new Label(I18n.t("txntype.field.name")), name);
        g.addRow(1, new Label(I18n.t("txntype.field.effect")), effect);
        dlg.getDialogPane().setContent(g);

        dlg.setResultConverter(bt -> {
            if (bt == ok) {
                try {
                    String dn = name.getText().trim();
                    if (dn.isBlank()) throw new IllegalArgumentException(I18n.t("txntype.err.nameRequired"));
                    String[] eff = effectChoices().get(effect.getValue());
                    TxnTypeRepo.insertCustom(Session.branchId(), dn, eff[0], eff[1]);
                    load();
                } catch (Exception ex) { err(ex.getMessage()); }
            }
            return null;
        });
        dlg.showAndWait();
    }

    private void onRename(TxnType t) {
        TextInputDialog d = new TextInputDialog(t.displayName());
        d.setTitle(I18n.t("txntype.dialog.rename"));
        d.setHeaderText(null);
        d.setContentText(I18n.t("txntype.field.name"));
        d.showAndWait().ifPresent(newName -> {
            if (newName.trim().isBlank()) { err(I18n.t("txntype.err.nameRequired")); return; }
            try { TxnTypeRepo.updateDisplayName(t.id(), newName.trim()); load(); }
            catch (Exception ex) { err(ex.getMessage()); }
        });
    }

    private void onToggle(TxnType t) {
        try {
            TxnTypeRepo.setActive(t.id(), !t.active());
            load();
        } catch (Exception ex) { err(ex.getMessage()); }
    }

    private void onMove(TxnType t, int dir) {
        try {
            var items = table.getItems();
            int idx = -1;
            for (int i = 0; i < items.size(); i++) if (items.get(i).id() == t.id()) { idx = i; break; }
            if (idx < 0) return;
            int swap = idx + dir;
            if (swap < 0 || swap >= items.size()) return;
            TxnType other = items.get(swap);
            int a = TxnTypeRepo.sortOrderOf(t.id());
            int b = TxnTypeRepo.sortOrderOf(other.id());
            TxnTypeRepo.setSortOrder(t.id(), b);
            TxnTypeRepo.setSortOrder(other.id(), a);
            load();
        } catch (Exception ex) { err(ex.getMessage()); }
    }

    private void onDelete(TxnType t) {
        Alert c = new Alert(Alert.AlertType.CONFIRMATION,
                I18n.t("txntype.delete.confirm", t.displayName()), ButtonType.YES, ButtonType.NO);
        c.setHeaderText(null); c.showAndWait();
        if (c.getResult() != ButtonType.YES) return;
        try {
            TxnTypeRepo.deleteCustom(t.id());
            load();
        } catch (Exception ex) {
            err(I18n.t("txntype.err.hasHistory"));   // friendly message when it has history
        }
    }

    private void err(String m) { Alert a = new Alert(Alert.AlertType.ERROR, m, ButtonType.OK); a.setHeaderText(null); a.showAndWait(); }
    private SimpleStringProperty str(String s) { return new SimpleStringProperty(s); }
}