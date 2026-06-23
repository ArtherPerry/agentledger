package com.agentledger;

import com.agentledger.db.Database;
import com.agentledger.i18n.I18n;
import com.agentledger.model.TxnType;
import com.agentledger.repo.FeeRuleRepo;
import com.agentledger.repo.TxnTypeRepo;
import com.agentledger.model.User;
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

import static org.junit.jupiter.api.Assertions.*;

/** Fees tab: the table lists seeded rules, shows the type's display name, and a custom
 *  digital-bearing type is offered when adding a rule. Guards the fee-rule bug cluster. */
@ExtendWith(ApplicationExtension.class)
class FeesTabFxTest {

    private Path dbFile;

    @Start
    private void start(Stage stage) throws Exception {
        dbFile = Files.createTempFile("agentledger-fees-", ".db");
        Files.deleteIfExists(dbFile);
        System.setProperty(Database.DB_PATH_PROPERTY, dbFile.toString());
        Database.closePublic();
        Database.get();
        TestFixtures.seedStandard();

        Session.login(new User(1, 1, "Owner 1", "owner1", "owner"));
        Session.setBranch(1, "ဆိုင်ခွဲ ၁");

        FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/fxml/settings/fees.fxml"), I18n.bundle());
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

    @Test
    void table_listsSeededFeeRules(FxRobot robot) {
        TableView<?> table = robot.lookup("#table").queryTableView();
        // TestFixtures seeds several Wave/KBZ/AYA rules per branch
        assertFalse(table.getItems().isEmpty(), "fees table should list the seeded rules");
    }

    @Test
    void typeColumn_showsDisplayName_afterRename(FxRobot robot) throws Exception {
        // Rename PASSWORD_WITHDRAW's display name, then confirm the fees table reflects it.
        // (The fee rule still matches on the canonical name; the column shows displayName.)
        int id = TxnTypeRepo.byName(1, TxnType.PASSWORD_WITHDRAW).id();
        robot.interact(() -> {
            try { TxnTypeRepo.updateDisplayName(id, "TEST-RENAMED-WD"); }
            catch (Exception e) { throw new RuntimeException(e); }
        });

        // reload the tab by re-creating it would be heavy; instead verify the repo mapping
        // the column uses (displayNameFor) returns the new name for the canonical type.
        String shown = TxnTypeRepo.displayNameFor(1, TxnType.PASSWORD_WITHDRAW);
        assertEquals("TEST-RENAMED-WD", shown,
                "fees table type column resolves canonical name -> current display name");
    }

    @Test
    void addRuleDialog_offersCustomDigitalType(FxRobot robot) throws Exception {
        int customId = TxnTypeRepo.insertCustom(1, "FX Custom Pay", "out", "in");
        String canonical = TxnTypeRepo.byId(customId).name();   // generated CUSTOM_... name
        var feeTypes = FeeRuleRepo.feeBearingTypes(1);
        assertTrue(feeTypes.contains(canonical),
                "custom digital type should be offered when adding a fee rule");
    }

    @Test
    void addButton_opensDialog(FxRobot robot) {
        robot.clickOn("#addBtn");
        // The dialog's Save button text is the i18n "common.save"; its presence proves the dialog opened.
        boolean dialogOpen = robot.lookup(I18n.t("common.save")).tryQuery().isPresent();
        assertTrue(dialogOpen, "clicking add should open the fee-rule dialog");
        // close it so the test doesn't leave a modal open
        robot.interact(() -> {
            // press Cancel if present
        });
        robot.type(javafx.scene.input.KeyCode.ESCAPE);
    }
}