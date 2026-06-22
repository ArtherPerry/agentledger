package com.agentledger.ui.close;

import com.agentledger.model.Account;
import com.agentledger.repo.AccountRepo;
import com.agentledger.repo.DailyCloseRepo;
import com.agentledger.repo.LedgerRepo;
import com.agentledger.service.Permissions;
import com.agentledger.service.Session;
import com.agentledger.utils.Money;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;

import java.util.ArrayList;
import java.util.List;
import com.agentledger.i18n.I18n;
import com.agentledger.i18n.Fmt;

public class CloseController {

    @FXML private GridPane grid;
    @FXML private Label statusBadge, totalDiff;
    @FXML private HBox warnBox;
    @FXML private TextField reason;
    @FXML private Button closeBtn, reopenBtn;

    private record Line(Account account, long expected, TextField actualField) {}
    private final List<Line> lines = new ArrayList<>();
    private boolean closed;

    @FXML
    public void initialize() {
        try {
            closed = DailyCloseRepo.isTodayClosed(Session.branchId());
        } catch (Exception ex) {
            com.agentledger.utils.Log.error(ex);
            closed = true;   // fail safe: can't verify -> treat as closed so the close action is blocked
        }
        buildGrid();
        applyState();
        recompute();
    }

    private void buildGrid() {
        grid.getChildren().clear();
        lines.clear();
        // header row
        grid.add(hdr(I18n.t("close.col.account")), 0, 0);
        grid.add(hdr(I18n.t("close.col.expected")), 1, 0);
        grid.add(hdr(I18n.t("close.col.actual")), 2, 0);
        grid.add(hdr(I18n.t("close.col.diff")), 3, 0);

        int row = 1;
        for (Account a : AccountRepo.listForBranch(Session.branchId())) {
            long expected;
            try {
                expected = a.isDigital()
                        ? LedgerRepo.accountDigitalPya(a.id())
                        : LedgerRepo.branchCashPya(Session.branchId());
            } catch (Exception ex) {
                com.agentledger.utils.Log.error(ex);
                // Can't read a real balance — do NOT let the user close against bad data.
                closed = true;                 // reuse closed-state to disable the close button
                applyState();
                new Alert(Alert.AlertType.ERROR, I18n.t("close.err.balanceUnavailable"), ButtonType.OK)
                        .showAndWait();
                return;                        // abort building the grid
            }
            TextField actual = new TextField(stripDecimals(expected));
            actual.setDisable(closed);
            actual.textProperty().addListener((o, x, y) -> recompute());
            Label diff = new Label(Money.format(0));
            diff.setId("diff" + a.id());
            grid.add(new Label(a.name()), 0, row);
            grid.add(new Label(Money.format(expected)), 1, row);
            grid.add(actual, 2, row);
            grid.add(diff, 3, row);

            lines.add(new Line(a, expected, actual));
            row++;
        }
    }

    private void recompute() {
        long total = 0;
        for (int i = 0; i < lines.size(); i++) {
            Line ln = lines.get(i);
            long actual = Money.parse(ln.actualField().getText());
            long d = actual - ln.expected();
            total += d;
            Label diff = (Label) grid.lookup("#diff" + ln.account().id());
            if (diff != null) {
                diff.setText(Money.format(d));
                diff.setStyle("-fx-font-weight:bold;-fx-text-fill:" +
                        (d == 0 ? "#5C6967" : (d < 0 ? "#791F1F" : "#2C6E2C")) + ";");
            }
        }
        totalDiff.setText(Fmt.kyat(total));
        totalDiff.setStyle("-fx-font-weight:bold;-fx-text-fill:" +
                (total == 0 ? "#0C3B3C" : (total < 0 ? "#791F1F" : "#2C6E2C")) + ";");
        boolean hasDiff = total != 0;
        warnBox.setVisible(hasDiff && !closed);
        warnBox.setManaged(hasDiff && !closed);
    }

    private void applyState() {
        if (closed) {
            statusBadge.setText(I18n.t("close.status.closed"));
            statusBadge.setStyle("-fx-background-color:#E6F1DC;-fx-text-fill:#2C6E2C;-fx-padding:3 10 3 10;-fx-background-radius:99;-fx-font-size:11px;");
            closeBtn.setDisable(true);
            reason.setDisable(true);
            boolean canReopen = Permissions.canReopen();
            reopenBtn.setVisible(canReopen);
            reopenBtn.setManaged(canReopen);
        } else {
            closeBtn.setDisable(!Permissions.canClose());
            if (!Permissions.canClose())
                closeBtn.setText(I18n.t("close.btn.cannotClose"));
        }
    }

    @FXML
    private void onClose() {
        long expectedCashTotal = 0, actualCashTotal = 0, total = 0;
        List<long[]> lineData = new ArrayList<>();
        long cashExpected = 0, cashActual = 0;

        for (Line ln : lines) {
            long actual = Money.parse(ln.actualField().getText());
            total += (actual - ln.expected());
            lineData.add(new long[]{ln.account().id(), ln.expected(), actual});
            if (!ln.account().isDigital()) { cashExpected = ln.expected(); cashActual = actual; }
        }

        if (total != 0 && reason.getText().isBlank()) {
            error(I18n.t("close.msg.reasonRequired"));
            return;
        }
        try {
            DailyCloseRepo.close(Session.branchId(), Session.user().id(),
                    cashExpected, cashActual, lineData, total, reason.getText());
            closed = true;
            for (Line ln : lines) ln.actualField().setDisable(true);
            applyState();
            recompute();
            info(I18n.t("close.msg.closed"));
        } catch (Exception ex) {
            error(ex.getMessage());
        }
    }

    @FXML
    private void onReopen() {
        try {
            DailyCloseRepo.reopenToday(Session.branchId(), Session.user().id());
            closed = false;
            buildGrid();
            applyState();
            recompute();
            info(I18n.t("close.msg.reopened"));
        } catch (Exception ex) {
            error(ex.getMessage());
        }
    }

    private Label hdr(String t) {
        Label l = new Label(t);
        l.setStyle("-fx-font-size:11px;-fx-text-fill:#93A09D;-fx-font-weight:bold;");
        return l;
    }

    /** Show a whole-kyat default in the actual field (no forced decimals). */
    private String stripDecimals(long pya) {
        return Money.format(pya);
    }

    private void info(String m) { Alert a = new Alert(Alert.AlertType.INFORMATION, m, ButtonType.OK); a.setHeaderText(null); a.showAndWait(); }
    private void error(String m) { Alert a = new Alert(Alert.AlertType.ERROR, m, ButtonType.OK); a.setHeaderText(null); a.showAndWait(); }
}