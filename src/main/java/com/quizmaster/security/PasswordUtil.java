package com.quizmaster.security;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;
import java.util.Locale;

/**
 * Salted PBKDF2-HMAC-SHA256 password hashing, carrying over the exact format
 * the desktop application used:
 *
 * <pre>PBKDF2:&lt;iterations&gt;:&lt;base64 salt&gt;:&lt;base64 hash&gt;</pre>
 *
 * <p>Because the format is unchanged, hashes produced by the JavaFX app (and
 * the demo hashes in {@code schema.sql}) verify here as-is.
 *
 * <p>Passwords are compared in constant time, and a row still holding a
 * plain-text password from an older build is upgraded transparently on the
 * first successful login.
 */
public final class PasswordUtil {

    /** PBKDF2 algorithm used for both hashing and verification. */
    public static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    /** Iteration count, kept at the desktop application's 210k so old hashes still verify. */
    public static final int ITERATIONS = 210_000;
    private static final int SALT_BYTES = 16;
    private static final int KEY_BITS = 256;
    private static final String PREFIX = "PBKDF2";
    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordUtil() {
    }

    /** Hashes a password with a fresh random salt. */
    public static String hash(String password) {
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        byte[] key = pbkdf2(password.toCharArray(), salt, ITERATIONS, KEY_BITS);
        Base64.Encoder encoder = Base64.getEncoder();
        return PREFIX + ":" + ITERATIONS + ":" + encoder.encodeToString(salt) + ":" + encoder.encodeToString(key);
    }

    /**
     * Verifies a password against a stored hash.
     *
     * @param password the plain-text password to check
     * @param stored   a {@code PBKDF2:...} hash; a row holding plain text is
     *                 compared (constant time) as a legacy fallback
     * @return true when the password matches
     */
    public static boolean verify(String password, String stored) {
        if (password == null || stored == null || stored.isEmpty()) {
            return false;
        }
        if (!isHashed(stored)) {
            // Legacy plain-text row: compare without leaking timing, and let the
            // caller re-hash it.
            return MessageDigest.isEqual(password.getBytes(StandardCharsets.UTF_8),
                    stored.getBytes(StandardCharsets.UTF_8));
        }

        String[] parts = stored.split(":");
        if (parts.length != 4) {
            return false;
        }
        int iterations;
        try {
            iterations = Integer.parseInt(parts[1]);
        } catch (NumberFormatException ex) {
            return false;
        }
        if (iterations <= 0) {
            return false;
        }

        byte[] salt;
        byte[] expected;
        try {
            salt = Base64.getDecoder().decode(parts[2]);
            expected = Base64.getDecoder().decode(parts[3]);
        } catch (IllegalArgumentException ex) {
            return false;
        }

        byte[] actual = pbkdf2(password.toCharArray(), salt, iterations, expected.length * 8);
        return MessageDigest.isEqual(expected, actual);
    }

    /** True when the stored value is a PBKDF2 hash rather than a plain-text password. */
    public static boolean isHashed(String stored) {
        return stored != null && stored.toUpperCase(Locale.ROOT).startsWith(PREFIX + ":");
    }

    /** True when a verified legacy row should be upgraded to a hash. */
    public static boolean needsUpgrade(String stored) {
        return stored != null && !isHashed(stored);
    }

    private static byte[] pbkdf2(char[] password, byte[] salt, int iterations, int keyBits) {
        PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, keyBits);
        try {
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).getEncoded();
        } catch (NoSuchAlgorithmException | InvalidKeySpecException ex) {
            throw new IllegalStateException("PBKDF2 is unavailable in this JVM", ex);
        } finally {
            spec.clearPassword();
        }
    }
}
