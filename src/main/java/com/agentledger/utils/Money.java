package com.agentledger.utils;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/** All money is stored as long pya (1 kyat = 100 pya). Display only at the UI edge. */
public final class Money {
    // Locale.ROOT keeps digits/separators as ASCII "1,234.56" regardless of the OS locale.
    private static final DecimalFormat FMT =
            new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.ROOT));

    private Money() {}

    /** pya -> "2,500.50" (English numerals, 2 decimals). */
    public static String format(long pya) {
        return FMT.format(pya / 100.0);
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