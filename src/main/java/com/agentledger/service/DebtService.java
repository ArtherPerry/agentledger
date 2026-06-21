package com.agentledger.service;

import com.agentledger.i18n.I18n;

import com.agentledger.db.Database;
import com.agentledger.model.Account;
import com.agentledger.model.TxnType;
import com.agentledger.repo.AccountRepo;
import com.agentledger.repo.CounterpartyRepo;
import com.agentledger.repo.DailyCloseRepo;
import com.agentledger.repo.TxnTypeRepo;

import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class DebtService {
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private DebtService() {}

    public static void create(String kind, String cpName, String cpType, String phone,
                              long amountPya, String note) throws Exception {
        if (!Permissions.canManageDebts()) throw new IllegalStateException(I18n.t("error.noPermission"));
        if (cpName == null || cpName.isBlank()) throw new IllegalArgumentException(I18n.t("error.nameRequired"));
        if (amountPya <= 0) throw new IllegalArgumentException(I18n.t("error.amountRequired"));

        int branch = Session.branchId();
        int cpId = CounterpartyRepo.findOrCreate(branch, cpName.trim(), cpType, phone);
        Connection c = Database.get();
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO debts(branch_id,counterparty_id,kind,original_pya,due_at,status,created_at,created_by) " +
                        "VALUES (?,?,?,?,?, 'open', ?, ?)")) {
            ps.setInt(1, branch); ps.setInt(2, cpId); ps.setString(3, kind);
            ps.setLong(4, amountPya); ps.setNull(5, Types.VARCHAR);
            ps.setString(6, LocalDate.now().toString()); ps.setInt(7, Session.user().id());
            ps.executeUpdate();
        }
        // Distinct action code per kind so the activity log reads clearly (ရရန်/ပေးရန်).
        String action = "receivable".equals(kind) ? "DEBT_CREATE_RECV" : "DEBT_CREATE_PAY";
        ActivityLog.record(c, branch, Session.user().id(), action,
                cpName + " " + com.agentledger.utils.Money.format(amountPya));
    }

    /** Repay (full or partial). Posts a cash ledger entry, records the payment, auto-settles. */
    public static void repay(long debtId, long amountPya) throws Exception {
        if (!Permissions.canManageDebts()) throw new IllegalStateException(I18n.t("error.noPermission"));
        if (amountPya <= 0) throw new IllegalArgumentException(I18n.t("error.amountRequired"));
        if (DailyCloseRepo.isTodayClosed(Session.branchId()))
            throw new IllegalStateException(I18n.t("error.dayClosed.action"));

        Connection c = Database.get();
        boolean oldAuto = c.getAutoCommit();
        c.setAutoCommit(false);
        try {
            // load debt kind + remaining
            String kind; long original, paid;
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT kind, original_pya, " +
                            "COALESCE((SELECT SUM(amount_pya) FROM debt_payments WHERE debt_id=?),0) " +
                            "FROM debts WHERE id=?")) {
                ps.setLong(1, debtId); ps.setLong(2, debtId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) throw new IllegalStateException(I18n.t("error.notFound"));
                    kind = rs.getString(1); original = rs.getLong(2); paid = rs.getLong(3);
                }
            }
            long remaining = original - paid;
            if (amountPya > remaining) throw new IllegalArgumentException(I18n.t("error.overpayment"));

            // post a cash ledger entry (receivable repaid -> cash in; payable repaid -> cash out)
            boolean receivable = "receivable".equals(kind);
            String typeName = receivable ? TxnType.REPAY_RECEIVABLE : TxnType.REPAY_PAYABLE;
            TxnType type = TxnTypeRepo.byName(Session.branchId(), typeName);
            Account cash = AccountRepo.listForBranch(Session.branchId()).stream()
                    .filter(a -> !a.isDigital()).findFirst()
                    .orElseThrow(() -> new IllegalStateException(I18n.t("error.noCashAccount")));

            PostingRequest r = new PostingRequest();
            r.type = type; r.account = cash; r.amountPya = amountPya;
            r.note = typeName + " (#debt " + debtId + ")";
            long entryId = LedgerService.postInTx(c, r);

            // record payment
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO debt_payments(debt_id,amount_pya,paid_at,paid_by,entry_id) VALUES (?,?,?,?,?)")) {
                ps.setLong(1, debtId); ps.setLong(2, amountPya);
                ps.setString(3, LocalDateTime.now().format(TS));
                ps.setInt(4, Session.user().id()); ps.setLong(5, entryId);
                ps.executeUpdate();
            }
            // settle if fully paid
            if (amountPya == remaining) {
                try (PreparedStatement ps = c.prepareStatement("UPDATE debts SET status='settled' WHERE id=?")) {
                    ps.setLong(1, debtId); ps.executeUpdate();
                }
            }
            // Distinct action code per kind so the activity log reads clearly (ရရန်/ပေးရန်).
            String action = receivable ? "DEBT_REPAY_RECV" : "DEBT_REPAY_PAY";
            ActivityLog.record(c, Session.branchId(), Session.user().id(), action,
                    "#debt " + debtId + " " + com.agentledger.utils.Money.format(amountPya));
            c.commit();
        } catch (Exception e) {
            c.rollback();
            throw e;
        } finally {
            c.setAutoCommit(oldAuto);
        }
    }
}