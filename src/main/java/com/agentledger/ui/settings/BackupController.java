package com.agentledger.ui.settings;

import com.agentledger.model.BackupRow;
import com.agentledger.service.BackupService;
import com.agentledger.service.Permissions;
import com.agentledger.utils.Files2;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.stage.FileChooser;

import java.io.File;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import com.agentledger.i18n.I18n;

public class BackupController {

    @FXML private HBox reminder;
    @FXML private Label reminderText, lastLabel;
    @FXML private Button backupBtn, restoreBtn;
    @FXML private TableView<BackupRow> table;
    @FXML private TableColumn<BackupRow, String> colTs, colLoc, colSize, colVerified;

    @FXML
    public void initialize() {
        boolean can = Permissions.canBackup();
        backupBtn.setDisable(!can);
        restoreBtn.setDisable(!can);

        colTs.setCellValueFactory(d -> str(d.getValue().ts()));
        colLoc.setCellValueFactory(d -> str(d.getValue().location()));
        colSize.setCellValueFactory(d -> str(d.getValue().sizeKb()));
        colVerified.setCellValueFactory(d -> str(d.getValue().verified() ? I18n.t("backup.verified") : "—"));

        refresh();
    }

    private void refresh() {
        table.setItems(FXCollections.observableArrayList(BackupService.history(100)));
        String last = BackupService.lastBackupTs();
        lastLabel.setText(last == null ? I18n.t("backup.last.none") : I18n.t("backup.last.at", last));
        showReminderIfStale(last);
    }

    private void showReminderIfStale(String last) {
        boolean stale;
        if (last == null) stale = true;
        else {
            try {
                LocalDateTime t = LocalDateTime.parse(last, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                stale = t.toLocalDate().isBefore(LocalDate.now());   // nothing today
            } catch (Exception e) { stale = true; }
        }
        reminder.setVisible(stale); reminder.setManaged(stale);
        if (stale) reminderText.setText(I18n.t("backup.reminder.stale"));
    }

    @FXML
    private void onBackup() {
        String name = "AgentLedger_" + LocalDate.now() + ".db";
        File f = Files2.chooseSave(backupBtn.getScene().getWindow(), I18n.t("backup.save.title"), name, "Database", "db");
        if (f == null) return;
        try {
            BackupService.backupTo(f);
            info(I18n.t("backup.success", f.getAbsolutePath()));
            refresh();
        } catch (Exception ex) { err(ex.getMessage()); }
    }

    @FXML
    private void onRestore() {
        Alert warn = new Alert(Alert.AlertType.WARNING,
                I18n.t("backup.restore.confirm"),
                ButtonType.YES, ButtonType.NO);
        warn.setHeaderText(null); warn.showAndWait();
        if (warn.getResult() != ButtonType.YES) return;

        FileChooser fc = new FileChooser();
        fc.setTitle(I18n.t("backup.restore.choose"));
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Database", "*.db"));
        File f = fc.showOpenDialog(restoreBtn.getScene().getWindow());
        if (f == null) return;

        try {
            BackupService.restoreFrom(f);
            Alert a = new Alert(Alert.AlertType.INFORMATION,
                    I18n.t("backup.restore.done"), ButtonType.OK);
            a.setHeaderText(null); a.showAndWait();
            javafx.application.Platform.exit();   // force a clean restart
        } catch (Exception ex) { err(ex.getMessage()); }
    }

    private void info(String m) { Alert a = new Alert(Alert.AlertType.INFORMATION, m, ButtonType.OK); a.setHeaderText(null); a.showAndWait(); }
    private void err(String m) { Alert a = new Alert(Alert.AlertType.ERROR, m, ButtonType.OK); a.setHeaderText(null); a.showAndWait(); }
    private SimpleStringProperty str(String s) { return new SimpleStringProperty(s); }
}