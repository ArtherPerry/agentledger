package com.agentledger.ui.reports;

import com.agentledger.i18n.Fmt;
import com.agentledger.i18n.I18n;
import com.agentledger.model.Debt;
import com.agentledger.model.PlatformRow;
import com.agentledger.model.ReportSummary;
import com.agentledger.repo.ReportRepo;
import com.agentledger.service.Session;
import com.agentledger.utils.Money;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.util.StringConverter;

import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.Locale;

public class ReportsController {

    // Stable codes (identity) — NOT display text. Labels come from I18n at render time.
    private static final String DAILY = "DAILY";
    private static final String WEEKLY = "WEEKLY";
    private static final String MONTHLY = "MONTHLY";
    private static final String COMMISSION = "COMMISSION";
    private static final String PAYREC = "PAYREC";

    @FXML private ComboBox<String> reportType;
    @FXML private DatePicker asOf, fromDate, toDate;
    @FXML private Label asOfLabel, tableTitle, sCount, sFee, sComm;
    @FXML private HBox cardsRow;

    @FXML private TableView<PlatformRow> platformTable;
    @FXML private TableColumn<PlatformRow, String> colPlatform, colCount, colAmount, colComm;

    @FXML private TableView<Debt> debtTable;
    @FXML private TableColumn<Debt, String> colKind, colName, colOriginal, colPaid, colRemaining;

    @FXML private Button xlsBtn, pdfBtn;

    @FXML
    public void initialize() {
        reportType.setItems(FXCollections.observableArrayList(DAILY, WEEKLY, MONTHLY, COMMISSION, PAYREC));
        // show the translated label for each code, but the stored value stays the code
        reportType.setConverter(new StringConverter<>() {
            @Override public String toString(String code) {
                if (code == null) return "";
                return switch (code) {
                    case DAILY -> I18n.t("reports.type.daily");
                    case WEEKLY -> I18n.t("reports.type.weekly");
                    case MONTHLY -> I18n.t("reports.type.monthly");
                    case COMMISSION -> I18n.t("reports.type.commission");
                    case PAYREC -> I18n.t("reports.type.payrec");
                    default -> code;
                };
            }
            @Override public String fromString(String s) { return s; }
        });
        reportType.getSelectionModel().select(DAILY);
        asOf.setValue(LocalDate.now());

        colPlatform.setCellValueFactory(d -> str(d.getValue().platform()));
        colCount.setCellValueFactory(d -> str(String.valueOf(d.getValue().count())));
        colAmount.setCellValueFactory(d -> str(Fmt.kyat(d.getValue().amountPya())));
        colComm.setCellValueFactory(d -> str(Fmt.kyat(d.getValue().commissionPya())));

        colKind.setCellValueFactory(d -> str("receivable".equals(d.getValue().kind())
                ? I18n.t("reports.kind.receivable") : I18n.t("reports.kind.payable")));
        colName.setCellValueFactory(d -> str(d.getValue().counterpartyName()));
        colOriginal.setCellValueFactory(d -> str(Fmt.kyat(d.getValue().originalPya())));
        colPaid.setCellValueFactory(d -> str(Fmt.kyat(d.getValue().paidPya())));
        colRemaining.setCellValueFactory(d -> str(Fmt.kyat(d.getValue().remainingPya())));

        reportType.valueProperty().addListener((o, a, b) -> { prefillDates(); onRun(); });
        asOf.valueProperty().addListener((o, a, b) -> { if (usesAsOf()) prefillDates(); });

        prefillDates();
        onRun();

        xlsBtn.setOnAction(e -> exportExcel());
        pdfBtn.setOnAction(e -> exportPdf());
    }

    private boolean usesAsOf() {
        String t = reportType.getValue();
        return DAILY.equals(t) || WEEKLY.equals(t) || MONTHLY.equals(t);
    }

    private boolean isPayRec() { return PAYREC.equals(reportType.getValue()); }

