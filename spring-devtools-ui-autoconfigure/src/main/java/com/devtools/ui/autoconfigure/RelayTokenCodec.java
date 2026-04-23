package com.devtools.ui.autoconfigure;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Base64;
import java.util.List;

final class RelayTokenCodec {

    private static final int AES_KEY_BITS = 128;
    private static final int GCM_TAG_BITS = 128;
    private static final int GCM_IV_BYTES = 12;

    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();
    private final SecretKey secretKey;

    RelayTokenCodec(Clock clock) {
        this.clock = clock;
        this.secretKey = generateKey();
    }

    String encode(String sessionId, String ownerName, List<String> allowedRoles, String expiresAt) {
        String payload = String.join("|",
                "v1",
                sessionId,
                ownerName,
                String.join(",", allowedRoles),
                expiresAt,
                clock.instant().toString()
        );

        byte[] iv = new byte[GCM_IV_BYTES];
        secureRandom.nextBytes(iv);

        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return base64Url(iv) + "." + base64Url(encrypted);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to encode relay token", exception);
        }
    }

    String preview(String token) {
        if (token == null || token.length() < 16) {
            return "[unavailable]";
        }
        return token.substring(0, 8) + "…" + token.substring(token.length() - 4);
    }

    private SecretKey generateKey() {
        try {
            KeyGenerator generator = KeyGenerator.getInstance("AES");
            generator.init(AES_KEY_BITS);
            return generator.generateKey();
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to initialize relay token codec", exception);
        }
    }

    private String base64Url(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }
}
