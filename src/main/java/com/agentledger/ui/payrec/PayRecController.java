package com.agentledger.ui.payrec;

import com.agentledger.model.Debt;
import com.agentledger.repo.DebtRepo;
import com.agentledger.service.DebtService;
import com.agentledger.service.Session;
import com.agentledger.utils.Money;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import com.agentledger.i18n.I18n;
import com.agentledger.i18n.Fmt;
import java.util.List;

public class PayRecController {

    @FXML private ToggleButton tabRecv, tabPay, tabSettled;
    @FXML private Label totalLabel, totalValue;
    @FXML private VBox listBox;

    @FXML
    public void initialize() {
        ToggleGroup g = new ToggleGroup();
        tabRecv.setToggleGroup(g); tabPay.setToggleGroup(g); tabSettled.setToggleGroup(g);
        g.selectedToggleProperty().addListener((o, a, b) -> { if (b == null) a.setSelected(true); else load(); });
        load();
    }

    private String currentKind() { return tabPay.isSelected() ? "payable" : "receivable"; }
    private boolean settledView() { return tabSettled.isSelected(); }

    private void load() {
        int branch = Session.branchId();
        String kind = settledView() ? "receivable" : currentKind(); // settled view: show both? keep simple -> receivable+payable
        listBox.getChildren().clear();

        if (settledView()) {
            renderList(DebtRepo.list(branch, "receivable", true));
            renderList(DebtRepo.list(branch, "payable", true));
            totalLabel.setText(I18n.t("payrec.total.settled"));
            totalValue.setText("—");
            totalValue.setStyle("-fx-font-size:16px;-fx-font-weight:bold;-fx-text-fill:#5C6967;");
        } else {
            renderList(DebtRepo.list(branch, kind, false));
            long total = DebtRepo.totalRemaining(branch, kind);
            totalLabel.setText(kind.equals("payable") ? I18n.t("payrec.total.payable") : I18n.t("payrec.total.receivable"));
            totalValue.setText(Fmt.kyat(total));
            totalValue.setStyle("-fx-font-size:16px;-fx-font-weight:bold;-fx-text-fill:#A8312B;");
        }
        if (listBox.getChildren().isEmpty())
            listBox.getChildren().add(new Label(I18n.t("payrec.empty")));
    }

    private void renderList(List<Debt> debts) {
        for (Debt d : debts) listBox.getChildren().add(card(d));
    }

    private VBox card(Debt d) {
        VBox box = new VBox(6);
        box.setStyle("-fx-background-color:white;-fx-background-radius:12;-fx-border-color:#e5e9e7;-fx-border-radius:12;-fx-padding:14;");

        HBox top = new HBox(8);
        Label name = new Label(d.counterpartyName());
        name.setStyle("-fx-font-size:14px;-fx-font-weight:bold;");
        Label tag = new Label("agent".equals(d.counterpartyType()) ? I18n.t("cp.agent") : I18n.t("cp.customer"));
        tag.setStyle("-fx-background-color:#f0f0f0;-fx-text-fill:#555;-fx-padding:1 8 1 8;-fx-background-radius:99;-fx-font-size:11px;");
        Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);
        Label remain = new Label(I18n.t("payrec.card.remaining", Fmt.kyat(d.remainingPya())));
        remain.setStyle("-fx-font-size:14px;-fx-font-weight:bold;-fx-text-fill:#A8312B;");
        top.getChildren().addAll(name, tag, sp, remain);
        top.setAlignment(Pos.CENTER_LEFT);

        Label sub = new Label(I18n.t("payrec.card.progress", Fmt.kyat(d.paidPya()), Fmt.kyat(d.originalPya()))
                + "   ·   " + d.createdAt());
        sub.setStyle("-fx-font-size:11px;-fx-text-fill:#5C6967;");

        ProgressBar bar = new ProgressBar(d.progress());
        bar.setMaxWidth(Double.MAX_VALUE);
        bar.setPrefHeight(6);

