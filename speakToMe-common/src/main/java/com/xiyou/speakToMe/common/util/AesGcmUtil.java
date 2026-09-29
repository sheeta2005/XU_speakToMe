package com.xiyou.speakToMe.common.util;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM 加解密工具（JDK 原生实现，无第三方依赖）。
 * 用于 openid、手机号等敏感字段的加密存储。
 *
 * 密钥要求：32 字节随机值的 Base64，可用 openssl rand -base64 32 生成，
 * 通过环境变量 AES_KEY 注入，禁止入库、入日志、入仓库。
 *
 * 密文格式：Base64( 12 字节 IV + GCM 密文(含 128bit tag) )
 */
public final class AesGcmUtil {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int KEY_LENGTH = 32;
    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;

    private AesGcmUtil() {
    }

    /** 生成 32 字节 Base64 密钥（部署初始化用） */
    public static String generateKey() {
        byte[] key = new byte[KEY_LENGTH];
        new SecureRandom().nextBytes(key);
        return Base64.getEncoder().encodeToString(key);
    }

    /** 加密，返回 Base64(IV + cipherText+tag) */
    public static String encrypt(String plainText, String base64Key) {
        try {
            byte[] key = Base64.getDecoder().decode(base64Key);
            byte[] iv = new byte[IV_LENGTH];
            new SecureRandom().nextBytes(iv);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE,
                    new SecretKeySpec(key, "AES"),
                    new GCMParameterSpec(TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            byte[] payload = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, payload, 0, iv.length);
            System.arraycopy(encrypted, 0, payload, iv.length, encrypted.length);
            return Base64.getEncoder().encodeToString(payload);
        } catch (Exception e) {
            throw new IllegalStateException("AES-GCM 加密失败", e);
        }
    }

    /** 解密，输入为 encrypt 的输出格式 */
    public static String decrypt(String cipherText, String base64Key) {
        try {
            byte[] payload = Base64.getDecoder().decode(cipherText);
            byte[] key = Base64.getDecoder().decode(base64Key);
            byte[] iv = new byte[IV_LENGTH];
            byte[] encrypted = new byte[payload.length - IV_LENGTH];
            System.arraycopy(payload, 0, iv, 0, IV_LENGTH);
            System.arraycopy(payload, IV_LENGTH, encrypted, 0, encrypted.length);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE,
                    new SecretKeySpec(key, "AES"),
                    new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("AES-GCM 解密失败", e);
        }
    }
}
