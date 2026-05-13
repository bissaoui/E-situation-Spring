package com.example.situation.security;

import java.io.ByteArrayOutputStream;

final class Base32Codec {

    private static final char[] ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".toCharArray();

    private Base32Codec() {
    }

    static String encode(byte[] value) {
        StringBuilder output = new StringBuilder((value.length * 8 + 4) / 5);
        int buffer = 0;
        int bitsLeft = 0;
        for (byte b : value) {
            buffer = (buffer << 8) | (b & 0xff);
            bitsLeft += 8;
            while (bitsLeft >= 5) {
                output.append(ALPHABET[(buffer >> (bitsLeft - 5)) & 0x1f]);
                bitsLeft -= 5;
            }
        }
        if (bitsLeft > 0) {
            output.append(ALPHABET[(buffer << (5 - bitsLeft)) & 0x1f]);
        }
        return output.toString();
    }

    static byte[] decode(String input) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        int buffer = 0;
        int bitsLeft = 0;
        for (char raw : input.toUpperCase().toCharArray()) {
            if (raw == '=' || Character.isWhitespace(raw)) {
                continue;
            }
            int value = decodeChar(raw);
            buffer = (buffer << 5) | value;
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                output.write((buffer >> (bitsLeft - 8)) & 0xff);
                bitsLeft -= 8;
            }
        }
        return output.toByteArray();
    }

    private static int decodeChar(char value) {
        if (value >= 'A' && value <= 'Z') {
            return value - 'A';
        }
        if (value >= '2' && value <= '7') {
            return value - '2' + 26;
        }
        throw new IllegalArgumentException("Invalid Base32 character");
    }
}
