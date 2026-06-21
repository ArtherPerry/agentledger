package com.agentledger;

import com.agentledger.model.FeeResult;
import com.agentledger.model.TxnType;
import com.agentledger.service.FeeService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FeeServiceTest extends TestBase {

    private static final String W = TxnType.PASSWORD_WITHDRAW;

    @Test void wave_500k_isTopOfTier2() {
        FeeResult r = FeeService.compute(1, W, "Wave Money", 50_000_000L);
        assertEquals(200_000L, r.feePya());
        assertEquals(150_000L, r.commissionPya());
    }

    @Test void wave_smallAmount_appliesMinimumFee() {
        FeeResult r = FeeService.compute(1, W, "Wave Money", 5_000_000L);
        assertEquals(50_000L, r.feePya());
    }

    @Test void wave_secondTier_appliesTier2MinFee() {
        FeeResult r = FeeService.compute(1, W, "Wave Money", 20_000_000L);
        assertEquals(100_000L, r.feePya());
    }

    @Test void unknownPlatform_orCash_returnsZero() {
        assertEquals(0L, FeeService.compute(1, W, null, 50_000_000L).feePya());
        assertEquals(0L, FeeService.compute(1, W, "Nonexistent", 50_000_000L).feePya());
    }

    @Test void wave_tier1_upperBoundary() {
        FeeResult r = FeeService.compute(1, W, "Wave Money", 10_000_000L);
        assertEquals(50_000L, r.feePya());
    }

    @Test void wave_tier2_lowerBoundary() {
        FeeResult r = FeeService.compute(1, W, "Wave Money", 10_000_001L);
        assertEquals(100_000L, r.feePya());
    }

    @Test void wave_tier3_noUpperBound() {
        FeeResult r = FeeService.compute(1, W, "Wave Money", 100_000_000L);
        assertEquals(300_000L, r.feePya());
        assertEquals(250_000L, r.commissionPya());
    }

    @Test void amountAtZeroBoundary_returnsZero() {
        assertEquals(0L, FeeService.compute(1, W, "Wave Money", 0L).feePya());
    }

    @Test void sameAmountDifferentType_canDiffer() {
        long withdraw = FeeService.compute(1, TxnType.PASSWORD_WITHDRAW, "Wave Money", 50_000_000L).feePya();
        long deposit  = FeeService.compute(1, TxnType.CASH_TO_ACCOUNT, "Wave Money", 50_000_000L).feePya();
        assertEquals(200_000L, withdraw);
        assertEquals(0L, deposit);
    }
}
