package com.agentledger.license;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * Verifies that a license key is a valid signature, by YOUR private key, over THIS
 * device's ID. The app holds only the PUBLIC key, so it can verify but never forge.
 */
public final class LicenseService {
    private LicenseService() {}

    /**
     * Your PUBLIC key, Base64 (X.509). Generated once by KeyGenTool (piece 4) and
     * pasted here. Safe to ship — it can only verify, not sign.
     */
    private static String PUBLIC_KEY_B64 = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAvARdDB6DCSKu+obPffnnvUssUAICA5lrjvtZUYd8O4255smYHCnT55mHktynvWE2gsjdcGsVWX7tBw+0WqF9+r/zlQFsHCGfHXYYqNPRb76D8fe5hoV/ixcobPdfnFIawX1ai/RIpLP7cxBT++gOj7717ZqrlJXAYfZ4GY19Z4VS6spN1QLwHd02662Yw/5RO+lhfjv7vDkXJaYQSw88hhMEFc4Qp3+cGpt5DEoHJTlpgyH9SqpdD+kfrF3eRi+AAyFozpsFhwy/8GnkPGoU7/bwJYIqfKz9WWuejFo2iCRdgAcUz+rFt80kIVlnDs4R6Cg8Uj/jSXqWISdNUjoWvQIDAQAB";
    /** True if licenseKey is a valid signature over deviceId. */
    public static boolean verify(String deviceId, String licenseKey) {
        if (deviceId == null || licenseKey == null || licenseKey.isBlank()) return false;
        try {
            byte[] sig = Base64.getDecoder().decode(licenseKey.trim().replaceAll("\\s", ""));
            Signature v = Signature.getInstance("SHA256withRSA");
            v.initVerify(publicKey());
            v.update(deviceId.getBytes(StandardCharsets.UTF_8));
            return v.verify(sig);
        } catch (Exception e) {
            return false;   // malformed key, wrong device, tampered -> invalid
        }
    }

    private static PublicKey publicKey() throws Exception {
        byte[] der = Base64.getDecoder().decode(PUBLIC_KEY_B64);
        return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
    }
}