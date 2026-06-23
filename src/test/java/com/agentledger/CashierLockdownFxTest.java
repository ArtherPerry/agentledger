package com.agentledger;

import com.agentledger.db.Database;
import com.agentledger.i18n.I18n;
import com.agentledger.model.User;
import com.agentledger.service.Session;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** A cashier may ONLY see the transaction screen + logout. No home/history/accounts/
 *  debts/close/reports/settings/switch-branch. Guards the data-entry-only lockdown. */
@ExtendWith(ApplicationExtension.class)
class CashierLockdownFxTest {

    private Path dbFile;

    @Start
    private void start(Stage stage) throws Exception {
        dbFile = Files.createTempFile("agentledger-cashier-", ".db");
        Files.deleteIfExists(dbFile);
        System.setProperty(Database.DB_PATH_PROPERTY, dbFile.toString());
        Database.closePublic();
        Database.get();
        TestFixtures.seedStandard();

        // Log in as a CASHIER (role drives the sidebar). The user need not exist in the DB
        // for the sidebar logic, but it must be branch 1 so the landing TXN screen loads seeded data.
        Session.login(new User(99, 1, "Cashier 1", "cashier1", "cashier"));
        Session.setBranch(1, "ဆိုင်ခွဲ ၁");

        FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/fxml/dashboard.fxml"), I18n.bundle());
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

    private boolean navPresent(FxRobot robot, String labelKey) {
        // nav buttons live inside the #sidebar VBox; match by exact label text
        String label = I18n.t(labelKey);
        return robot.lookup("#sidebar").lookup(label).tryQuery().isPresent();
    }

    @Test
    void cashier_seesTransactionAndLogout(FxRobot robot) {
        assertTrue(navPresent(robot, "nav.txn"), "cashier must see the transaction nav");
        assertTrue(navPresent(robot, "nav.logout"), "cashier must see logout");
    }

    @Test
    void cashier_cannotSeeOwnerOrManagerScreens(FxRobot robot) {
        assertFalse(navPresent(robot, "nav.home"),     "cashier must NOT see Home");
        assertFalse(navPresent(robot, "nav.history"),  "cashier must NOT see History");
        assertFalse(navPresent(robot, "nav.accounts"), "cashier must NOT see Accounts");
        assertFalse(navPresent(robot, "nav.payrec"),   "cashier must NOT see Payable/Receivable");
        assertFalse(navPresent(robot, "nav.close"),    "cashier must NOT see Daily Close");
        assertFalse(navPresent(robot, "nav.reports"),  "cashier must NOT see Reports");
        assertFalse(navPresent(robot, "nav.settings"), "cashier must NOT see Settings");
        assertFalse(navPresent(robot, "nav.switchBranch"), "cashier must NOT see Switch Branch");
    }

    @Test
    void sidebar_existsAndHasItems(FxRobot robot) {
        VBox sidebar = robot.lookup("#sidebar").queryAs(VBox.class);
        assertNotNull(sidebar);
        assertFalse(sidebar.getChildren().isEmpty(), "sidebar should have at least txn + logout");
    }
}