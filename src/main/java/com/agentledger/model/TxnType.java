package com.agentledger.model;

public record TxnType(int id, String name, String cashEffect, String digitalEffect) {

    @Override public String toString() { return name; }

    // ---- Canonical type names (DATA, not UI text — never translate these) ----
    public static final String PASSWORD_WITHDRAW = "Password ဖြင့် ထုတ်ယူ";
    public static final String PASSWORD_TRANSFER = "Password ဖြင့် လွဲ";
    public static final String WALLET_TO_WALLET  = "အကောင့်မှ အကောင့်";
    public static final String ACCOUNT_WITHDRAW  = "အကောင့်မှ ထုတ်";
    public static final String CASH_TO_ACCOUNT   = "ငွေသားမှ အကောင့်";
    public static final String TOPUP_DIGITAL     = "ဒစ်ဂျစ်တယ် ဖြည့်သွင်း";
    public static final String TOPUP_CASH        = "ငွေသား ဖြည့်သွင်း";
    public static final String REPAY_RECEIVABLE  = "ရရန် ပြန်ဆပ်ခြင်း";
    public static final String REPAY_PAYABLE     = "ပေးရန် ပြန်ဆပ်ခြင်း";

    /** Internal types hidden from the transaction form / reports. */
    public static final String[] INTERNAL = {
            TOPUP_DIGITAL, TOPUP_CASH, REPAY_RECEIVABLE, REPAY_PAYABLE
    };

    /** SQL-quoted, comma-separated list of internal names for NOT IN (...) clauses. */
    public static String internalSqlList() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < INTERNAL.length; i++) {
            if (i > 0) sb.append(',');
            sb.append('\'').append(INTERNAL[i]).append('\'');
        }
        return sb.toString();
    }
}