package com.agentledger;

import com.agentledger.utils.Money;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MoneyTest {

    @Test void parsesWholeKyatToPya() {
        assertEquals(50000000L, Money.parse("500000"));   // 500,000 kyat -> pya
    }

    @Test void parsesDecimalsToPya() {
        assertEquals(250050L, Money.parse("2500.50"));
        assertEquals(100L, Money.parse("1.00"));
        assertEquals(1L, Money.parse("0.01"));
    }

    @Test void parsesMyanmarDigits() {
        assertEquals(Money.parse("123"), Money.parse("၁၂၃"));
    }

    @Test void parsesGroupingAndBlankAndJunk() {
        assertEquals(150000000L, Money.parse("1,500,000"));
        assertEquals(0L, Money.parse(""));
        assertEquals(0L, Money.parse(null));
    }

    @Test void formatRoundTripsThroughMyanmarDigits() {
        // format produces Myanmar numerals; parse should read them back
        String shown = Money.format(250050L);            // ၂,၅၀၀.၅၀
        assertEquals(250050L, Money.parse(shown));
    }

    @Test void formatHasTwoDecimals() {
        String s = Money.format(250000L);                // 2,500.00 in MM digits
        assertTrue(s.contains("."), "should show decimals");
    }
}