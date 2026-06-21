package com.agentledger.i18n;

import com.agentledger.utils.Money;

/** UI-layer formatting that combines money with localized units. */
public final class Fmt {
    private Fmt() {}

    /** "၂,၅၀၀.၅၀ ကျပ်" — amount with the localized kyat unit. */
    public static String kyat(long pya) {
        return Money.format(pya) + " " + I18n.t("common.kyat");
    }
}