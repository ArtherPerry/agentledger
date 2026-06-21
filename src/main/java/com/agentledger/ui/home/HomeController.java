package com.agentledger.ui.home;

import com.agentledger.model.LedgerRow;
import com.agentledger.model.TodayStats;
import com.agentledger.nav.Router;
import com.agentledger.nav.View;
import com.agentledger.repo.DailyCloseRepo;
import com.agentledger.repo.LedgerRepo;
import com.agentledger.service.Session;
import com.agentledger.utils.Money;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import com.agentledger.i18n.I18n;
import com.agentledger.i18n.Fmt;

public class HomeController {

    @FXML private HBox closeBanner;
    @FXML private Label statCount, statFee, statComm, statCash, statDigital;
    @FXML private TableView<LedgerRow> table;
    @FXML private TableColumn<LedgerRow, String> colTime, colType, colAcct, colAmount, colStatus;

    @FXML
    public void initialize() {
        int branch = Session.branchId();

        TodayStats st = LedgerRepo.todayStats(branch);
        statCount.setText(String.valueOf(st.count()));
        statFee.setText(Fmt.kyat(st.feePya()));
        statComm.setText(Fmt.kyat(st.commissionPya()));
        statCash.setText(Fmt.kyat(LedgerRepo.branchCashPya(branch)));
        statDigital.setText(Fmt.kyat(LedgerRepo.branchDigitalTotalPya(branch)));

        boolean closed = DailyCloseRepo.isTodayClosed(branch);
        closeBanner.setVisible(!closed);
        closeBanner.setManaged(!closed);

        colTime.setCellValueFactory(d -> str(d.getValue().time()));
        colType.setCellValueFactory(d -> str(d.getValue().typeName()));
        colAcct.setCellValueFactory(d -> str(d.getValue().accountName()));
        colAmount.setCellValueFactory(d -> str(Money.format(d.getValue().amountPya())));
        colStatus.setCellValueFactory(d -> str(status(d.getValue())));

        table.setItems(FXCollections.observableArrayList(LedgerRepo.recent(branch, 8)));
    }

    private String status(LedgerRow r) {
        if (r.isReversal()) return I18n.t("status.reversal");
        if (r.reversed())   return I18n.t("status.reversed");
        return I18n.t("status.done");
    }

    @FXML private void onGoClose() { Router.show(View.CLOSE); }

    private SimpleStringProperty str(String s) { return new SimpleStringProperty(s); }
}