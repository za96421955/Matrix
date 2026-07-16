package com.matrix.client.service;

import com.alibaba.fastjson2.JSONObject;
import com.matrix.client.context.Constant;
import com.matrix.client.context.ExecutorProperties;
import com.matrix.client.util.FileUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/**
 * @description skill 管理服务
 * <p> <功能详细描述> </p>
 *
 * @author 陈晨
 */
@Slf4j
@Service
public class SkillManagerService {

    @Resource
    private ExecutorProperties executorProperties;
    @Resource
    private CommandExecutor commandExecutor;

    /**
     * 安装 skill
     * <p> 支持从 URL 下载压缩包或指定本地文件路径安装 skill </p>
     *
     * @param jsonStr JSON 参数: {"source":"下载地址或本地路径","skillName":"可选，安装后的目录名"}
     * @return 安装结果信息
     */
    public String installSkill(String jsonStr) {
        Path tmpDir = null;
        try {
            JSONObject json = JSONObject.parseObject(jsonStr);
            String source = json.getString("source");
            String skillName = json.getString("skillName");

            if (StringUtils.isBlank(source)) {
                return "安装失败: source (下载地址或本地文件路径) 不可为空";
            }

            // === 步骤1: 获取源文件 ===
            tmpDir = Files.createTempDirectory("skill-install-");
            String sourceFilePath;

            if (source.startsWith("http://") || source.startsWith("https://")) {
                // 从 URL 下载
                String fileName = FileUtil.getFileName(source);
                if (StringUtils.isBlank(fileName)) {
                    fileName = "skill.zip";
                }
                Path downloadPath = tmpDir.resolve(fileName);
                String curlCmd = "curl -sL -o " + downloadPath.toAbsolutePath() + " " + source;
                commandExecutor.execute(null, curlCmd);
                if (!Files.exists(downloadPath) || Files.size(downloadPath) == 0) {
                    return "安装失败: 下载文件为空或失败 - " + source;
                }
                sourceFilePath = downloadPath.toAbsolutePath().toString();
                log.info("下载 skill 成功: {} -> {}", source, sourceFilePath);
            } else {
                // 本地文件
                File localFile = new File(source);
                if (!localFile.exists() || !localFile.isFile()) {
                    return "安装失败: 本地文件不存在 - " + source;
                }
                sourceFilePath = localFile.getAbsolutePath();
                log.info("使用本地 skill 文件: {}", sourceFilePath);
            }

            // === 步骤2: 检测文件类型并解压到临时目录 ===
            String ext = FileUtil.getExtension(sourceFilePath).toLowerCase();
            Path extractDir = tmpDir.resolve("extracted");
            Files.createDirectories(extractDir);

            switch (ext) {
                case "zip":
                    commandExecutor.execute(null, "unzip -o " + sourceFilePath + " -d " + extractDir.toAbsolutePath());
                    // zip slip 安全检查
                    Files.walk(extractDir)
                            .filter(p -> p.toFile().isFile())
                            .forEach(p -> {
                                if (p.toAbsolutePath().normalize().toString().contains("..")) {
                                    throw new RuntimeException("压缩包包含路径穿越风险: " + p);
                                }
                            });
                    break;
                case "gz":
                case "tgz":
                    commandExecutor.execute(null, "tar xzf " + sourceFilePath + " -C " + extractDir.toAbsolutePath());
                    break;
                case "bz2":
                    commandExecutor.execute(null, "tar xjf " + sourceFilePath + " -C " + extractDir.toAbsolutePath());
                    break;
                case "tar":
                    commandExecutor.execute(null, "tar xf " + sourceFilePath + " -C " + extractDir.toAbsolutePath());
                    break;
                default:
                    return "安装失败: 不支持的压缩格式 - " + ext + " (支持: zip, tar.gz, tgz, tar.bz2, tar)";
            }

            // === 步骤3: 推断 skill 名称 ===
            File[] extractedFiles = extractDir.toFile().listFiles();
            if (extractedFiles == null || extractedFiles.length == 0) {
                return "安装失败: 压缩包内容为空";
            }

            File skillRootDir = null;
            if (StringUtils.isNotBlank(skillName)) {
                // 使用指定的 skillName，解压根目录即为 skill 根目录
                skillRootDir = extractDir.toFile();
            } else {
                // 自动推断
                if (extractedFiles.length == 1 && extractedFiles[0].isDirectory()) {
                    // 解压后只有一个目录，使用该目录名作为 skill 名
                    skillRootDir = extractedFiles[0];
                    skillName = skillRootDir.getName();
                    log.info("自动推断 skill 名称: {}", skillName);
                } else {
                    // 尝试从 SKILL.md 的 YAML 头读取 name
                    File skillMdFile = new File(extractDir.toFile(), Constant.SKILL_FILE);
                    if (skillMdFile.exists() && skillMdFile.isFile()) {
                        skillName = parseSkillNameFromYaml(skillMdFile);
                    }
                    if (StringUtils.isBlank(skillName)) {
                        // 检查是否在某个子目录下有 SKILL.md
                        for (File f : extractedFiles) {
                            if (f.isDirectory()) {
                                File subSkillMd = new File(f, Constant.SKILL_FILE);
                                if (subSkillMd.exists() && subSkillMd.isFile()) {
                                    skillRootDir = f;
                                    skillName = f.getName();
                                    log.info("从子目录推断 skill 名称: {}", skillName);
                                    break;
                                }
                            }
                        }
                        if (StringUtils.isBlank(skillName)) {
                            return "安装失败: 无法推断 skill 名称，请通过 skillName 参数指定";
                        }
                    } else {
                        skillRootDir = extractDir.toFile();
                    }
                }
            }

            // 确认 skill 根目录和名称
            if (skillRootDir == null || StringUtils.isBlank(skillName)) {
                return "安装失败: 无法确定 skill 名称或根目录";
            }

            // === 步骤4: 验证 SKILL.md ===
            File skillMdFile = new File(skillRootDir, Constant.SKILL_FILE);
            if (!skillMdFile.exists() || !skillMdFile.isFile()) {
                return "安装失败: 未找到 SKILL.md (路径: " + skillMdFile.getAbsolutePath() + ")";
            }
            String skillMdContent = FileUtil.read(skillMdFile.getAbsolutePath());
            if (!skillMdContent.startsWith("---")) {
                return "安装失败: SKILL.md 缺少 YAML 头 (必须以 --- 开头)";
            }

            // === 步骤5: 安装到目标目录 ===
            String skillBasePath = executorProperties.getBasic().getSkillPath();
            Path targetDir = Paths.get(skillBasePath, skillName);

            // 如果目标目录已存在，先删除
            if (Files.exists(targetDir)) {
                log.info("目标目录已存在，先删除: {}", targetDir.toAbsolutePath());
                FileUtil.delete(targetDir.toAbsolutePath().toString());
            }
            Files.createDirectories(targetDir);

            // 复制文件 (使用 effectively final 的副本)
            final Path skillRootPath = skillRootDir.toPath();
            Files.walk(skillRootPath)
                    .forEach(sourcePath -> {
                        try {
                            Path relativePath = skillRootPath.relativize(sourcePath);
                            Path targetPath = targetDir.resolve(relativePath);
                            if (sourcePath.toFile().isDirectory()) {
                                Files.createDirectories(targetPath);
                            } else {
                                Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
                            }
                        } catch (IOException e) {
                            throw new RuntimeException("复制文件失败: " + sourcePath, e);
                        }
                    });

            // === 步骤6: 设置 bin 目录脚本可执行权限 ===
            File binDir = new File(targetDir.toFile(), "bin");
            if (binDir.exists() && binDir.isDirectory()) {
                commandExecutor.execute(null, "chmod +x " + binDir.getAbsolutePath() + "/*.sh 2>/dev/null; chmod +x " + binDir.getAbsolutePath() + "/*.py 2>/dev/null; true");
            }

            // === 步骤7: 列出已安装的文件 ===
            StringBuilder fileList = new StringBuilder();
            Files.walk(targetDir)
                    .filter(p -> p.toFile().isFile())
                    .sorted()
                    .forEach(p -> fileList.append("  - ").append(targetDir.relativize(p)).append("\n"));

            String result = "安装成功\n"
                    + "skill名称: " + skillName + "\n"
                    + "安装路径: " + targetDir.toAbsolutePath() + "\n"
                    + "文件列表:\n" + fileList.toString();

            log.info("skill 安装成功: {}", skillName);
            return result;

        } catch (Exception e) {
            log.error("安装 skill 异常", e);
            return "安装失败: " + e.getMessage();
        } finally {
            // 清理临时文件
            if (tmpDir != null) {
                try {
                    FileUtil.delete(tmpDir.toAbsolutePath().toString());
                } catch (IOException e) {
                    log.warn("清理临时目录失败: {}", tmpDir.toAbsolutePath(), e);
                }
            }
        }
    }

