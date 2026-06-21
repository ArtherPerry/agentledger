package com.agentledger.utils;

import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.File;

public final class Files2 {
    private Files2() {}

    public static File chooseSave(Window owner, String title, String defaultName, String desc, String ext) {
        FileChooser fc = new FileChooser();
        fc.setTitle(title);
        fc.setInitialFileName(defaultName);
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter(desc, "*." + ext));
        return fc.showSaveDialog(owner);
    }
}