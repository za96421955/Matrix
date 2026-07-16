package com.matrix.client.service.impl;

import com.matrix.client.service.CommandExecutor;
import com.matrix.client.service.Fingerprint;
import com.matrix.client.util.Base64Util;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;

/**
 * PC 系统指纹
 * <p> <功能详细描述> </p>
 *
 * @author 陈晨
 */
@Service
@Slf4j
public class PCFingerprintImpl implements Fingerprint {
//    private static final String FINGER_PRINT_CMD = "(echo \"Host: $(hostname)\"; echo \"Machine: $(cat /etc/machine-id 2>/dev/null || echo 'no-machine-id')\"; uname -s -m; cat /etc/os-release 2>/dev/null | grep -E '^ID=|^VERSION_ID=' 2>/dev/null | sort; echo \"Arch: $(uname -m)\") 2>/dev/null | sha256sum | cut -d' ' -f1";

    private static final String MAC = "system_profiler SPHardwareDataType | awk '/Hardware UUID/ {print $3}'";
    private static final String LINUX = "cat /sys/class/dmi/id/product_uuid 2>/dev/null || cat /etc/machine-id 2>/dev/null";
    private static final String WINDOWS = "powershell.exe -Command \"Get-CimInstance -Class Win32_ComputerSystemProduct | Select-Object -ExpandProperty UUID\"";

    @Resource
    private CommandExecutor commandExecutor;

    @Override
    public String get() throws IOException, InterruptedException {
        String os = System.getProperty("os.name").toLowerCase();
        String cli;
        if (os.contains("mac")) {
            cli = MAC;
        } else if (os.contains("linux")) {
            cli = LINUX;
        } else if (os.contains("win")) {
            cli = WINDOWS;
        } else {
            throw new UnsupportedOperationException("Unsupported OS: " + System.getProperty("os.name"));
        }
        return Base64Util.formatHex(commandExecutor.execute(null, cli));
    }

}


