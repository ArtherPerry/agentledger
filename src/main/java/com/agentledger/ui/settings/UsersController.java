package com.agentledger.ui.settings;

import com.agentledger.model.UserRow;
import com.agentledger.repo.UserRepo;
import com.agentledger.service.Permissions;
import com.agentledger.service.Session;
import com.agentledger.service.UserService;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import com.agentledger.i18n.I18n;

public class UsersController {

    @FXML private TableView<UserRow> table;
    @FXML private TableColumn<UserRow, String> colName, colUser, colRole, colActive;
    @FXML private TableColumn<UserRow, Void> colAction;
    @FXML private Button addBtn;
    @FXML private Label hint;

    @FXML
    public void initialize() {
        colName.setCellValueFactory(d -> str(d.getValue().name()));
        colUser.setCellValueFactory(d -> str(d.getValue().username()));
        colRole.setCellValueFactory(d -> str(d.getValue().roleMm()));
        colActive.setCellValueFactory(d -> str(d.getValue().active() ? I18n.t("users.status.active") : I18n.t("users.status.inactive")));

        boolean canManage = Permissions.canManageUsers();
        addBtn.setDisable(!canManage);
        hint.setText(canManage ? I18n.t("users.hint.canManage") : I18n.t("users.hint.viewOnly"));
        addActionColumn(canManage);
        load();
    }

    private void load() {
        table.setItems(FXCollections.observableArrayList(UserRepo.listForBranch(Session.branchId())));
    }

    private void addActionColumn(boolean canManage) {
        colAction.setCellFactory(col -> new TableCell<>() {
            private final Button edit = new Button(I18n.t("common.edit"));
            { edit.setStyle("-fx-font-size:11px;");
                edit.setOnAction(e -> openDialog(getTableView().getItems().get(getIndex()))); }
            @Override protected void updateItem(Void v, boolean empty) {
                super.updateItem(v, empty);
                setGraphic(empty || !canManage ? null : new HBox(edit));
            }
        });
    }

    @FXML private void onAdd() { openDialog(null); }

    private void openDialog(UserRow existing) {
        boolean editing = existing != null;
        Dialog<Void> dlg = new Dialog<>();
        dlg.setTitle(editing ? I18n.t("users.dialog.edit") : I18n.t("users.dialog.add"));
        ButtonType ok = new ButtonType(I18n.t("common.save"), ButtonBar.ButtonData.OK_DONE);
        dlg.getDialogPane().getButtonTypes().addAll(ok, ButtonType.CANCEL);

        TextField name = new TextField(editing ? existing.name() : "");
        TextField username = new TextField(editing ? existing.username() : "");
        username.setDisable(editing); // username fixed after creation
        PasswordField pwd = new PasswordField();
        pwd.setPromptText(editing ? I18n.t("users.pwd.placeholder.edit") : I18n.t("common.password"));
        ComboBox<String> role = new ComboBox<>();
        role.getItems().addAll("owner", "manager", "cashier");
        role.setValue(editing ? existing.role() : "cashier");
        CheckBox active = new CheckBox("Active");
        active.setSelected(!editing || existing.active());

        GridPane g = new GridPane(); g.setHgap(10); g.setVgap(10);
        g.addRow(0, new Label(I18n.t("common.name")), name);
        g.addRow(1, new Label(I18n.t("common.username")), username);
        g.addRow(2, new Label(I18n.t("common.password")), pwd);
        g.addRow(3, new Label(I18n.t("users.field.role")), role);
        if (editing) g.addRow(4, new Label(I18n.t("users.field.status")), active);
        dlg.getDialogPane().setContent(g);

        dlg.setResultConverter(bt -> {
            if (bt == ok) {
                try {
                    if (editing)
                        UserService.update(existing, name.getText(), role.getValue(),
                                active.isSelected(), pwd.getText().isBlank() ? null : pwd.getText());
                    else
                        UserService.create(name.getText(), username.getText(), pwd.getText(), role.getValue());
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