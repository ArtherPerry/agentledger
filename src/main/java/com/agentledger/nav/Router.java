package com.agentledger.nav;

import com.agentledger.i18n.I18n;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;

public final class Router {
    private static Stage stage;
    private static Pane contentArea;   // the dashboard's center region

    private Router() {}

    public static void init(Stage s) { stage = s; }

    /** Full-screen swap (branch, login, dashboard frame). */
    public static void to(View view) {
        try {
            FXMLLoader loader = new FXMLLoader(Router.class.getResource(view.path()), I18n.bundle());
            Parent root = loader.load();
            if (stage.getScene() == null) stage.setScene(new Scene(root, 1100, 680));
            else stage.getScene().setRoot(root);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load view " + view, e);
        }
    }

    /** The dashboard registers its center pane here so the sidebar can swap into it. */
    public static void setContentArea(Pane area) { contentArea = area; }

    /** Swap just the center of the dashboard frame (sidebar navigation). */
    public static void show(View view) {
        if (contentArea == null) throw new IllegalStateException("content area not set");
        try {
            FXMLLoader loader = new FXMLLoader(Router.class.getResource(view.path()), I18n.bundle());
            Parent node = loader.load();
            contentArea.getChildren().setAll(node);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load content " + view, e);
        }
    }
}