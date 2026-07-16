package com.matrix.client.service.impl;

import com.alibaba.fastjson2.JSONObject;
import com.matrix.client.service.CommandExecutor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * @description PC 指令执行器
 * <p> <功能详细描述> </p>
 *
 * @author 陈晨
 */
@Slf4j
@Component
public class PCCommandExecutor implements CommandExecutor {

    /**
     * @description 判断是否 windows 系统
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    private boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }

    @Override
    public String getOsInfo() throws IOException, InterruptedException {
        if (this.isWindows()) {
            String command = "[System.Environment]::OSVersion.VersionString";
            return this.execute(this.getPowershell(command), null, command);
        }
        String command = "uname -a";
        return this.execute(null, command);
    }

    @Override
    public String execute(String taskId, String command) {
        // 获取执行器
        String dir = null;
        String cmd = command;
        try {
            JSONObject json = JSONObject.parseObject(command);
            dir = json.getString("dir");
            cmd = json.getString("command");
        } catch (Exception ignore) {}
        ProcessBuilder processBuilder = this.getProcessBuilder(cmd);
        // 执行
        try {
            if (StringUtils.isNotBlank(dir)) {
                processBuilder.directory(new File(dir));
            }
            return this.execute(processBuilder, taskId, cmd);
        } catch (Exception e) {
            return "命令执行失败: " + e.getMessage();
        }
    }

    /**
     * @description 执行命令
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    private String execute(ProcessBuilder processBuilder, String taskId, String command)
            throws IOException, InterruptedException {
        // 执行
        Process process = processBuilder.start();
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                log.info(line);
                sb.append(line).append("\n");
            }
        }
        log.debug("[{}] command={}, 命令执行结果: {}", taskId, command, sb);
        // 终止
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            log.error("[{}] command={}, 命令执行失败, 退出码: {}", taskId, command, exitCode);
            throw new RuntimeException(sb.toString());
        }
        // 执行成功
        return sb.toString();
    }

    /**
     * @description 获取执行器
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    private ProcessBuilder getProcessBuilder(String command) {
        return this.isWindows() ? this.getCmd(command) : this.getBash(command);
    }

    private ProcessBuilder getBash(String command) {
        ProcessBuilder processBuilder = new ProcessBuilder("bash", "-c", command);
        processBuilder.redirectErrorStream(true);
        return processBuilder;
    }

    private ProcessBuilder getCmd(String command) {
        ProcessBuilder processBuilder = new ProcessBuilder("cmd", "/c", command);
        processBuilder.redirectErrorStream(true);
        return processBuilder;
    }

    private ProcessBuilder getPowershell(String command) {
        ProcessBuilder processBuilder = new ProcessBuilder(
                "powershell", "-Command",
                "[Console]::OutputEncoding=[System.Text.Encoding]::UTF8; " + command);
        processBuilder.redirectErrorStream(true);
        return processBuilder;
    }

}


