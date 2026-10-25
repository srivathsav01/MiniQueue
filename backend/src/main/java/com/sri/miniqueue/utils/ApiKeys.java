package com.sri.miniqueue.utils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/** Creating, hashing and recognising API keys. */
public final class ApiKeys {

    /** Lets people and secret scanners (GitHub's included) recognise a leaked MiniQueue key. */
    public static final String PREFIX = "mq_";

    /** Dashboard session tokens. A different prefix, so the filter knows which table to check. */
    public static final String SESSION_PREFIX = "mqs_";

    private static final int RANDOM_BYTES = 32;            // 256 bits: unguessable
    private static final int DISPLAY_LENGTH = PREFIX.length() + 8;
    private static final int MIN_LENGTH = PREFIX.length() + 32;
    private static final SecureRandom RANDOM = new SecureRandom();

    private ApiKeys() {
    }

    /** e.g. "mq_q3Zx..." (46 characters). URL-safe Base64, so it fits in a header unchanged. */
    public static String generate() {
        return randomToken(PREFIX);
    }

    /** A prefix plus 256 random bits. Used for API keys, sessions, OAuth state and one-time codes. */
    public static String randomToken(String prefix) {
        byte[] bytes = new byte[RANDOM_BYTES];
        RANDOM.nextBytes(bytes);
        return prefix + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static boolean looksLikeSessionToken(String value) {
        return value != null && value.startsWith(SESSION_PREFIX)
                && value.length() >= SESSION_PREFIX.length() + 32 && value.length() <= 200;
    }

    public static String hash(String key) {
        try {
            // A new MessageDigest per call: instances are not thread-safe, and creating one is cheap.
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(sha256.digest(key.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is missing from this JVM", e);
        }
    }

    public static String displayPrefix(String key) {
        return key.substring(0, Math.min(DISPLAY_LENGTH, key.length()));
    }

    /** A cheap check before touching the database: obvious garbage never costs a query. */
    public static boolean looksLikeKey(String value) {
        return value != null && value.startsWith(PREFIX) && value.length() >= MIN_LENGTH && value.length() <= 200;
    }
}