package com.agentledger.utils;

import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;

import java.util.function.UnaryOperator;

/**
 * English-only input filters for numeric fields. Blocks Burmese digits (၀-၉),
 * letters, and other stray characters at type/paste time, on every OS/keyboard.
 * Display formatting lives in {@link Money}; this only governs what can be typed.
 */
public final class Numeric {
    private Numeric() {}

    // Money/amount: ASCII digits, optional grouping commas, at most one decimal point.
    // Matches what Money.format() produces (e.g. "2,500.00") so setText() round-trips cleanly.
    private static final String MONEY   = "[0-9,]*\\.?[0-9]*";
    // Plain decimal (percent, rates): digits + at most one point, no grouping.
    private static final String DECIMAL = "[0-9]*\\.?[0-9]*";
    // Phone: digits with an optional single leading '+'.
    private static final String PHONE   = "\\+?[0-9]*";

    /** Restrict the given money fields to English digits / commas / one dot. */
    public static void money(TextField... fields)   { apply(MONEY, fields); }

    /** Restrict the given fields to a plain English decimal (e.g. a percent). */
    public static void decimal(TextField... fields) { apply(DECIMAL, fields); }

    /** Restrict the given fields to phone-style input (digits, optional leading +). */
    public static void phone(TextField... fields)   { apply(PHONE, fields); }

    private static void apply(String regex, TextField... fields) {
        for (TextField f : fields) {
            if (f == null) continue;
            UnaryOperator<TextFormatter.Change> filter =
                    c -> c.getControlNewText().matches(regex) ? c : null;
            f.setTextFormatter(new TextFormatter<>(filter));
        }
    }
}
