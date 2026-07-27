package com.matrix.service.service.user;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 密码服务类
 * 使用 bcrypt 加密，cost=10
 */
@Service
public class PasswordService {
    
    @Value("${matrix.service.bcrypt-cost:10}")
    private int bcryptCost;
    
    /**
     * 哈希密码
     * @param plaintext 明文密码
     * @return bcrypt 哈希后的密码
     */
    public String hashPassword(String plaintext) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(bcryptCost);
        return encoder.encode(plaintext);
    }
    
    /**
     * 验证密码
     * @param plaintext 明文密码
     * @param hashed 哈希密码
     * @return 是否匹配
     */
    public boolean verifyPassword(String plaintext, String hashed) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(bcryptCost);
        return encoder.matches(plaintext, hashed);
    }

    /**
     * 获取稳定的哈希值（确定性哈希），同一输入始终产生相同结果。
     * 使用 SHA-256 算法，输出为十六进制小写字符串。
     * 适用场景：生成数据指纹、分片键、幂等性校验等（不可用于密码存储）。
     *
     * @param input 原始字符串
     * @return 64 位十六进制 SHA-256 哈希值
     * @throws RuntimeException 如果 SHA-256 算法不可用（理论上不会发生）
     */
    /** generateStableHash操作 */
    public String generateStableHash(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                // 将每个字节转为两位十六进制数
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 是 Java 标准算法，任何合规的 JVM 都支持
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

}


