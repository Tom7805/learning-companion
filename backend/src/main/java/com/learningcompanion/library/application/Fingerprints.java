package com.learningcompanion.library.application;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.regex.Pattern;

/** Dấu vân tay nội dung của tệp: SHA-256 dạng hex chữ thường, dùng để báo tài liệu trùng trong kho (QTN-17). */
public final class Fingerprints {

    public static final Pattern SHA256_HEX = Pattern.compile("[0-9a-f]{64}");

    private Fingerprints() {
    }

    public static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("Máy ảo Java thiếu SHA-256", ex);
        }
    }
}
