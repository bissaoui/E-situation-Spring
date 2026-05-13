package com.example.situation.security;

import com.example.situation.model.AppUser;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Instant;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class TotpService {

    private static final int SECRET_BYTES = 32;
    private static final int TIME_STEP_SECONDS = 30;
    private static final int OTP_DIGITS = 6;
    private static final String PRIMARY_HMAC_ALGORITHM = "HmacSHA1";
    private static final String LEGACY_HMAC_ALGORITHM = "HmacSHA256";

    private final SecureRandom secureRandom = new SecureRandom();
    private final String issuer;

    public TotpService(@Value("${security.mfa.issuer:E-Situation}") String issuer) {
        this.issuer = issuer;
    }

    public String generateSecret() {
        byte[] random = new byte[SECRET_BYTES];
        secureRandom.nextBytes(random);
        return Base32Codec.encode(random);
    }

    public boolean verifyCode(String secret, String code) {
        if (secret == null || secret.isBlank() || code == null || !code.matches("^\\d{6}$")) {
            return false;
        }
        long currentStep = Instant.now().getEpochSecond() / TIME_STEP_SECONDS;
        for (long offset = -1; offset <= 1; offset++) {
            long step = currentStep + offset;
            if (buildCode(secret, step, PRIMARY_HMAC_ALGORITHM).equals(code)
                || buildCode(secret, step, LEGACY_HMAC_ALGORITHM).equals(code)) {
                return true;
            }
        }
        return false;
    }

    public String buildOtpAuthUri(AppUser user, String secret) {
        String account = user == null ? "unknown" : user.getUsername();
        String label = urlEncode(issuer + ":" + account);
        return "otpauth://totp/" + label
            + "?secret=" + urlEncode(secret)
            + "&issuer=" + urlEncode(issuer)
            + "&algorithm=SHA1"
            + "&digits=" + OTP_DIGITS
            + "&period=" + TIME_STEP_SECONDS;
    }

    private String buildCode(String secret, long timeStep, String algorithm) {
        try {
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
            int otp = binary % 1_000_000;
            return String.format("%06d", otp);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Unable to verify MFA code", ex);
        }
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
