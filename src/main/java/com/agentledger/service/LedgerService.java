package com.agentledger.service;

import com.agentledger.i18n.I18n;

import com.agentledger.db.Database;
import com.agentledger.model.Account;
import com.agentledger.model.TxnType;
import com.agentledger.utils.Money;

import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class LedgerService {
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private LedgerService() {}

    /** Signed effect: 'in' -> +amount, 'out' -> -amount, 'none' -> 0. */
    private static long delta(String effect, long amount) {
        return switch (effect) {
            case "in" -> amount;
            case "out" -> -amount;
            default -> 0L;
        };
    }

    /** Public entry point: opens one transaction, posts, commits. */
    public static long post(PostingRequest r) throws Exception {
        // Hard close: no posting into a day that's already closed.
        if (com.agentledger.repo.DailyCloseRepo.isTodayClosed(Session.branchId()))
            throw new IllegalStateException(I18n.t("error.dayClosed.post"));

        Connection c = Database.get();
        boolean oldAuto = c.getAutoCommit();
        c.setAutoCommit(false);
        try {
            long id = postInTx(c, r);
            c.commit();
            return id;
        } catch (Exception e) {
            c.rollback();
            throw e;
        } finally {
            c.setAutoCommit(oldAuto);
        }
    }

    /**
     * Posts one immutable entry (two-sided for wallet-to-wallet) on the GIVEN connection,
     * WITHOUT committing. The caller owns the transaction, so other operations
     * (e.g. debt repayment) can post a ledger entry atomically with their own writes.
     * Returns the new primary entry id.
     */
    public static long postInTx(Connection c, PostingRequest r) throws Exception {
        // Validate before writing.
        if (r.type == null)    throw new IllegalArgumentException(I18n.t("error.typeRequired"));
        if (r.account == null) throw new IllegalArgumentException(I18n.t("error.accountRequired"));
        if (r.amountPya <= 0)  throw new IllegalArgumentException(I18n.t("error.amountRequired"));

        boolean walletToWallet = TxnType.WALLET_TO_WALLET.equals(r.type.name());
        if (walletToWallet) {
            if (r.toAccount == null) throw new IllegalArgumentException(I18n.t("error.toAccountRequired"));
            if (r.toAccount.id() == r.account.id()) throw new IllegalArgumentException(I18n.t("error.sameAccount"));
        }

        long cashDelta = delta(r.type.cashEffect(), r.amountPya);
        long digitalDelta = delta(r.type.digitalEffect(), r.amountPya);

        int branch = Session.branchId();
        int userId = Session.user().id();
        String now = LocalDateTime.now().format(TS);

        String sql = "INSERT INTO ledger_entries " +
                "(branch_id,account_id,to_account_id,type_id,amount_pya,fee_pya,commission_pya," +
                " cash_delta_pya,digital_delta_pya,sender_phone,receiver_phone,ref_no,note,created_at,created_by) " +
                "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";

        long id;
        // Primary entry (affects r.account).
        try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, branch);
            ps.setInt(2, r.account.id());
            if (walletToWallet) ps.setInt(3, r.toAccount.id()); else ps.setNull(3, Types.INTEGER);
            ps.setInt(4, r.type.id());
            ps.setLong(5, r.amountPya);
            ps.setLong(6, r.feePya);
            ps.setLong(7, r.commissionPya);
            ps.setLong(8, cashDelta);
            ps.setLong(9, digitalDelta);
            setOrNull(ps, 10, r.senderPhone);
            setOrNull(ps, 11, r.receiverPhone);
            setOrNull(ps, 12, r.refNo);
            setOrNull(ps, 13, r.note);
            ps.setString(14, now);
            ps.setInt(15, userId);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                id = keys.getLong(1);
            }
        }

        // Second leg for wallet-to-wallet: money lands in toAccount (digital +amount).
        if (walletToWallet) {
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setInt(1, branch);
                ps.setInt(2, r.toAccount.id());   // this leg affects the destination
                ps.setInt(3, r.account.id());     // reference back to the source
                ps.setInt(4, r.type.id());
                ps.setLong(5, r.amountPya);
                ps.setLong(6, 0);                 // fee/commission recorded once, on the primary leg
                ps.setLong(7, 0);
                ps.setLong(8, 0);                 // cash already 0 for this type
                ps.setLong(9, r.amountPya);       // destination wallet increases
                setOrNull(ps, 10, r.senderPhone);
                setOrNull(ps, 11, r.receiverPhone);
                setOrNull(ps, 12, r.refNo);
                setOrNull(ps, 13, "(လက်ခံ) " + (r.note == null ? "" : r.note));
                ps.setString(14, now);
                ps.setInt(15, userId);
                ps.executeUpdate();
            }
        }

        ActivityLog.record(c, branch, userId, r.feeOverridden ? "POST_OVERRIDE" : "POST",
                "#" + id + " " + r.type.name() + " " + r.account.name() +
                        " " + Money.format(r.amountPya) + " ကျပ်" +
                        (r.feeOverridden ? " [" + I18n.t("txn.overrideTag") + " " + Money.format(r.feePya) + "]" : ""));
        return id;
    }

    /**
     * Reverses an entry (and, for wallet-to-wallet, its second leg) by writing negated copies.
     * Allowed for Owner/Manager only. The group is identified by the primary entry id:
     * a leg either has id = primaryId, or to_account_id-based second leg created in the same post.
     */
    public static void reverse(long entryId) throws Exception {
        if (!Permissions.canReverse())
            throw new IllegalStateException(I18n.t("error.noReversePermission"));

        int userId = Session.user().id();
        Connection c = Database.get();
        boolean oldAuto = c.getAutoCommit();
        c.setAutoCommit(false);
        try {
            // already reversed?
            try (PreparedStatement chk = c.prepareStatement(
                    "SELECT 1 FROM ledger_entries WHERE reverses_id = ?")) {
                chk.setLong(1, entryId);
                try (ResultSet rs = chk.executeQuery()) {
                    if (rs.next()) throw new IllegalStateException(I18n.t("error.alreadyReversed"));
                }
            }

            // load the original (+ its sibling leg, if wallet-to-wallet)
            String load = "SELECT * FROM ledger_entries WHERE id = ? OR " +
                    "(account_id = (SELECT to_account_id FROM ledger_entries WHERE id = ?) " +
                    " AND to_account_id = (SELECT account_id FROM ledger_entries WHERE id = ?) " +
                    " AND created_at = (SELECT created_at FROM ledger_entries WHERE id = ?))";
            String insert = "INSERT INTO ledger_entries " +
                    "(branch_id,account_id,to_account_id,type_id,amount_pya,fee_pya,commission_pya," +
                    " cash_delta_pya,digital_delta_pya,sender_phone,receiver_phone,ref_no,note,reverses_id,created_at,created_by) " +
                    "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
            String now = java.time.LocalDateTime.now()
                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

            try (PreparedStatement ld = c.prepareStatement(load)) {
                ld.setLong(1, entryId); ld.setLong(2, entryId);
                ld.setLong(3, entryId); ld.setLong(4, entryId);
                try (ResultSet rs = ld.executeQuery();
                     PreparedStatement ins = c.prepareStatement(insert)) {
                    int count = 0;
                    while (rs.next()) {
                        ins.setInt(1, rs.getInt("branch_id"));
                        ins.setInt(2, rs.getInt("account_id"));
                        int toAcc = rs.getInt("to_account_id");
                        if (rs.wasNull()) ins.setNull(3, java.sql.Types.INTEGER); else ins.setInt(3, toAcc);
                        ins.setInt(4, rs.getInt("type_id"));
                        ins.setLong(5, -rs.getLong("amount_pya"));
                        ins.setLong(6, -rs.getLong("fee_pya"));
                        ins.setLong(7, -rs.getLong("commission_pya"));
                        ins.setLong(8, -rs.getLong("cash_delta_pya"));
                        ins.setLong(9, -rs.getLong("digital_delta_pya"));
                        ins.setNull(10, java.sql.Types.VARCHAR);
                        ins.setNull(11, java.sql.Types.VARCHAR);
                        ins.setString(12, rs.getString("ref_no"));
                        ins.setString(13, "ပြန်ရုပ်သိမ်းချက် — #" + rs.getLong("id"));
                        ins.setLong(14, rs.getLong("id"));   // reverses_id -> the row it cancels
                        ins.setString(15, now);
                        ins.setInt(16, userId);
                        ins.executeUpdate();
                        count++;
                    }
                    if (count == 0) throw new IllegalStateException(I18n.t("error.notFound"));
                }
            }

            ActivityLog.record(c, Session.branchId(), userId, "REVERSE", "#" + entryId);
            c.commit();
        } catch (Exception e) {
            c.rollback();
            throw e;
        } finally {
            c.setAutoCommit(oldAuto);
        }
    }

    /** Top up a wallet's digital balance, or add cash float. amountPya > 0. */
    public static long topUp(Account account, long amountPya, boolean digital, String note) throws Exception {
        if (!Permissions.canTopUp()) throw new IllegalStateException(I18n.t("error.noTopupPermission"));
        if (account == null) throw new IllegalArgumentException(I18n.t("error.accountRequired"));
        if (amountPya <= 0) throw new IllegalArgumentException(I18n.t("error.amountRequired"));

        String typeName = digital ? TxnType.TOPUP_DIGITAL : TxnType.TOPUP_CASH;
        TxnType type = com.agentledger.repo.TxnTypeRepo.byName(Session.branchId(), typeName);
        if (type == null) throw new IllegalStateException(I18n.t("error.typeNotFound", typeName));

        PostingRequest r = new PostingRequest();
        r.type = type;
        r.account = account;
        r.amountPya = amountPya;
        r.note = (note == null || note.isBlank()) ? typeName : note;
        return post(r);   // reuses the same one-transaction posting path
    }

    private static void setOrNull(PreparedStatement ps, int idx, String v) throws SQLException {
        if (v == null || v.isBlank()) ps.setNull(idx, Types.VARCHAR);
        else ps.setString(idx, v.trim());
    }
}