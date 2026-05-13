package com.example.situation.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.situation.model.AppUser;
import java.nio.ByteBuffer;
import java.time.Instant;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class TotpServiceTest {

    private static final String SECRET = "MLREO3VEJAMKXWNRAPEBQF6KWXGCVOOKB6CA6KFF4A7IHNP3PTQQ";

    @Test
    void verifyCodeAcceptsSha1Codes() throws Exception {
        TotpService service = new TotpService("E-Situation");
        String code = buildCode(SECRET, currentStep(), "HmacSHA1");

        assertTrue(service.verifyCode(SECRET, code));
    }

    @Test
    void verifyCodeStillAcceptsSha256Codes() throws Exception {
        TotpService service = new TotpService("E-Situation");
        String code = buildCode(SECRET, currentStep(), "HmacSHA256");

        assertTrue(service.verifyCode(SECRET, code));
    }

    @Test
    void verifyCodeRejectsInvalidCodes() {
        TotpService service = new TotpService("E-Situation");

        assertFalse(service.verifyCode(SECRET, "000000"));
    }

    @Test
    void buildOtpAuthUriUsesSha1ForBetterAuthenticatorCompatibility() {
        TotpService service = new TotpService("E-Situation");
        AppUser user = new AppUser();
        user.setUsername("admin");

        String uri = service.buildOtpAuthUri(user, SECRET);

        assertTrue(uri.contains("algorithm=SHA1"));
    }

    private static long currentStep() {
        return Instant.now().getEpochSecond() / 30;
    }

    private static String buildCode(String secret, long timeStep, String algorithm) throws Exception {
        byte[] key = Base32Codec.decode(secret);
        byte[] counter = ByteBuffer.allocate(8).putLong(timeStep).array();
        Mac mac = Mac.getInstance(algorithm);
        mac.init(new SecretKeySpec(key, algorithm));
        byte[] hmac = mac.doFinal(counter);
        int offset = hmac[hmac.length - 1] & 0x0f;
        int binary = ((hmac[offset] & 0x7f) << 24)
            | ((hmac[offset + 1] & 0xff) << 16)
            | ((hmac[offset + 2] & 0xff) << 8)
            | (hmac[offset + 3] & 0xff);
        return String.format("%06d", binary % 1_000_000);
    }
}
