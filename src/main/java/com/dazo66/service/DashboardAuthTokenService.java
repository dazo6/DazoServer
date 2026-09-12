package com.dazo66.service;

import com.dazo66.config.DashboardAuthProperties;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.Base64;

@Service
public class DashboardAuthTokenService {

    private static final byte TOKEN_VERSION = 1;
    private static final int IV_LENGTH = 12;
    private static final int GCM_TAG_BITS = 128;
    private static final byte[] AAD = "dazo-dashboard-auth-v1".getBytes(StandardCharsets.UTF_8);

    private final DashboardAuthProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();

    public DashboardAuthTokenService(DashboardAuthProperties properties) {
        this.properties = properties;
    }

    public boolean authenticate(String userId, String password) {
        return constantTimeEquals(properties.getUserId(), userId)
                && constantTimeEquals(properties.getPassword(), password);
    }

    public String createToken() {
        try {
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, encryptionKey(), new GCMParameterSpec(GCM_TAG_BITS, iv));
            cipher.updateAAD(AAD);
            byte[] encrypted = cipher.doFinal(properties.getUserId().getBytes(StandardCharsets.UTF_8));
            ByteBuffer token = ByteBuffer.allocate(1 + iv.length + encrypted.length);
            token.put(TOKEN_VERSION).put(iv).put(encrypted);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(token.array());
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("无法生成登录 Token", exception);
        }
    }

    public boolean isValid(String tokenValue) {
        if (tokenValue == null || tokenValue.trim().isEmpty()) {
            return false;
        }
        try {
            byte[] token = Base64.getUrlDecoder().decode(tokenValue);
            if (token.length <= 1 + IV_LENGTH + 16 || token[0] != TOKEN_VERSION) {
                return false;
            }
            byte[] iv = new byte[IV_LENGTH];
            byte[] encrypted = new byte[token.length - 1 - IV_LENGTH];
            System.arraycopy(token, 1, iv, 0, iv.length);
            System.arraycopy(token, 1 + iv.length, encrypted, 0, encrypted.length);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, encryptionKey(), new GCMParameterSpec(GCM_TAG_BITS, iv));
            cipher.updateAAD(AAD);
            String userId = new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
            return constantTimeEquals(properties.getUserId(), userId);
        } catch (IllegalArgumentException | GeneralSecurityException exception) {
            return false;
        }
    }

    public String getCookieName() {
        return properties.getCookieName();
    }

    private SecretKeySpec encryptionKey() throws GeneralSecurityException {
        KeySpec keySpec = new PBEKeySpec(properties.getPassword().toCharArray(),
                properties.getTokenSalt().getBytes(StandardCharsets.UTF_8), 120000, 128);
        byte[] key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(keySpec).getEncoded();
        return new SecretKeySpec(key, "AES");
    }

    private boolean constantTimeEquals(String expected, String actual) {
        byte[] left = expected == null ? new byte[0] : expected.getBytes(StandardCharsets.UTF_8);
        byte[] right = actual == null ? new byte[0] : actual.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(left, right);
    }
}
