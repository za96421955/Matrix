package com.matrix.client.service;

import com.alibaba.fastjson2.JSONObject;
import com.matrix.client.context.Constant;
import com.matrix.client.context.MatrixClientProperties;
import com.matrix.client.util.FileUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;

/**
 * 系统服务
 * <p> <功能详细描述> </p>
 *
 * @author 陈晨
 */
@Slf4j
@Service
public class SystemService {

    @Resource
    private MatrixClientProperties properties;
    @Resource
    private CommandExecutor commandExecutor;
    @Resource
    private RegisterService registerService;
    @Resource
    private SkillManagerService skillManagerService;

    /**
     * @description 处理系统指令
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    public String commandHandle(String taskId, String command) throws IOException, InterruptedException {
        // os-info
        if (command.indexOf(Constant.SYSTEM_COMMAND.OS_INFO) == 0) {
            return commandExecutor.getOsInfo();
        }

        // 读取记忆
        if (command.indexOf(Constant.SYSTEM_COMMAND.READ_MEMORY) == 0) {
            String memory = null;
            File file = new File(properties.getClient().getBasic().getSettingsPath(), Constant.MEMORY);
            if (file.exists() && file.isFile()) {
                memory = FileUtil.read(file.getAbsolutePath());
            }
            return StringUtils.isBlank(memory) ? "暂无" : memory;
        }
        // 写入记忆
        if (command.indexOf(Constant.SYSTEM_COMMAND.WRITE_MEMORY) == 0) {
            String memory = command.substring(Constant.SYSTEM_COMMAND.WRITE_MEMORY.length());
            File file = new File(properties.getClient().getBasic().getSettingsPath(), Constant.MEMORY);
            FileUtil.write(file.getAbsolutePath(), memory);
            return "记忆更新完成";
        }

        // 读取操作说明
        if (command.indexOf(Constant.SYSTEM_COMMAND.READ_ASSISTANT) == 0) {
            String jsonStr = command.substring(Constant.SYSTEM_COMMAND.READ_ASSISTANT.length());
            JSONObject json = JSONObject.parseObject(jsonStr);
            String filePath = json.getString("filePath");
            File file = new File(filePath, Constant.ASSISTANT);
            String content = null;
            if (file.exists() && file.isFile()) {
                content = FileUtil.read(file.getAbsolutePath());
            }
            return StringUtils.isBlank(content) ? "暂无" : content;
        }
        // 写入操作说明
        if (command.indexOf(Constant.SYSTEM_COMMAND.WRITE_ASSISTANT) == 0) {
            String jsonStr = command.substring(Constant.SYSTEM_COMMAND.WRITE_ASSISTANT.length());
            JSONObject json = JSONObject.parseObject(jsonStr);
            String filePath = json.getString("filePath");
            String content = json.getString("content");
            File file = new File(filePath, Constant.ASSISTANT);
            FileUtil.write(file.getAbsolutePath(), content);
            return "操作说明更新完成";
        }

        // 读取 SKILL.md
        if (command.indexOf(Constant.SYSTEM_COMMAND.READ_SKILL) == 0) {
            String jsonStr = command.substring(Constant.SYSTEM_COMMAND.READ_SKILL.length());
            JSONObject json = JSONObject.parseObject(jsonStr);
            String skillName = json.getString("skillName");
            File skillDir = new File(properties.getClient().getBasic().getSkillPath(), skillName);
            File skillFile = new File(skillDir, Constant.SKILL_FILE);
            if (!skillFile.exists() || !skillFile.isFile()) {
                return "SKILL.md not found: " + skillFile.getAbsolutePath();
            }
            return FileUtil.read(skillFile.getAbsolutePath());
        }

        // 写入 SKILL.md
        if (command.indexOf(Constant.SYSTEM_COMMAND.WRITE_SKILL) == 0) {
            String jsonStr = command.substring(Constant.SYSTEM_COMMAND.WRITE_SKILL.length());
            JSONObject json = JSONObject.parseObject(jsonStr);
            String skillName = json.getString("skillName");
            String content = json.getString("content");
            File skillDir = new File(properties.getClient().getBasic().getSkillPath(), skillName);
            File skillFile = new File(skillDir, Constant.SKILL_FILE);
            FileUtil.write(skillFile.getAbsolutePath(), content);
            return "SKILL.md written: " + skillFile.getAbsolutePath();
        }

        // 安装 skill
        if (command.indexOf(Constant.SYSTEM_COMMAND.INSTALL_SKILL) == 0) {
            String jsonStr = command.substring(Constant.SYSTEM_COMMAND.INSTALL_SKILL.length());
            return skillManagerService.installSkill(jsonStr);
        }

        // 触发重新注册
        if (command.indexOf(Constant.SYSTEM_COMMAND.TRIGGER_REGISTER) == 0) {
            try {
                registerService.reload();
                return "trigger register success";
            } catch (Exception e) {
                return "trigger register fail: " + e.getMessage();
            }
        }

        return "";
    }

}
