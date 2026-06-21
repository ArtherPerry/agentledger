package com.agentledger;

import com.agentledger.model.FeeRule;
import com.agentledger.model.TxnType;
import com.agentledger.repo.FeeRuleRepo;
import com.agentledger.service.FeeService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FeeRuleEditTest extends TestBase {

    private static final String W = TxnType.PASSWORD_WITHDRAW;

    @Test void editingARule_changesComputedFee() throws Exception {
        // baseline: AYA Pay (withdrawal) flat 0.5%, min 50,000
        assertEquals(250_000L, FeeService.compute(1, W, "AYA Pay", 50_000_000L).feePya()); // 0.5%
        // find the AYA rule and change it to 1.0%
        FeeRule aya = FeeRuleRepo.listForBranch(1).stream()
                .filter(r -> "AYA Pay".equals(r.platform())).findFirst().orElseThrow();
        FeeRule updated = new FeeRule(aya.id(), W, "AYA Pay", aya.minAmountPya(), aya.maxAmountPya(),
                1.0, aya.minFeePya(), aya.commPct(), true);
        FeeRuleRepo.update(updated);
        // now the engine must use 1.0%
        assertEquals(500_000L, FeeService.compute(1, W, "AYA Pay", 50_000_000L).feePya()); // 1.0%
    }

    @Test void newRule_appliesImmediately() throws Exception {
        // no rule for "CB Pay" yet -> fee 0
        assertEquals(0L, FeeService.compute(1, W, "CB Pay", 50_000_000L).feePya());
        FeeRuleRepo.insert(1, new FeeRule(0, W, "CB Pay", 0L, null, 0.6, 50_000L, 0.3, true));
        assertEquals(300_000L, FeeService.compute(1, W, "CB Pay", 50_000_000L).feePya()); // 0.6%
    }
}
