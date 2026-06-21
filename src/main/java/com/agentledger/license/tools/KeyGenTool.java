package com.agentledger.license.tools;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.Base64;

/**
 * DEVELOPER-ONLY tools. NEVER ship this class or the private key file to the client.
 *
 * Usage:
 *   genkeys
 *       -> generates a keypair, WRITES the private key to the key file (clean, one line),
 *          and PRINTS the public key for you to paste into LicenseService.
 *
 *   sign <DEVICE_ID>
 *       -> reads the private key from the key file and prints the client's license key.
 */
public final class KeyGenTool {

    private static final String KEY_FILE =
            "/Users/sithu/Desktop/JSE/agentledger-private.key";

    public static void main(String[] args) throws Exception {
        if (args.length == 0) { usage(); return; }
        switch (args[0]) {
            case "genkeys" -> genkeys();
            case "sign" -> {
                if (args.length < 2) { usage(); return; }
                sign(args[1].trim());
            }
            case "reset" -> {
                if (args.length < 2) { usage(); return; }
                sign(args[1].trim());   // a reset code is signed exactly like a device id
            }
            default -> usage();
        }
    }

    private static void genkeys() throws Exception {
        KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
        g.initialize(2048);
        KeyPair kp = g.generateKeyPair();

        String privateB64 = Base64.getEncoder().encodeToString(kp.getPrivate().getEncoded());
        String publicB64 = Base64.getEncoder().encodeToString(kp.getPublic().getEncoded());

        // Write the private key straight to the file — no copy-paste, guaranteed clean.
        Files.writeString(Path.of(KEY_FILE), privateB64, StandardCharsets.UTF_8);

        System.out.println("Private key written to: " + KEY_FILE);
        System.out.println("  (" + privateB64.length() + " chars — keep this file safe, never ship it)");
        System.out.println();
        System.out.println("PUBLIC KEY — paste this into LicenseService.PUBLIC_KEY_B64:");
        System.out.println(publicB64);
    }

    private static void sign(String deviceId) throws Exception {
        Path path = Path.of(KEY_FILE);
        if (!Files.exists(path)) {
            System.out.println("Private key file not found: " + KEY_FILE + " — run genkeys first.");
            return;
        }
        String privateB64 = Files.readString(path, StandardCharsets.UTF_8).replaceAll("\\s", "");
        byte[] der = Base64.getDecoder().decode(privateB64);
        PrivateKey priv = KeyFactory.getInstance("RSA")
                .generatePrivate(new java.security.spec.PKCS8EncodedKeySpec(der));
        Signature s = Signature.getInstance("SHA256withRSA");
        s.initSign(priv);
        s.update(deviceId.getBytes(StandardCharsets.UTF_8));
        System.out.println("License key for device " + deviceId + ":");
        System.out.println(Base64.getEncoder().encodeToString(s.sign()));
    }

    private static void usage() {
        System.out.println("Usage:");
        System.out.println("  genkeys            (generates keypair, writes private key to file, prints public key)");
        System.out.println("  sign <DEVICE_ID>   (prints the client's license key)");
        System.out.println("  reset <RESET_CODE>   (prints the owner password reset key)");
    }
}