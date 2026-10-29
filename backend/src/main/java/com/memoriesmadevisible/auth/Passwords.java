package com.memoriesmadevisible.auth;

import java.security.SecureRandom;

/** Random temporary passwords and reset tokens. */
final class Passwords {

    private static final SecureRandom RANDOM = new SecureRandom();
    /** No look-alikes (0/O, 1/l/I) — these get typed from an email. */
    private static final String ALPHABET = "abcdefghjkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private Passwords() {}

    static String temporary() {
        StringBuilder sb = new StringBuilder(12);
        for (int i = 0; i < 12; i++) sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        return sb.toString();
    }

    static String token() {
        byte[] b = new byte[32];
        RANDOM.nextBytes(b);
        return java.util.HexFormat.of().formatHex(b);
    }
}
