package com.agentledger;

import at.favre.lib.crypto.bcrypt.BCrypt;
import com.agentledger.db.Database;
import com.agentledger.db.SeedData;
import com.agentledger.model.TxnType;

import java.sql.*;
import java.time.LocalDate;

/**
 * Recreates the data the test suite expects (formerly the demo seed in Database).
 * Production no longer seeds demo data — tests provide their own fixture here.
 */
final class TestFixtures {
    private TestFixtures() {}

    static void seedStandard() throws Exception {
        Connection conn = Database.get();
        String today = LocalDate.now().toString();
        String ownerHash = BCrypt.withDefaults().hashToString(12, "owner123".toCharArray());
        String[] names = {"ဆိုင်ခွဲ ၁", "ဆိုင်ခွဲ ၂"};
        String W = TxnType.PASSWORD_WITHDRAW;   // seeded fee rules are withdrawal rules

        boolean oldAuto = conn.getAutoCommit();
        conn.setAutoCommit(false);
        try (Statement s = conn.createStatement()) {
            for (int b = 1; b <= 2; b++) {
                s.executeUpdate("INSERT INTO branches(id,name,created_at) VALUES (" + b + ",'" + names[b-1] + "','" + today + "')");
                s.executeUpdate("INSERT INTO users(branch_id,name,username,pwd_hash,role,created_at) " +
                        "VALUES (" + b + ",'Owner " + b + "','owner" + b + "','" + ownerHash + "','owner','" + today + "')");
                s.executeUpdate("INSERT INTO accounts(branch_id,name,platform,acct_type,created_at) VALUES (" + b + ",'ငွေသား',NULL,'cash','" + today + "')");
                s.executeUpdate("INSERT INTO accounts(branch_id,name,platform,acct_type,created_at) VALUES (" + b + ",'Wave Main','Wave Money','digital','" + today + "')");
                s.executeUpdate("INSERT INTO accounts(branch_id,name,platform,acct_type,created_at) VALUES (" + b + ",'KBZPay','KBZ Pay','digital','" + today + "')");
                s.executeUpdate("INSERT INTO accounts(branch_id,name,platform,acct_type,created_at) VALUES (" + b + ",'AYA Pay','AYA Pay','digital','" + today + "')");
                SeedData.insertTxnTypes(conn, b);
                s.executeUpdate("INSERT INTO fee_rules(branch_id,type_name,platform,min_amount_pya,max_amount_pya,fee_pct,min_fee_pya,comm_pct) VALUES (" + b + ",'" + W + "','Wave Money',0,10000000,0.5,50000,0.3)");
                s.executeUpdate("INSERT INTO fee_rules(branch_id,type_name,platform,min_amount_pya,max_amount_pya,fee_pct,min_fee_pya,comm_pct) VALUES (" + b + ",'" + W + "','Wave Money',10000001,50000000,0.4,100000,0.3)");
                s.executeUpdate("INSERT INTO fee_rules(branch_id,type_name,platform,min_amount_pya,max_amount_pya,fee_pct,min_fee_pya,comm_pct) VALUES (" + b + ",'" + W + "','Wave Money',50000001,NULL,0.3,200000,0.25)");
                s.executeUpdate("INSERT INTO fee_rules(branch_id,type_name,platform,min_amount_pya,max_amount_pya,fee_pct,min_fee_pya,comm_pct) VALUES (" + b + ",'" + W + "','KBZ Pay',0,50000000,0.5,50000,0.3)");
                s.executeUpdate("INSERT INTO fee_rules(branch_id,type_name,platform,min_amount_pya,max_amount_pya,fee_pct,min_fee_pya,comm_pct) VALUES (" + b + ",'" + W + "','KBZ Pay',50000001,NULL,0.4,150000,0.25)");
                s.executeUpdate("INSERT INTO fee_rules(branch_id,type_name,platform,min_amount_pya,max_amount_pya,fee_pct,min_fee_pya,comm_pct) VALUES (" + b + ",'" + W + "','AYA Pay',0,NULL,0.5,50000,0.3)");
            }
            conn.commit();
        } catch (Exception e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(oldAuto);
        }
    }
}