    /**
     * 从 SKILL.md 的 YAML 头中解析 name 字段
     */
    public String parseSkillNameFromYaml(File skillMdFile) throws IOException {
        String content = FileUtil.read(skillMdFile.getAbsolutePath());
        if (!content.startsWith("---")) {
            return null;
        }
        int endIndex = content.indexOf("---", 3);
        if (endIndex == -1) {
            return null;
        }
        String yamlHeader = content.substring(3, endIndex);
        for (String line : yamlHeader.split("\n")) {
            line = line.trim();
            if (line.startsWith("name:")) {
                String name = line.substring(5).trim();
                // 去掉可能的引号
                if (name.startsWith("\"") && name.endsWith("\"")) {
                    name = name.substring(1, name.length() - 1);
                } else if (name.startsWith("'") && name.endsWith("'")) {
                    name = name.substring(1, name.length() - 1);
                }
                return name;
            }
        }
        return null;
    }

//    /**
//     * 执行 bash 命令
//     */
//    public String execBash(String command) throws IOException, InterruptedException {
//        ProcessBuilder pb = new ProcessBuilder("bash", "-c", command);
//        pb.redirectErrorStream(true);
//        Process process = pb.start();
//        StringBuilder output = new StringBuilder();
//        try (BufferedReader reader = new BufferedReader(
//                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
//            String line;
//            while ((line = reader.readLine()) != null) {
//                output.append(line).append("\n");
//            }
//        }
//        int exitCode = process.waitFor();
//        String result = output.toString().trim();
//        if (exitCode != 0) {
//            // 忽略 chmod 出错的情况
//            if (!command.startsWith("chmod")) {
//                log.warn("命令执行异常, exitCode={}, command={}, output={}", exitCode, command, result);
//                throw new RuntimeException("命令执行失败, exitCode=" + exitCode + ", output=" + result);
//            }
//        }
//        return result;
//    }

}
