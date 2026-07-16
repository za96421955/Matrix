package com.matrix.common.util;

import com.matrix.common.constant.ClientType;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 终端检测工具
 * <p> <功能详细描述> </p>
 *
 * @author 陈晨
 */
public abstract class ClientDetectUtil {

    private ClientDetectUtil() {}

    /**
     * 根据 uname -a 的输出判断设备类型
     *
     * @param unameOutput uname -a 命令的输出
     * @return 设备类型
     */
    public static String getClientType(String unameOutput) {
        if (unameOutput == null || unameOutput.trim().isEmpty()) {
            return ClientType.UNKNOWN;
        }
        String lowerOutput = unameOutput.toLowerCase();

        // 检测虚拟机
        if (isVirtualMachine(lowerOutput)) {
            return ClientType.VIRTUAL;
        }

        // 检测移动设备
        if (isMobileDevice(lowerOutput)) {
            return ClientType.MOBILE;
        }

        // 检测物联网/嵌入式设备
        if (isIoTDevice(lowerOutput)) {
            return ClientType.IOT;
        }

        // 检测服务器
        if (isServer(lowerOutput)) {
            return ClientType.SERVER;
        }

        // 检测PC
        if (isPC(lowerOutput)) {
            return ClientType.PC;
        }

        return ClientType.UNKNOWN;
    }

