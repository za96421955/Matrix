package com.matrix.common.util;

/**
 * 脱敏工具类
 */
public class DesensitizeUtil {
    
    /**
     * 手机号脱敏：138****1234
     */
    public static String maskPhone(String phone) {
        if (phone == null || phone.isEmpty()) {
            return phone;
        }
        if (phone.length() >= 7) {
            return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
        }
        return "****";
    }
    
    /**
     * 邮箱脱敏：test****@example.com
     */
    public static String maskEmail(String email) {
        if (email == null || email.isEmpty()) {
            return email;
        }
        int atIndex = email.indexOf('@');
        if (atIndex <= 0) {
            return "****";
        }
        String prefix = email.substring(0, atIndex);
        String domain = email.substring(atIndex);
        if (prefix.length() > 4) {
            prefix = prefix.substring(0, 4) + "****";
        } else {
            prefix = "****";
        }
        return prefix + domain;
    }
    
    /**
     * 身份证号脱敏：110101********1234
     */
    public static String maskIdCard(String idCard) {
        if (idCard == null || idCard.isEmpty()) {
            return idCard;
        }
        if (idCard.length() >= 18) {
            return idCard.substring(0, 6) + "********" + idCard.substring(14);
        }
        return "****";
    }
    
    /**
     * 姓名脱敏：张*三
     */
    public static String maskName(String name) {
        if (name == null || name.isEmpty()) {
            return name;
        }
        if (name.length() == 1) {
            return "*";
        }
        if (name.length() == 2) {
            return name.charAt(0) + "*";
        }
        return name.charAt(0) + "*" + name.substring(name.length() - 1);
    }
    
    /**
     * 密码脱敏：******
     */
    public static String maskPassword(String password) {
        if (password == null || password.isEmpty()) {
            return password;
        }
        return "******";
    }

}


