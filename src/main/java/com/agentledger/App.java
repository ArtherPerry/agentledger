package com.agentledger;

import com.agentledger.db.Database;
import com.agentledger.license.LicenseStore;
import com.agentledger.nav.Router;
import com.agentledger.nav.View;
import javafx.application.Application;
import javafx.stage.Stage;

public class App extends Application {

    @Override
    public void start(Stage stage) {
        try {
            Database.get();
        } catch (Exception e) {
            com.agentledger.utils.Log.error(e);
        }
        Router.init(stage);
        stage.setTitle("AgentLedger");

        if (!LicenseStore.isActivated()) {
            Router.to(View.ACTIVATION);
        } else if (needsSetup()) {
            Router.to(View.SETUP);
        } else {
            Router.to(View.BRANCH);
        }
        stage.show();
    }

    /** Fresh DB with no users yet -> first-run setup. */
    private boolean needsSetup() {
        try {
            var c = Database.get();
            try (var s = c.createStatement();
                 var rs = s.executeQuery("SELECT COUNT(*) FROM users")) {
                rs.next();
                return rs.getInt(1) == 0;
            }
        } catch (Exception e) {
            return false;
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}