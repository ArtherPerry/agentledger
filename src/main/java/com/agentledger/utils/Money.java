package com.agentledger.utils;

import java.text.DecimalFormat;

/** All money is stored as long pya (1 kyat = 100 pya). Display only at the UI edge. */
public final class Money {
    private static final char[] MM = {'၀','၁','၂','၃','၄','၅','၆','၇','၈','၉'};
    private static final DecimalFormat FMT = new DecimalFormat("#,##0.00");

    private Money() {}

    /** pya -> "၂,၅၀၀.၅၀" (Myanmar numerals, 2 decimals). */
    public static String format(long pya) {
        String latin = FMT.format(pya / 100.0);
        StringBuilder sb = new StringBuilder(latin.length());
        for (char c : latin.toCharArray()) {
            sb.append(c >= '0' && c <= '9' ? MM[c - '0'] : c);
        }
        return sb.toString();
    }

    /** "2,500.50" or "၂,၅၀၀.၅၀" -> 250050 pya. */
    public static long parse(String text) {
        if (text == null || text.isBlank()) return 0L;
        StringBuilder s = new StringBuilder();
        for (char c : text.toCharArray()) {
            if (c >= '၀' && c <= '၉') s.append((char) ('0' + (c - '၀')));
            else if (c == '.' || (c >= '0' && c <= '9') || c == '-') s.append(c);
        }
        if (s.length() == 0) return 0L;
        return Math.round(Double.parseDouble(s.toString()) * 100.0);
    }
}