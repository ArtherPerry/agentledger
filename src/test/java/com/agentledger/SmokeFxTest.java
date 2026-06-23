package com.agentledger;

import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.api.FxRobot;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(ApplicationExtension.class)
class SmokeFxTest {

    private Label label;

    @Start
    private void start(Stage stage) {
        label = new Label("before");
        Button btn = new Button("go");
        btn.setId("goBtn");
        btn.setOnAction(e -> label.setText("after"));
        stage.setScene(new Scene(new VBox(label, btn), 200, 100));
        stage.show();
    }

    @Test
    void clickingButton_updatesLabel(FxRobot robot) {
        robot.clickOn("#goBtn");
        assertEquals("after", label.getText());
    }
}