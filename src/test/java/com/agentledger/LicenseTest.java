package com.agentledger;

import com.agentledger.license.LicenseService;
import org.junit.jupiter.api.*;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class LicenseTest {

    private static KeyPair kp;

    @BeforeAll static void setup() throws Exception {
        KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
        g.initialize(2048);
        kp = g.generateKeyPair();
        // inject our test public key into LicenseService via reflection
        Field f = LicenseService.class.getDeclaredField("PUBLIC_KEY_B64");
        f.setAccessible(true);
        // remove 'final' modifier handling: set on the static field
        f.set(null, Base64.getEncoder().encodeToString(kp.getPublic().getEncoded()));
    }

    private static String sign(String deviceId) throws Exception {
        Signature s = Signature.getInstance("SHA256withRSA");
        s.initSign(kp.getPrivate());
        s.update(deviceId.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(s.sign());
    }

    @Test void validLicensePassesForCorrectDevice() throws Exception {
        String device = "K7M2-9XQ4-P3R8";
        assertTrue(LicenseService.verify(device, sign(device)));
    }

    @Test void licenseForOneDeviceFailsOnAnother() throws Exception {
        String licenseForA = sign("DEVICE-AAAA");
        assertFalse(LicenseService.verify("DEVICE-BBBB", licenseForA));
    }

    @Test void tamperedLicenseIsRejected() throws Exception {
        String device = "K7M2-9XQ4-P3R8";
        String good = sign(device);
        byte[] sig = Base64.getDecoder().decode(good);
        sig[0] ^= 0x01;            // flip one bit in the actual signature bytes
        String tampered = Base64.getEncoder().encodeToString(sig);
        assertFalse(LicenseService.verify(device, tampered));
    }

    @Test void blankLicenseIsRejected() {
        assertFalse(LicenseService.verify("ANY-DEVICE", ""));
        assertFalse(LicenseService.verify("ANY-DEVICE", null));
    }
}