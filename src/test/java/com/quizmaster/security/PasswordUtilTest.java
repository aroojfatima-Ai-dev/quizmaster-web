package com.quizmaster.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Hashing and verification.
 *
 * <p>The two expected hashes were produced by the desktop application (and are
 * the demo rows documented in its {@code schema.sql}); pinning them here proves
 * that accounts created by the JavaFX version can still sign in on the web
 * version.
 */
class PasswordUtilTest {

    /** PBKDF2-HMAC-SHA256, 210k iterations, of "teacher123" with the documented salt. */
    private static final String TEACHER_HASH =
            "PBKDF2:210000:AAECAwQFBgcICQoLDA0ODw==:+WRaa9Yu9g8N/ZMdLu2CHwpmSSKH7ErY4Ti4IQkz84M=";
    /** PBKDF2-HMAC-SHA256, 210k iterations, of "student123". */
    private static final String STUDENT_HASH =
            "PBKDF2:210000:EBESExQVFhcYGRobHB0eHw==:xmlfVVbH3zxLNMnd3Dt3fKgCt7tUab4jgMkPZlzVvuE=";

    @Test
    @DisplayName("hashes written by the JavaFX app still verify")
    void desktopHashesVerify() {
        assertTrue(PasswordUtil.verify("teacher123", TEACHER_HASH));
        assertTrue(PasswordUtil.verify("student123", STUDENT_HASH));
        assertFalse(PasswordUtil.verify("teacher124", TEACHER_HASH));
        assertFalse(PasswordUtil.verify("student123", TEACHER_HASH));
    }

    @Test
    @DisplayName("a fresh hash round-trips and keeps the documented format")
    void hashRoundTrips() {
        String hash = PasswordUtil.hash("s3cret-password");
        assertTrue(hash.startsWith("PBKDF2:210000:"));
        assertTrue(PasswordUtil.verify("s3cret-password", hash));
        assertFalse(PasswordUtil.verify("s3cret-passworD", hash));
    }

    @Test
    @DisplayName("the same password hashes to two different values (random salt)")
    void saltsAreRandom() {
        String first = PasswordUtil.hash("same-password");
        String second = PasswordUtil.hash("same-password");
        assertNotEquals(first, second);
        assertTrue(PasswordUtil.verify("same-password", first));
        assertTrue(PasswordUtil.verify("same-password", second));
    }

    @Test
    @DisplayName("a tampered or truncated hash is rejected instead of throwing")
    void malformedHashesAreRejected() {
        assertFalse(PasswordUtil.verify("teacher123", "PBKDF2:210000:AAECAwQFBgcICQoLDA0ODw=="));
        assertFalse(PasswordUtil.verify("teacher123", "PBKDF2:not-a-number:AAECAwQFBgcICQoLDA0ODw==:AAAA"));
        assertFalse(PasswordUtil.verify("teacher123", "PBKDF2:210000:!!!:!!!"));
        assertFalse(PasswordUtil.verify("teacher123", ""));
        assertFalse(PasswordUtil.verify(null, TEACHER_HASH));
        assertFalse(PasswordUtil.verify("teacher123", null));
        assertFalse(PasswordUtil.verify("teacher123", "PBKDF2:210000:AAECAwQFBgcICQoLDA0ODw==:dGFtcGVyZWQ="));
    }

    @Test
    @DisplayName("a legacy plain-text row verifies and is flagged for upgrade")
    void legacyPlainTextIsUpgraded() {
        assertFalse(PasswordUtil.isHashed("teacher123"));
        assertTrue(PasswordUtil.verify("teacher123", "teacher123"));
        assertFalse(PasswordUtil.verify("wrong", "teacher123"));
        assertTrue(PasswordUtil.needsUpgrade("teacher123"));
        assertFalse(PasswordUtil.needsUpgrade(TEACHER_HASH));
        assertTrue(PasswordUtil.isHashed(TEACHER_HASH));
    }
}
