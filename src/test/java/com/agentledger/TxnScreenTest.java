package com.agentledger;

import com.agentledger.db.Database;
import com.agentledger.i18n.I18n;
import com.agentledger.model.User;
import com.agentledger.service.Session;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
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

@ExtendWith(ApplicationExtension.class)
class TxnScreenFxTest {

    private Path dbFile;

    /** Build a known state, then load ONLY the transaction screen into the stage. */
    @Start
    private void start(Stage stage) throws Exception {
        // --- same setup TestBase does, but here on the FX thread ---
        dbFile = Files.createTempFile("agentledger-fxtest-", ".db");
        Files.deleteIfExists(dbFile);
        System.setProperty(Database.DB_PATH_PROPERTY, dbFile.toString());
        Database.closePublic();
        Database.get();                 // fresh schema, migrated to current
        TestFixtures.seedStandard();    // branches, users, accounts, fee rules, txn types

        Session.login(new User(1, 1, "Owner 1", "owner1", "owner"));
        Session.setBranch(1, "ဆိုင်ခွဲ ၁");

        // --- load just the transaction screen, with the i18n bundle for %keys ---
        FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/fxml/txn.fxml"), I18n.bundle());
        Parent root = loader.load();
        stage.setScene(new Scene(root, 1000, 700));
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
    void typeDropdown_isPopulated(FxRobot robot) {
        ComboBox<?> typeBox = robot.lookup("#typeBox").queryComboBox();
        assertNotNull(typeBox, "typeBox should exist");
        assertFalse(typeBox.getItems().isEmpty(), "type dropdown should have transaction types");
    }

    @Test
    void enteringAmount_computesFee(FxRobot robot) {
        // The seeded fixture has a Wave Money fee rule for PASSWORD_WITHDRAW.
        // Select that type + the Wave account, type an amount, and assert the fee field fills in.
        ComboBox<Object> typeBox = robot.lookup("#typeBox").queryComboBox();
        ComboBox<Object> accountBox = robot.lookup("#accountBox").queryComboBox();

        // pick the PASSWORD_WITHDRAW type
        Object withdraw = typeBox.getItems().stream()
                .filter(t -> t.toString().contains("ထုတ်ယူ"))   // display name of PASSWORD_WITHDRAW
                .findFirst().orElseThrow();
        // pick the Wave account
        Object wave = accountBox.getItems().stream()
                .filter(a -> a.toString().contains("Wave"))
                .findFirst().orElseThrow();

        // set selections on the FX thread
        robot.interact(() -> {
            typeBox.getSelectionModel().select(withdraw);
            accountBox.getSelectionModel().select(wave);
        });

        // type an amount
        robot.clickOn("#amount").write("100000");

        // the fee field should now show a non-zero computed fee
        TextField feeField = robot.lookup("#feeField").queryAs(TextField.class);
        robot.interact(() -> {}); // let listeners settle
        String fee = feeField.getText();
        assertNotNull(fee);
        assertNotEquals("0.00", fee.trim(), "fee should compute for a Wave withdraw with a seeded rule");
    }
}