    private void prefillDates() {
        LocalDate d = asOf.getValue() == null ? LocalDate.now() : asOf.getValue();
        String t = reportType.getValue();

        boolean showAsOf = usesAsOf();
        asOf.setDisable(!showAsOf);
        asOfLabel.setDisable(!showAsOf);

        switch (t) {
            case DAILY -> { fromDate.setValue(d); toDate.setValue(d); }
            case WEEKLY -> {
                WeekFields wf = WeekFields.of(Locale.getDefault());
                LocalDate start = d.with(wf.dayOfWeek(), 1);
                fromDate.setValue(start); toDate.setValue(start.plusDays(6));
            }
            case MONTHLY -> { fromDate.setValue(d.withDayOfMonth(1)); toDate.setValue(d.withDayOfMonth(d.lengthOfMonth())); }
            case COMMISSION -> {
                if (fromDate.getValue() == null) fromDate.setValue(d.withDayOfMonth(1));
                if (toDate.getValue() == null) toDate.setValue(d);
            }
            case PAYREC -> { /* no date range */ }
        }
    }

    @FXML
    private void onRun() {
        int branch = Session.branchId();

        if (isPayRec()) {
            cardsRow.setVisible(false); cardsRow.setManaged(false);
            platformTable.setVisible(false); platformTable.setManaged(false);
            debtTable.setVisible(true); debtTable.setManaged(true);
            tableTitle.setText(I18n.t("reports.title.payrecRemaining"));
            debtTable.setItems(FXCollections.observableArrayList(ReportRepo.outstandingDebts(branch)));
            return;
        }

        cardsRow.setVisible(true); cardsRow.setManaged(true);
        platformTable.setVisible(true); platformTable.setManaged(true);
        debtTable.setVisible(false); debtTable.setManaged(false);

        String from = fromDate.getValue().toString();
        String to = toDate.getValue().toString();

        ReportSummary s = ReportRepo.summary(branch, from, to);
        sCount.setText(String.valueOf(s.txnCount()));
        sFee.setText(Fmt.kyat(s.totalFeePya()));
        sComm.setText(Fmt.kyat(s.totalCommissionPya()));

        tableTitle.setText(COMMISSION.equals(reportType.getValue())
                ? I18n.t("reports.title.platformComm") : I18n.t("reports.title.platform"));
        platformTable.setItems(FXCollections.observableArrayList(ReportRepo.byPlatform(branch, from, to)));
    }

    private void exportExcel() {
        if (isPayRec()) { info(I18n.t("reports.export.notAvailable")); return; }
        try {
            String from = fromDate.getValue().toString(), to = toDate.getValue().toString();
            var s = ReportRepo.summary(Session.branchId(), from, to);
            var rows = ReportRepo.byPlatform(Session.branchId(), from, to);
            java.io.File f = com.agentledger.utils.Files2.chooseSave(
                    xlsBtn.getScene().getWindow(), I18n.t("reports.export.excelTitle"),
                    "report_" + from + "_" + to + ".xlsx", "Excel", "xlsx");
            if (f == null) return;
            com.agentledger.service.ExcelExport.writeSummary(f, Session.branchName(), from, to, s, rows);
            info(I18n.t("reports.export.excelDone", f.getAbsolutePath()));
        } catch (Exception ex) { err(ex.getMessage()); }
    }

    private void exportPdf() {
        if (isPayRec()) { info(I18n.t("reports.export.notAvailable")); return; }
        try {
            String from = fromDate.getValue().toString(), to = toDate.getValue().toString();
            var s = ReportRepo.summary(Session.branchId(), from, to);
            var rows = ReportRepo.byPlatform(Session.branchId(), from, to);
            java.io.File f = com.agentledger.utils.Files2.chooseSave(
                    pdfBtn.getScene().getWindow(), I18n.t("reports.export.pdfTitle"),
                    "report_" + from + "_" + to + ".pdf", "PDF", "pdf");
            if (f == null) return;
            com.agentledger.service.PdfExport.writeSummary(f, Session.branchName(), from, to, s, rows);
            info(I18n.t("reports.export.pdfDone", f.getAbsolutePath()));
        } catch (Exception ex) { err(ex.getMessage()); }
    }

    private void info(String m) { Alert a = new Alert(Alert.AlertType.INFORMATION, m, ButtonType.OK); a.setHeaderText(null); a.showAndWait(); }
    private void err(String m) { Alert a = new Alert(Alert.AlertType.ERROR, m, ButtonType.OK); a.setHeaderText(null); a.showAndWait(); }

    private SimpleStringProperty str(String s) { return new SimpleStringProperty(s); }
}