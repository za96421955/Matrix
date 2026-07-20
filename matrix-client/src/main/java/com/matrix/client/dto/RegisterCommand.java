package com.matrix.client.dto;

import com.alibaba.fastjson2.JSON;
import com.matrix.client.context.Constant;
import com.matrix.client.context.ClientProperties;
import com.matrix.client.util.FileUtil;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.ArrayUtils;

import java.io.File;
import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 注册请求
 * <p> <功能详细描述> </p>
 *
 * @author 陈晨
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegisterCommand implements Serializable {
    @Serial
    private static final long serialVersionUID = -7084098956082075954L;

    private String osInfo;
    private String apiKey;
    private List<Skill> skills;
    private List<Application> apps;
    private RiskLevel riskLevel;

    @Override
    public String toString() {
        return JSON.toJSONString(this);
    }

    /**
     * @description 加载注册信息
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    public RegisterCommand load(String clientId, ClientProperties clientProperties) throws Exception {

        // API Key
        this.setApiKey(System.getenv("DEEPSEEK_API_KEY"));

        // skill
        this.setSkills(new ArrayList<>());
        List<File> files = this.loadFiles(clientProperties.getBasic().getSkillPath(), Constant.SKILL_FILE);
        for (File file : files) {
            Skill skill = Skill.parse(FileUtil.read(file.getAbsolutePath()));
            skill.setClientId(clientId);
            skill.setRootPath(file.getParentFile().getAbsolutePath());
            this.getSkills().add(skill);
        }

        // app
        this.setApps(new ArrayList<>());
        files = this.loadFiles(clientProperties.getBasic().getAppPath(), Constant.APP_FILE);
        for (File file : files) {
            this.getApps().add(Application.parse(clientId,
                    file.getParentFile().getAbsolutePath(),
                    FileUtil.read(file.getAbsolutePath())));
        }

        // risk-level
        File root = new File(clientProperties.getBasic().getRiskLevelPath());
        if (root.exists() && root.isFile()) {
            this.setRiskLevel(RiskLevel.parse(FileUtil.read(root.getAbsolutePath())));
        }
        return this;
    }

    /**
     * @description 加载文件列表
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    private List<File> loadFiles(String rootPath, String fileName) {
        List<File> files = new ArrayList<>();
        File root = new File(rootPath);
        if (!root.exists() || !root.isDirectory()) {
            return files;
        }
        // 获取文件
        File file = new File(root, fileName);
        if (file.exists() && file.isFile()) {
            files.add(file);
        }
        // 扫描子目录
        File[] dirs = root.listFiles(File::isDirectory);
        if (ArrayUtils.isEmpty(dirs)) {
            return files;
        }
        for (File dir : dirs) {
            files.addAll(this.loadFiles(dir.getAbsolutePath(), fileName));
        }
        return files;
    }

}


