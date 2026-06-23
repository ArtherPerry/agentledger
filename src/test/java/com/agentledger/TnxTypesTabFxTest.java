package com.agentledger;

import com.agentledger.db.Database;
import com.agentledger.i18n.I18n;
import com.agentledger.model.TxnType;
import com.agentledger.model.User;
import com.agentledger.repo.TxnTypeRepo;
import com.agentledger.service.Session;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.TableView;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(ApplicationExtension.class)
class TxnTypesTabFxTest {

    private Path dbFile;

    @Start
    private void start(Stage stage) throws Exception {
        dbFile = Files.createTempFile("agentledger-fxtypes-", ".db");
        Files.deleteIfExists(dbFile);
        System.setProperty(Database.DB_PATH_PROPERTY, dbFile.toString());
        Database.closePublic();
        Database.get();
        TestFixtures.seedStandard();

        Session.login(new User(1, 1, "Owner 1", "owner1", "owner"));
        Session.setBranch(1, "ဆိုင်ခွဲ ၁");

        FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/fxml/settings/txntypes.fxml"), I18n.bundle());
        Parent root = loader.load();
        stage.setScene(new Scene(root, 1100, 700));
        stage.show();
    }

    @AfterEach
    void cleanup() throws Exception {
        Database.closePublic();
        Session.logout();
        System.clearProperty(Database.DB_PATH_PROPERTY);
        Files.deleteIfExists(dbFile);
        Files.deleteIfExists(Path.of(dbFile + "-wal"));
        Files.deleteIfExists(Path.of(dbFile + "-shm"));
    }

    /** Helper: is the given type active right now (read straight from the DB)? */
    private boolean activeInDb(int branchId, String canonicalName) {
        TxnType t = TxnTypeRepo.byName(branchId, canonicalName);
        // byName only returns active rows; if null, it's inactive (or absent)
        return t != null;
    }

    @Test
    void table_listsTheBuiltinTypes(FxRobot robot) {
        TableView<?> table = robot.lookup("#table").queryTableView();
        assertFalse(table.getItems().isEmpty(), "txn-types table should list the seeded types");
    }

    @Test
    void disableButton_deactivatesThatRow(FxRobot robot) {
        // PASSWORD_WITHDRAW starts active.
        assertTrue(activeInDb(1, TxnType.PASSWORD_WITHDRAW), "should start active");

        // Click the disable button (ပိတ်ရန်) in the FIRST row.
        // Row 0 is PASSWORD_WITHDRAW (seeded sort_order 1). The button text comes from i18n.
        String disableLabel = I18n.t("txntype.btn.disable");
        robot.clickOn(disableLabel);   // clicks the first visible button with that text (row 0)

        // It should now be inactive in the DB.
        assertFalse(activeInDb(1, TxnType.PASSWORD_WITHDRAW),
                "clicking disable should deactivate PASSWORD_WITHDRAW");
    }

    @Test
    void arrowButton_doesNotChangeActiveState(FxRobot robot) {
        // The bug we fixed: clicking the up/down arrow used to flip a row's active state.
        // Capture the active-state of all built-in user types before clicking an arrow.
        List<String> names = List.of(
                TxnType.PASSWORD_WITHDRAW, TxnType.PASSWORD_TRANSFER,
                TxnType.WALLET_TO_WALLET, TxnType.ACCOUNT_WITHDRAW, TxnType.CASH_TO_ACCOUNT);
        boolean[] before = new boolean[names.size()];
        for (int i = 0; i < names.size(); i++) before[i] = activeInDb(1, names.get(i));

        // Click a down arrow (↓) on the second row to reorder.
        // There are several ↓ buttons (one per row); clickOn hits the first visible one.
        robot.clickOn("↓");

        // No active-state should have changed — only order.
        for (int i = 0; i < names.size(); i++) {
            assertEquals(before[i], activeInDb(1, names.get(i)),
                    "reordering must NOT change active state of " + names.get(i));
        }
    }
}