        box.getChildren().addAll(top, sub, bar);

        if (!"settled".equals(d.status())) {
            HBox actions = new HBox(8);
            Button full = new Button(I18n.t("payrec.btn.fullRepay"));
            Button part = new Button(I18n.t("payrec.btn.partialRepay"));
            full.setOnAction(e -> doRepay(d, d.remainingPya()));
            part.setOnAction(e -> partialRepay(d));
            actions.getChildren().addAll(full, part);
            box.getChildren().add(actions);
        } else {
            Label done = new Label(I18n.t("payrec.card.settled"));
            done.setStyle("-fx-text-fill:#2C6E2C;-fx-font-size:12px;");
            box.getChildren().add(done);
        }
        return box;
    }

    private void partialRepay(Debt d) {
        TextInputDialog dlg = new TextInputDialog();
        dlg.setTitle(I18n.t("payrec.partial.title"));
        dlg.setHeaderText(I18n.t("payrec.partial.header", d.counterpartyName(), Fmt.kyat(d.remainingPya())));
        dlg.setContentText(I18n.t("payrec.partial.amount"));
        dlg.showAndWait().ifPresent(s -> doRepay(d, Money.parse(s)));
    }

    private void doRepay(Debt d, long amt) {
        try {
            DebtService.repay(d.id(), amt);
            load();
        } catch (Exception ex) {
            err(ex.getMessage());
        }
    }

    @FXML
    private void onAdd() {
        Dialog<Void> dlg = new Dialog<>();
        dlg.setTitle(I18n.t("payrec.add.title"));
        ButtonType ok = new ButtonType(I18n.t("common.save"), ButtonBar.ButtonData.OK_DONE);
        dlg.getDialogPane().getButtonTypes().addAll(ok, ButtonType.CANCEL);

        ComboBox<String> kind = new ComboBox<>();
        kind.getItems().addAll(I18n.t("payrec.add.kind.recv"), I18n.t("payrec.add.kind.pay"));
        kind.getSelectionModel().selectFirst();
        ComboBox<String> type = new ComboBox<>();
        type.getItems().addAll(I18n.t("cp.customer"), I18n.t("cp.agent"));
        type.getSelectionModel().selectFirst();
        TextField name = new TextField(); name.setPromptText(I18n.t("common.name"));
        TextField phone = new TextField(); phone.setPromptText(I18n.t("payrec.phone.optional"));
        TextField amount = new TextField(); amount.setPromptText(I18n.t("common.amountKyat"));
        com.agentledger.utils.Numeric.money(amount);
        com.agentledger.utils.Numeric.phone(phone);

        GridPane g = new GridPane(); g.setHgap(10); g.setVgap(10);
        g.addRow(0, new Label(I18n.t("payrec.field.kind")), kind);
        g.addRow(1, new Label(I18n.t("payrec.field.person")), type);
        g.addRow(2, new Label(I18n.t("common.name")), name);
        g.addRow(3, new Label(I18n.t("payrec.field.phone")), phone);
        g.addRow(4, new Label(I18n.t("payrec.field.amount")), amount);
        dlg.getDialogPane().setContent(g);

        dlg.setResultConverter(bt -> {
            if (bt == ok) {
                try {
                    String k = kind.getSelectionModel().getSelectedIndex() == 1 ? "payable" : "receivable";
                    String t = type.getSelectionModel().getSelectedIndex() == 1 ? "agent" : "customer";
                    DebtService.create(k, name.getText(), t, phone.getText(), Money.parse(amount.getText()), null);
                    load();
                } catch (Exception ex) { err(ex.getMessage()); }
            }
            return null;
        });
        dlg.showAndWait();
    }

    private void err(String m) { Alert a = new Alert(Alert.AlertType.ERROR, m, ButtonType.OK); a.setHeaderText(null); a.showAndWait(); }
}