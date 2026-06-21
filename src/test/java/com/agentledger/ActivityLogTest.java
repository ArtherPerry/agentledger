package com.agentledger;

import com.agentledger.db.Database;
import com.agentledger.repo.DebtRepo;
import com.agentledger.service.DebtService;
import org.junit.jupiter.api.Test;

import java.sql.*;

import static org.junit.jupiter.api.Assertions.*;

class ActivityLogTest extends TestBase {

    private String lastAction() throws Exception {
        Connection c = Database.get();
        try (Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT action FROM activity_log ORDER BY id DESC LIMIT 1")) {
            return rs.next() ? rs.getString(1) : null;
        }
    }

    @Test void receivableCreate_logsRecvCode() throws Exception {
        DebtService.create("receivable", "ကိုဂန်", "customer", null, 5_000_000L, null);
        assertEquals("DEBT_CREATE_RECV", lastAction());
    }

    @Test void payableCreate_logsPayCode() throws Exception {
        DebtService.create("payable", "ကုမ္ပဏီ", "agent", null, 5_000_000L, null);
        assertEquals("DEBT_CREATE_PAY", lastAction());
    }

    @Test void receivableRepay_logsRecvCode() throws Exception {
        DebtService.create("receivable", "ကိုဂန်", "customer", null, 5_000_000L, null);
        long id = DebtRepo.list(1, "receivable", false).get(0).id();
        DebtService.repay(id, 2_000_000L);
        assertEquals("DEBT_REPAY_RECV", lastAction());
    }

    @Test void payableRepay_logsPayCode() throws Exception {
        DebtService.create("payable", "ကုမ္ပဏီ", "agent", null, 5_000_000L, null);
        long id = DebtRepo.list(1, "payable", false).get(0).id();
        DebtService.repay(id, 2_000_000L);
        assertEquals("DEBT_REPAY_PAY", lastAction());
    }
}