    /**
     * 判断是否为移动设备
     */
    private static boolean isMobileDevice(String unameOutput) {
        // Android 设备
        if (unameOutput.contains("android")) {
            return true;
        }

        // iOS 设备 (iPhone, iPad, iPod)
        if (unameOutput.contains("iphone") ||
                unameOutput.contains("ipad") ||
                unameOutput.contains("ipod")) {
            return true;
        }

        // 通过内核版本判断移动设备
        if (unameOutput.contains("darwin")) {
            // Darwin 内核 + ARM 架构 + 版本 >= 20 可能是 iOS
            Pattern darwinPattern = Pattern.compile("darwin.*version\\s+(\\d+)\\.(\\d+)");
            Matcher matcher = darwinPattern.matcher(unameOutput);
            if (matcher.find()) {
                int majorVersion = Integer.parseInt(matcher.group(1));

                // 检查是否是 Mac
                // Mac 通常有明确的标识，如 RELEASE_ARM64_T8103, RELEASE_ARM64_T6000 等
                boolean isMac = unameOutput.contains("macbook") ||
                        unameOutput.contains("macmini") ||
                        unameOutput.contains("imac") ||
                        unameOutput.contains("macpro") ||
                        (unameOutput.contains("release_arm64_t") &&
                                !unameOutput.contains("iphone") &&
                                !unameOutput.contains("ipad") &&
                                !unameOutput.contains("ipod"));

                // iOS 设备的 Darwin 内核版本通常较高，但不是所有高版本都是 iOS
                // 如果明确是 Mac，返回 false
                if (isMac) {
                    return false;
                }

                // 非 Mac 的 Darwin + ARM 且版本 >= 20，很可能是 iOS
                if (majorVersion >= 20 && unameOutput.contains("arm")) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 判断是否为物联网设备
     */
    private static boolean isIoTDevice(String unameOutput) {
        // 常见 IoT 设备的关键词
        String[] iotKeywords = {
                "raspberry",     // 树莓派
                "openwrt",       // OpenWrt路由器
                "lede",          // LEDE路由器系统
                "dd-wrt",        // DD-WRT路由器
                "tomato",        // Tomato路由器
                "asuswrt",       // 华硕路由器
                "firmware",      // 固件
                "edge",          // 边缘计算设备
                "gateway",       // 网关
                "router",        // 路由器
                "switch",        // 交换机
                "armv5",         // 较老的ARM架构
                "armv6",         // IoT常用架构
                "armv7l",        // ARMv7
                "aarch64",       // ARM64
                "mips",          // MIPS架构
                "mips64",        // MIPS64架构
                "armv8"          // ARMv8
        };
        for (String keyword : iotKeywords) {
            if (unameOutput.contains(keyword)) {
                return true;
            }
        }

        // 检测是否运行在嵌入式系统上
        if (unameOutput.contains("linux") &&
                (unameOutput.contains("arm") ||
                        unameOutput.contains("mips") ||
                        unameOutput.contains("aarch64"))) {
            // 排除已知的PC/服务器架构
            if (!unameOutput.contains("x86") &&
                    !unameOutput.contains("amd64") &&
                    !unameOutput.contains("intel") &&
                    !unameOutput.contains("apple m1") &&
                    !unameOutput.contains("apple m2") &&
                    !unameOutput.contains("apple silicon")) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断是否为PC
     */
    private static boolean isPC(String unameOutput) {
        // macOS (包括 Intel 和 Apple Silicon)
        if (unameOutput.contains("darwin")) {
            // 检查是否是明确的 Mac
            boolean isMac = unameOutput.contains("macbook") ||
                    unameOutput.contains("macmini") ||
                    unameOutput.contains("imac") ||
                    unameOutput.contains("macpro") ||
                    unameOutput.contains("release_arm64_t") ||  // Apple Silicon Mac
                    unameOutput.contains("release_x86_64");     // Intel Mac

            if (isMac && !isMobileDevice(unameOutput)) {
                return true;
            }

            // 其他的 Darwin 系统也可能是 PC
            if (!isMobileDevice(unameOutput)) {
                return true;
            }
        }

        // Windows (通过Cygwin或WSL)
        if (unameOutput.contains("cygwin") ||
                unameOutput.contains("microsoft") ||
                unameOutput.contains("windows")) {
            return true;
        }

        // Linux PC
        if (unameOutput.contains("linux") &&
                (unameOutput.contains("x86_64") ||
                        unameOutput.contains("x86") ||
                        unameOutput.contains("amd64") ||
                        unameOutput.contains("intel") ||
                        unameOutput.contains("i386") ||
                        unameOutput.contains("i686"))) {
            return true;
        }

        return false;
    }

    /**
     * 判断是否为服务器
     */
    private static boolean isServer(String unameOutput) {
        // 服务器通常有特定的内核或版本标识
        String[] serverKeywords = {
                "server",
                "enterprise",
                "centos",
                "rhel",
                "red hat",
                "ubuntu server",
                "debian",
                "suse",
                "oracle linux",
                "amazon linux",
                "cloud",
                "vmware",
                "hypervisor",
                "esxi"
        };
        for (String keyword : serverKeywords) {
            if (unameOutput.contains(keyword)) {
                return true;
            }
        }

        // 通过主机名判断 (常见服务器命名模式)
        Pattern hostnamePattern = Pattern.compile("\\b([a-z]+)(\\d+)\\b", Pattern.CASE_INSENSITIVE);
        Matcher matcher = hostnamePattern.matcher(unameOutput);
        while (matcher.find()) {
            String prefix = matcher.group(1).toLowerCase();
            // 常见的服务器命名前缀
            if (prefix.matches("(srv|server|web|db|app|api|prod|staging|dev)")) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断是否为虚拟机
     */
    private static boolean isVirtualMachine(String unameOutput) {
        String[] vmKeywords = {
                "vmware",
                "virtualbox",
                "qemu",
                "kvm",
                "xen",
                "hyperv",
                "hypervisor",
                "docker",
                "container",
                "lxc",
                "lxd"
        };

        String lowerOutput = unameOutput.toLowerCase();
        for (String keyword : vmKeywords) {
            if (lowerOutput.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 测试方法
     */
    public static void main(String[] args) {
        // 测试用例
        String[] testCases = {
                "Darwin ZA-MacBook.local 23.6.0 Darwin Kernel Version 23.6.0: Mon Jul 29 21:14:21 PDT 2024; root:xnu-10063.141.2~1/RELEASE_ARM64_T8103 arm64",
                "Linux raspberrypi 5.10.63-v7+ #1459 SMP Wed Oct 6 16:41:10 BST 2021 armv7l GNU/Linux",
                "Linux android-xyz 4.19.113-perf+ #1 SMP PREEMPT Thu Jan 1 00:00:00 UTC 1970 aarch64",
                "Linux ubuntu-server 5.4.0-91-generic #102-Ubuntu SMP Fri Nov 5 16:31:28 UTC 2021 x86_64 x86_64 x86_64 GNU/Linux",
                "Linux openwrt 4.14.221 #0 SMP Thu Mar 11 15:29:30 2021 mips GNU/Linux",
                "Linux ip-172-31-12-45 5.4.0-1057-aws #59-Ubuntu SMP Wed Jan 12 20:39:32 UTC 2022 x86_64 x86_64 x86_64 GNU/Linux"
        };

        for (String testCase : testCases) {
            String type = getClientType(testCase);
            System.out.println("输入: " + testCase);
            System.out.println("设备类型: " + type);
            System.out.println("---");
        }
    }

}


