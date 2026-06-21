package com.agentledger.license;

import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;

/**
 * A stable per-machine identifier. On Windows it uses the registry MachineGuid
 * (survives hardware changes; changes only on OS reinstall). On other OSes it
 * falls back to a hostname+user signal so the app is testable off-Windows.
 * The raw value is hashed and formatted as short groups for easy copying.
 */
public final class DeviceId {
    private DeviceId() {}

    /** Formatted ID shown to the user, e.g. "K7M2-9XQ4-P3R8-T1W5". */
    public static String get() {
        String raw = rawMachineSignal();
        return format(hash(raw));
    }

    private static String rawMachineSignal() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) {
            String guid = windowsMachineGuid();
            if (guid != null && !guid.isBlank()) return "win:" + guid.trim();
        }
        // Fallback (Mac/Linux dev, or if registry read fails): host + user + os.
        String host = System.getenv().getOrDefault("COMPUTERNAME",
                System.getenv().getOrDefault("HOSTNAME", "unknown-host"));
        String user = System.getProperty("user.name", "unknown-user");
        return "fb:" + host + ":" + user + ":" + os;
    }

    private static String windowsMachineGuid() {
        try {
            Process p = new ProcessBuilder("reg", "query",
                    "HKLM\\SOFTWARE\\Microsoft\\Cryptography", "/v", "MachineGuid")
                    .redirectErrorStream(true).start();
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            p.waitFor();
            for (String line : out.split("\\R")) {
                if (line.contains("MachineGuid")) {
                    String[] parts = line.trim().split("\\s+");
                    return parts[parts.length - 1];   // the GUID is the last token
                }
            }
        } catch (Exception ignore) { }
        return null;
    }

    private static byte[] hash(String s) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** First 10 bytes of the hash -> 16 Crockford-ish chars -> 4 groups of 4. */
    private static String format(byte[] hash) {
        final char[] ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray(); // no I,L,O,U
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 10; i++) sb.append(ALPHABET[(hash[i] & 0xFF) % ALPHABET.length]);
        sb.append(ALPHABET[(hash[10] & 0xFF) % ALPHABET.length]);
        sb.append(ALPHABET[(hash[11] & 0xFF) % ALPHABET.length]);
        String s = sb.toString(); // 12 chars
        return s.substring(0,4) + "-" + s.substring(4,8) + "-" + s.substring(8,12);
    }
}