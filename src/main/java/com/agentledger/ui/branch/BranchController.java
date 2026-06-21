package com.agentledger.ui.branch;

import com.agentledger.model.Branch;
import com.agentledger.nav.Router;
import com.agentledger.nav.View;
import com.agentledger.service.BranchService;
import com.agentledger.service.Session;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.layout.VBox;

public class BranchController {

    @FXML private VBox branchBox;

    @FXML
    public void initialize() {
        for (Branch b : BranchService.listActive()) {
            Button btn = new Button(b.name());
            btn.setPrefWidth(260);
            btn.setPrefHeight(46);
            btn.setStyle("-fx-font-size:14px;-fx-background-color:white;-fx-border-color:#d2d8d6;-fx-border-radius:10;-fx-background-radius:10;");
            btn.setOnAction(e -> {
                Session.setBranch(b.id(), b.name());
                Router.to(View.LOGIN);
            });
            branchBox.getChildren().add(btn);
        }
    }
}