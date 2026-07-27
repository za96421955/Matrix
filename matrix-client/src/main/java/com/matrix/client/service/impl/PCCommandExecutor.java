package com.matrix.client.service.impl;

import com.alibaba.fastjson2.JSONObject;
import com.matrix.client.context.MatrixClientProperties;
import com.matrix.client.service.CommandExecutor;
import com.matrix.client.service.SystemService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

/**
 * @description PC 指令执行器
 * <p> <功能详细描述> </p>
 *
 * @author 陈晨
 */
@Slf4j
@Component
public class PCCommandExecutor implements CommandExecutor {

    @Resource
    private MatrixClientProperties properties;

    private final SystemService systemService;
    /** PCCommandExecutor操作 */
    public PCCommandExecutor(@Lazy SystemService systemService) {
        this.systemService = systemService;
    }

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
    /** 获取OsInfo属性值 */
    public String getOsInfo() throws IOException, InterruptedException {
        if (this.isWindows()) {
            String command = "[System.Environment]::OSVersion.VersionString";
            return this.execute(this.getPowershell(command), null, command);
        }
        String command = "uname -a";
        JSONObject json = new JSONObject();
        json.put("name", properties.getClient().getName());
        json.put("desc", properties.getClient().getDesc());
        json.put("osInfo", this.execute(null, command));
        return json.toJSONString();
    }

    @Override
    /** 执行命令或任务 */
    public String execute(String taskId, String command) throws IOException, InterruptedException {
        // 空值校验
        if (command == null) {
            log.warn("[{}] 命令为空", taskId);
            return "命令为空";
        }
        // 检测 null 字节注入
        if (command.indexOf('\0') >= 0) {
            log.warn("[{}] 命令包含 null 字节，疑似注入攻击: {}", taskId, command);
            return "命令包含非法字符";
        }
        // 处理系统指令
        String result = systemService.commandHandle(taskId, command);
        if (StringUtils.isNotBlank(result)) {
            return result;
        }

        // 获取执行器
        String dir = null;
        String cmd = command;
        try {
            JSONObject json = JSONObject.parseObject(command);
            dir = json.getString("dir");
            cmd = json.getString("command");
        } catch (Exception ignore) {}
        // 工作目录安全检查
        ProcessBuilder processBuilder = this.getProcessBuilder(cmd);
        try {
            if (StringUtils.isNotBlank(dir)) {
                // 校验目录路径：去除 .. 路径穿越
                File dirFile = new File(dir).getCanonicalFile();
                if (dir.contains("..")) {
                    log.warn("[{}] 工作目录包含路径穿越: {}", taskId, dir);
                }
                processBuilder.directory(dirFile);
            }
            return this.execute(processBuilder, taskId, cmd);
        } catch (Exception e) {
            log.warn("[{}] 命令执行异常: {}", taskId, e.getMessage());
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
        Process process = processBuilder.start();
        StringBuilder sb = new StringBuilder();

        // 用一个子线程读取标准输出
        Thread readerThread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log.info(line);
                    sb.append(line).append("\n");
                }
            } catch (IOException e) {
                // 进程被销毁或流关闭时可能抛出异常，属于正常情况
                log.debug("读取输出流时被中断或关闭", e);
            }
        });
        readerThread.start();

        // 主线程等待进程结束，最多等 3 分钟
        boolean finished = process.waitFor(3, TimeUnit.MINUTES);
        if (!finished) {
            // 超时：强制终止进程，并中断读取线程
            process.destroyForcibly();
            readerThread.interrupt();  // 让读取线程尽快退出
            log.error("[{}] command={}, 命令执行超时 (3分钟), 已强制终止", taskId, command);
            throw new RuntimeException("命令执行超时: " + command);
        }
        // 等待读取线程结束，确保输出收集完整
        readerThread.join();

        int exitCode = process.exitValue();  // 此时进程已结束，直接取值不会阻塞
        log.debug("[{}] command={}, 命令执行结果: {}", taskId, command, sb);
        if (exitCode != 0) {
            log.warn("[{}] command={}, 命令执行失败, 退出码: {}", taskId, command, exitCode);
            throw new RuntimeException(sb.toString());
        }
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
