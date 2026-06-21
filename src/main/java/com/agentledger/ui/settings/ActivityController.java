package com.agentledger.ui.settings;

import com.agentledger.model.ActivityRow;
import com.agentledger.repo.ActivityRepo;
import com.agentledger.service.Session;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;

public class ActivityController {

    @FXML private TableView<ActivityRow> table;
    @FXML private TableColumn<ActivityRow, String> colTs, colUser, colAction, colDetail;

    @FXML
    public void initialize() {
        colTs.setCellValueFactory(d -> str(d.getValue().ts()));
        colUser.setCellValueFactory(d -> str(d.getValue().userName()));
        colAction.setCellValueFactory(d -> str(d.getValue().actionMm()));
        colDetail.setCellValueFactory(d -> str(d.getValue().detail()));
        load();
    }

    private void load() {
        table.setItems(FXCollections.observableArrayList(
                ActivityRepo.recent(Session.branchId(), 300)));
    }

    @FXML private void onRefresh() { load(); }

    private SimpleStringProperty str(String s) { return new SimpleStringProperty(s); }
}