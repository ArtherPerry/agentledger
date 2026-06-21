package com.agentledger.license;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;

import com.agentledger.db.Database;

/** Persists the activated license key next to the database (survives app updates). */
public final class LicenseStore {
    private LicenseStore() {}

    private static Path file() {
        return Database.appDataDir().resolve("license.key");
    }

    public static void save(String licenseKey) throws Exception {
        Path f = file();
        Files.createDirectories(f.getParent());
        Files.writeString(f, licenseKey.trim(), StandardCharsets.UTF_8);
    }

    public static String load() {
        try {
            Path f = file();
            return Files.exists(f) ? Files.readString(f, StandardCharsets.UTF_8).trim() : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** True if a stored license exists AND is valid for this device. */
    public static boolean isActivated() {
        String key = load();
        return key != null && LicenseService.verify(DeviceId.get(), key);
    }
}