package com.matrix.client.service.impl;

import com.matrix.client.service.CommandExecutor;
import com.matrix.client.service.Fingerprint;
import com.matrix.client.util.Base64Util;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * PC 系统指纹
 * <p> <功能详细描述> </p>
 *
 * @author 陈晨
 */
@Service
@Slf4j
public class PCFingerprintImpl implements Fingerprint {
    // ---- 系统 UUID 获取命令 (原) ----
    private static final String MAC_UUID = "system_profiler SPHardwareDataType | awk '/Hardware UUID/ {print $3}'";
    private static final String LINUX_UUID = "cat /sys/class/dmi/id/product_uuid 2>/dev/null || cat /etc/machine-id 2>/dev/null";
    private static final String WINDOWS_UUID = "powershell.exe -Command \"Get-CimInstance -Class Win32_ComputerSystemProduct | Select-Object -ExpandProperty UUID\"";

    // ---- 主网卡 MAC 获取命令 ----
    private static final String MAC_MAC = "ifconfig en0 | awk '/ether/ {print $2}'";
    private static final String LINUX_MAC = "ip link show | grep -E '^[0-9]+: (eth|enp|eno)' | head -1 | awk '{print $2}' | cut -d/ -f1";
    private static final String WINDOWS_MAC = "powershell -Command \"Get-NetAdapter -Physical | Where-Object {$_.Status -eq 'Up'} | Select-Object -First 1 | ForEach-Object { $_.MacAddress }\"";

    @Resource
    private CommandExecutor commandExecutor;

    @Override
    public String get() throws IOException, InterruptedException {
        String os = System.getProperty("os.name").toLowerCase();
        String uuidCmd, macCmd;

        if (os.contains("mac")) {
            uuidCmd = MAC_UUID;
            macCmd = MAC_MAC;
        } else if (os.contains("linux")) {
            uuidCmd = LINUX_UUID;
            macCmd = LINUX_MAC;
        } else if (os.contains("win")) {
            uuidCmd = WINDOWS_UUID;
            macCmd = WINDOWS_MAC;
        } else {
            throw new UnsupportedOperationException("Unsupported OS: " + System.getProperty("os.name"));
        }

        // 1. 获取 UUID
        String uuid = executeCommandSafely(uuidCmd);
        // 2. 获取 MAC
        String mac = executeCommandSafely(macCmd);

        // 3. 如果 MAC 获取失败，尝试备选网卡（如 eth0 或使用 hostname）
        if (mac == null || mac.isEmpty()) {
            log.warn("主 MAC 获取失败，尝试使用 hostname 作为备选");
            mac = System.getenv("HOSTNAME");          // Linux/Mac
            if (mac == null || mac.isEmpty()) {
                mac = System.getenv("COMPUTERNAME");  // Windows
            }
            if (mac == null || mac.isEmpty()) {
                mac = "unknown-host";
            }
        }

        // 4. 拼接并哈希
        String raw = (uuid != null ? uuid.trim() : "") + "|" + (mac != null ? mac.trim() : "");
        if (raw.isEmpty()) {
            throw new IllegalStateException("无法获取任何系统标识信息");
        }

        String fingerprint = sha256Hex(raw);
        log.info("生成指纹：{} (基于 UUID={}, MAC={})", fingerprint, uuid, mac);
        return fingerprint;
    }

    /**
     * 执行命令并返回输出（去除换行），失败时返回 null
     */
    private String executeCommandSafely(String cmd) {
        try {
            String result = commandExecutor.execute(null, cmd);
            if (result != null) {
                return result.replaceAll("\\s+", ""); // 去除空白
            }
        } catch (Exception e) {
            log.warn("执行命令失败: {}, 错误: {}", cmd, e.getMessage());
        }
        return null;
    }

    /**
     * SHA-256 哈希转十六进制小写
     */
    private String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 算法不可用", e);
        }
    }

}


