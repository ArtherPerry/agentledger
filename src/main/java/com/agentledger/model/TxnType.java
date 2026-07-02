package com.agentledger.model;

public record TxnType(int id, String name, String displayName, String cashEffect,
                      String digitalEffect, boolean active, boolean builtin) {

    @Override public String toString() { return displayName != null ? displayName : name; }

    // ---- Canonical type names (DATA, not UI text — never translate or rename these) ----
    public static final String PASSWORD_WITHDRAW = "Password ဖြင့် ထုတ်ယူ";
    public static final String PASSWORD_TRANSFER = "Password ဖြင့် လွဲ";
    public static final String WALLET_TO_WALLET  = "အကောင့်မှ အကောင့်";
    public static final String ACCOUNT_WITHDRAW  = "အကောင့်မှ ထုတ်";
    public static final String CASH_TO_ACCOUNT   = "ငွေသားမှ အကောင့်";
    // ---- Added as standard defaults (client request) ----
    // Single-account cash-in / digital-out (customer pays cash, agent sends digital to the customer's
    // wallet). Distinct from WALLET_TO_WALLET (which is a two-account digital transfer).
    public static final String AGENT_CASHIN      = "agent မှ user အကောင့်ထဲထည့်";
    public static final String CUS_TO_SHOP       = "cus အကောင့်မှ ဆိုင် user အကောင့်ထဲထည့်";
    public static final String RENT2OWN_DEPOSIT  = "Rent 2 Own ငွေသွင်း";
    public static final String RENT2OWN_CASH_OUT = "Rent 2 Own ငွေသား ထုတ်";
    public static final String WIFI_FEE          = "Wifi ကြေးသွင်း";
    public static final String TOPUP_DIGITAL     = "ဒစ်ဂျစ်တယ် ဖြည့်သွင်း";
    public static final String TOPUP_CASH        = "ငွေသား ဖြည့်သွင်း";
    public static final String REPAY_RECEIVABLE  = "ရရန် ပြန်ဆပ်ခြင်း";
    public static final String REPAY_PAYABLE     = "ပေးရန် ပြန်ဆပ်ခြင်း";

    /** Internal types hidden from the transaction form / reports. */
    public static final String[] INTERNAL = {
            TOPUP_DIGITAL, TOPUP_CASH, REPAY_RECEIVABLE, REPAY_PAYABLE
    };

    public static final String[] BUILTIN_USER = {
            PASSWORD_WITHDRAW, PASSWORD_TRANSFER, WALLET_TO_WALLET, ACCOUNT_WITHDRAW, CASH_TO_ACCOUNT,
            AGENT_CASHIN, CUS_TO_SHOP, RENT2OWN_DEPOSIT, RENT2OWN_CASH_OUT, WIFI_FEE
    };

    public static boolean isBuiltinName(String n) {
        for (String s : BUILTIN_USER) if (s.equals(n)) return true;
        for (String s : INTERNAL)     if (s.equals(n)) return true;
        return false;
    }

    public static String internalSqlList() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < INTERNAL.length; i++) {
            if (i > 0) sb.append(',');
            sb.append('\'').append(INTERNAL[i]).append('\'');
        }
        return sb.toString();
    }
}