package com.agentledger.ui.settings;

import com.agentledger.model.Branch;
import com.agentledger.service.BranchService;
import com.agentledger.service.Permissions;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import com.agentledger.i18n.I18n;

public class BranchesController {

    @FXML private TableView<Branch> table;
    @FXML private TableColumn<Branch, String> colId, colName;
    @FXML private TableColumn<Branch, Void> colAction;
    @FXML private Button addBtn;
    @FXML private Label hint;

    @FXML
    public void initialize() {
        colId.setCellValueFactory(d -> str(String.valueOf(d.getValue().id())));
        colName.setCellValueFactory(d -> str(d.getValue().name()));

        boolean canManage = Permissions.canManageBranches();
        addBtn.setDisable(!canManage);
        if (!canManage) hint.setText(I18n.t("branches.hint.viewOnly"));
        addActionColumn(canManage);
        load();
    }

    private void load() {
        table.setItems(FXCollections.observableArrayList(BranchService.listActive()));
    }

    private void addActionColumn(boolean canManage) {
        colAction.setCellFactory(col -> new TableCell<>() {
            private final Button edit = new Button(I18n.t("common.edit"));
            { edit.setStyle("-fx-font-size:11px;");
                edit.setOnAction(e -> rename(getTableView().getItems().get(getIndex()))); }
            @Override protected void updateItem(Void v, boolean empty) {
                super.updateItem(v, empty);
                setGraphic(empty || !canManage ? null : new HBox(edit));
            }
        });
    }

    private void rename(Branch b) {
        TextInputDialog dlg = new TextInputDialog(b.name());
        dlg.setTitle(I18n.t("branches.rename.title"));
        dlg.setHeaderText(null);
        dlg.setContentText(I18n.t("branches.rename.prompt"));
        dlg.showAndWait().ifPresent(name -> {
            try { BranchService.rename(b.id(), name); load(); }
            catch (Exception ex) { err(ex.getMessage()); }
        });
    }

    @FXML
    private void onAdd() {
        Dialog<Void> dlg = new Dialog<>();
        dlg.setTitle(I18n.t("branches.dialog.add"));
        ButtonType ok = new ButtonType(I18n.t("common.create"), ButtonBar.ButtonData.OK_DONE);
        dlg.getDialogPane().getButtonTypes().addAll(ok, ButtonType.CANCEL);

        TextField branchName = new TextField();
        TextField ownerName = new TextField();
        TextField ownerUser = new TextField();
        PasswordField ownerPwd = new PasswordField();

        GridPane g = new GridPane(); g.setHgap(10); g.setVgap(10);
        g.addRow(0, new Label(I18n.t("branches.field.branchName")), branchName);
        g.addRow(1, new Label(I18n.t("branches.field.ownerName")), ownerName);
        g.addRow(2, new Label(I18n.t("branches.field.ownerUser")), ownerUser);
        g.addRow(3, new Label(I18n.t("branches.field.ownerPwd")), ownerPwd);
        dlg.getDialogPane().setContent(g);

        dlg.setResultConverter(bt -> {
            if (bt == ok) {
                try {
                    BranchService.create(branchName.getText(), ownerName.getText(),
                            ownerUser.getText(), ownerPwd.getText());
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