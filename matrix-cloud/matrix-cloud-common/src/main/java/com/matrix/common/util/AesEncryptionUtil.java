package com.matrix.common.util;

import lombok.extern.slf4j.Slf4j;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM 加密工具类
 * 
 * 加密模式：AES/GCM/NoPadding
 * 密钥长度：256 位
 * IV 长度：12 字节（96 位）
 * 
 * 设计依据：command-详细设计.md 5.2 节
 */
@Slf4j
public class AesEncryptionUtil {
    
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int KEY_LENGTH = 32; // 256 位 = 32 字节
    private static final int IV_LENGTH = 12; // 96 位
    private static final int TAG_LENGTH_BIT = 128; // GCM 认证标签长度
    
    /**
     * AES-256-GCM 加密
     * 
     * @param keyBase64 Base64 编码的密钥
     * @param plaintext 明文数据
     * @return Base64 编码的加密结果（IV + 密文）
     * @throws Exception 加密异常
     */
    public static String encrypt(String keyBase64, String plaintext) throws Exception {
        log.debug("aes encrypt start, plaintext length={}", plaintext.length());
        
        // 解码密钥
        byte[] keyBytes = Base64.getDecoder().decode(keyBase64);
        if (keyBytes.length != KEY_LENGTH) {
            throw new IllegalArgumentException("Key must be 32 bytes for AES-256");
        }
        
        // 生成随机 IV
        SecureRandom secureRandom = new SecureRandom();
        byte[] iv = new byte[IV_LENGTH];
        secureRandom.nextBytes(iv);
        
        // 创建密钥规范
        SecretKeySpec secretKey = new SecretKeySpec(keyBytes, "AES");
        
        // 创建 Cipher 实例
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        GCMParameterSpec parameterSpec = new GCMParameterSpec(TAG_LENGTH_BIT, iv);
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec);
        
        // 加密数据
        byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
        
        // 合并 IV 和密文（IV 在前 12 字节，密文在后）
        byte[] encryptedData = new byte[IV_LENGTH + ciphertext.length];
        System.arraycopy(iv, 0, encryptedData, 0, IV_LENGTH);
        System.arraycopy(ciphertext, 0, encryptedData, IV_LENGTH, ciphertext.length);
        
        // Base64 编码返回
        String result = Base64.getEncoder().encodeToString(encryptedData);
        log.debug("aes encrypt success, encrypted length={}", result.length());
        
        return result;
    }
    
    /**
     * AES-256-GCM 解密
     * 
     * @param keyBase64 Base64 编码的密钥
     * @param encryptedDataBase64 Base64 编码的加密数据（IV + 密文）
     * @return 解密后的明文
     * @throws Exception 解密异常
     */
    public static String decrypt(String keyBase64, String encryptedDataBase64) throws Exception {
        log.debug("aes decrypt start");
        
        // 解码密钥和加密数据
        byte[] keyBytes = Base64.getDecoder().decode(keyBase64);
        byte[] encryptedData = Base64.getDecoder().decode(encryptedDataBase64);
        
        if (keyBytes.length != KEY_LENGTH) {
            throw new IllegalArgumentException("Key must be 32 bytes for AES-256");
        }
        
        if (encryptedData.length < IV_LENGTH) {
            throw new IllegalArgumentException("Encrypted data too short");
        }
        
        // 提取 IV 和密文
        byte[] iv = new byte[IV_LENGTH];
        byte[] ciphertext = new byte[encryptedData.length - IV_LENGTH];
        System.arraycopy(encryptedData, 0, iv, 0, IV_LENGTH);
        System.arraycopy(encryptedData, IV_LENGTH, ciphertext, 0, ciphertext.length);
        
        // 创建密钥规范
        SecretKeySpec secretKey = new SecretKeySpec(keyBytes, "AES");
        
        // 创建 Cipher 实例
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        GCMParameterSpec parameterSpec = new GCMParameterSpec(TAG_LENGTH_BIT, iv);
        cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec);
        
        // 解密数据
        byte[] plaintextBytes = cipher.doFinal(ciphertext);
        String result = new String(plaintextBytes, StandardCharsets.UTF_8);
        
        log.debug("aes decrypt success, plaintext length={}", result.length());
        
        return result;
    }
}
