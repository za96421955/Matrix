package com.matrix.client.util;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.FileVisitResult;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
/**
 * 文件工具类 (基于 JDK 17+ NIO.2)
 *
 * @author 陈晨
 */
@Slf4j
public final class FileUtil {

	private FileUtil() {}

	/** 备份文件后缀时间戳格式 */
	private static final DateTimeFormatter BACKUP_TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");


	/**
	 * 从完整路径中提取文件名（包含扩展名）
	 * 例如：/path/to/demo.php -> demo.php
	 */
	public static String getFileName(String filePath) {
		if (filePath == null || filePath.isEmpty()) {
			return "";
		}

		// 处理路径分隔符
		filePath = filePath.replace('\\', '/');

		int lastSlash = filePath.lastIndexOf('/');
		if (lastSlash != -1) {
			return filePath.substring(lastSlash + 1);
		}

		return filePath;
	}

	/**
	 * 提取文件名（不包含扩展名）
	 * 例如：demo.php -> demo
	 */
	public static String getBaseName(String filePath) {
		String fileName = getFileName(filePath);

		int lastDot = fileName.lastIndexOf('.');
		if (lastDot != -1) {
			return fileName.substring(0, lastDot);
		}

		return fileName;
	}

	/**
	 * 提取扩展名（包括点）
	 * 例如：demo.php -> .php
	 */
	public static String getExtensionWithDot(String filePath) {
		String fileName = getFileName(filePath);

		int lastDot = fileName.lastIndexOf('.');
		if (lastDot != -1) {
			return fileName.substring(lastDot);
		}

		return "";
	}

	/**
	 * 提取扩展名（不包括点）
	 * 例如：demo.php -> php
	 */
	public static String getExtension(String filePath) {
		String extension = getExtensionWithDot(filePath);
		if (extension.length() > 1 && extension.charAt(0) == '.') {
			return extension.substring(1);
		}
		return extension;
	}

	/**
	 * 创建文件（如果目录不存在，则自动创建）
	 *
	 * @param filePath 文件路径
	 * @return 创建的文件对应的Path对象
	 * @throws IOException 如果文件已存在或创建失败
	 */
	public static Path create(String filePath) throws IOException {
		if (StringUtils.isBlank(filePath)) {
			throw new IllegalArgumentException("文件路径不能为空");
		}
		Path path = Paths.get(filePath);
		Path parent = path.getParent();
		if (parent != null && !Files.exists(parent)) {
			Files.createDirectories(parent);
		}
		return Files.createFile(path);
	}

	/**
	 * 删除文件
	 *
	 * @param filePath 文件路径
	 * @return 删除成功返回true，文件不存在返回false
	 * @throws IOException 如果删除过程中发生错误（如无权限）
	 */
	public static boolean delete(String filePath) throws IOException {
		if (filePath == null || filePath.trim().isEmpty()) {
			throw new IllegalArgumentException("文件路径不能为空");
		}
		Path path = Paths.get(filePath);
		if (!Files.exists(path)) {
			return false;
		}
		if (!Files.isDirectory(path)) {
			return Files.deleteIfExists(path);
		}
		// 递归删除目录内容
		Files.walk(path)
				.sorted((p1, p2) -> -p1.compareTo(p2)) // 先删除子项，再删除父目录
				.forEach(p -> {
					try {
						Files.delete(p);
					} catch (IOException e) {
						log.warn("删除失败: {}", p, e);
					}
				});
		return true;
	}

	/**
	 * 清空文件内容（保留文件本身）
	 *
	 * @param filePath 文件路径
	 * @throws IOException 如果文件不存在或清空过程中发生错误
	 */
	public static void clear(String filePath) throws IOException {
		if (filePath == null || filePath.trim().isEmpty()) {
			throw new IllegalArgumentException("文件路径不能为空");
		}
		Path path = Paths.get(filePath);
		if (!Files.exists(path)) {
			return;
		}
		if (Files.isDirectory(path)) {
			return;
		}
		Files.write(path, new byte[0], StandardOpenOption.TRUNCATE_EXISTING);
	}

	/**
	 * 向文件追加文本内容
	 *
	 * @param content  要追加的文本内容
	 * @param filePath 文件路径
	 * @return 写入的文件对应的Path对象
	 * @throws IOException 如果发生I/O错误
	 */
	public static Path append(String content, String filePath) throws IOException {
		if (StringUtils.isBlank(content) || StringUtils.isBlank(filePath)) {
			throw new IllegalArgumentException("内容和文件路径不能为空");
		}
		Path path = Paths.get(filePath);
		// 创建文件夹
		Path parentDir = path.getParent();
		if (parentDir != null && !Files.exists(parentDir)) {
			Files.createDirectories(parentDir);
		}
		// 追加文件
		Files.writeString(path, content, StandardCharsets.UTF_8,
				StandardOpenOption.CREATE,
				StandardOpenOption.APPEND);
		return path;
	}

	/**
	 * 将内容覆盖写入文件（如果文件不存在则创建，存在则覆盖原内容）
	 *
	 * @param filePath 文件路径
	 * @param content  要写入的文本内容
	 * @return 写入的文件对应的Path对象
	 * @throws IOException 如果发生I/O错误
	 */
	public static Path write(String filePath, String content) throws IOException {
		if (StringUtils.isBlank(content) || StringUtils.isBlank(filePath)) {
			throw new IllegalArgumentException("内容和文件路径不能为空");
		}
		Path path = Paths.get(filePath);
		// 创建文件夹
		Path parentDir = path.getParent();
		if (parentDir != null && !Files.exists(parentDir)) {
			Files.createDirectories(parentDir);
		}
		// 创建、覆盖写入文件
		Files.writeString(path, content, StandardCharsets.UTF_8,
				StandardOpenOption.CREATE,
				StandardOpenOption.TRUNCATE_EXISTING,
				StandardOpenOption.WRITE);
		return path;
	}

	/**
	 * 按行读取文件，返回文本行列表
	 *
	 * @param filePath 文件路径
	 * @return 包含文件所有行的List，如果文件不存在或读取失败则返回空列表
	 */
	public static List<String> readLines(String filePath) throws IOException {
		if (filePath == null || filePath.trim().isEmpty()) {
			throw new IllegalArgumentException("文件路径不能为空");
		}
		Path path = Paths.get(filePath);
		if (!Files.exists(path) || !Files.isRegularFile(path)) {
			return new ArrayList<>();
		}
		return Files.readAllLines(path, StandardCharsets.UTF_8);
	}

	/**
	 * 按行读取文件 (使用UTF-8字符集)
	 *
	 * @param filePath 文件路径
	 * @return 包含文件所有行的内容
	 */
	public static String read(String filePath) throws IOException {
		List<String> lines = readLines(filePath);
		StringBuilder sb = new StringBuilder();
		for (String line : lines) {
			sb.append(line).append("\n");
		}
		return sb.toString();
	}

	/**
	 * 检查文件是否存在
	 *
	 * @param filePath 文件路径
	 * @return 文件存在返回true，否则返回false
	 */
	public static boolean exists(String filePath) {
		if (filePath == null || filePath.trim().isEmpty()) {
			return false;
		}
		return Files.exists(Paths.get(filePath));
	}

	/**
	 * 检查是否是文件（不是目录）
	 *
	 * @param filePath 文件路径
	 * @return 如果是文件返回true，否则返回false
	 */
	public static boolean isFile(String filePath) {
		if (filePath == null || filePath.trim().isEmpty()) {
			return false;
		}
		Path path = Paths.get(filePath);
		return Files.exists(path) && Files.isRegularFile(path);
	}

	/**
	 * 检查是否是目录
	 *
	 * @param dirPath 目录路径
	 * @return 如果是目录返回true，否则返回false
	 */
	public static boolean isDirectory(String dirPath) {
		if (dirPath == null || dirPath.trim().isEmpty()) {
			return false;
		}
		Path path = Paths.get(dirPath);
		return Files.exists(path) && Files.isDirectory(path);
	}

	/**
	 * 获取文件大小（字节）
	 *
	 * @param filePath 文件路径
	 * @return 文件大小（字节），文件不存在返回-1
	 */
	public static long getFileSize(String filePath) {
		if (filePath == null || filePath.trim().isEmpty()) {
			return -1;
		}
		try {
			Path path = Paths.get(filePath);
			if (!Files.exists(path) || !Files.isRegularFile(path)) {
				return -1;
			}
			return Files.size(path);
		} catch (IOException e) {
			return -1;
		}
	}

	// ==================== 备份方法 ====================

	/**
	 * 备份单个文件。在写入变更前调用，备份原始内容。
	 * 备份文件命名格式：{原始文件路径}.back_yyyyMMddHHmmss
	 * 若原始文件不存在，则跳过备份。
	 * 若原始文件内容与最近一次备份内容完全一致，则跳过本次备份。
	 * 备份完成后，清理同目录下的旧备份，仅保留最近3次。
	 *
	 * @param filePath 原始文件的绝对路径
	 */
	public static void backupFile(String filePath) {
		if (filePath == null || filePath.trim().isEmpty()) {
			log.warn("备份文件失败: 文件路径为空");
			return;
		}
		Path sourcePath = Paths.get(filePath);
		if (!Files.exists(sourcePath) || !Files.isRegularFile(sourcePath)) {
			log.warn("备份文件失败: 文件不存在或不是普通文件, path={}", filePath);
			return;
		}

		try {
			byte[] currentContent = Files.readAllBytes(sourcePath);
			Path parentDir = sourcePath.getParent();
			String sourceName = sourcePath.getFileName().toString();
			String backupPrefix = sourceName + ".back_";

			List<Path> existingBackups;
			if (parentDir != null) {
				try (var stream = Files.list(parentDir)) {
					existingBackups = stream
							.filter(p -> p.getFileName().toString().startsWith(backupPrefix))
							.filter(p -> !Files.isDirectory(p))
							.sorted(Comparator.reverseOrder())
							.collect(Collectors.toList());
				}
			} else {
				existingBackups = List.of();
			}

			if (!existingBackups.isEmpty()) {
				byte[] latestBackupContent = Files.readAllBytes(existingBackups.get(0));
				if (java.util.Arrays.equals(currentContent, latestBackupContent)) {
					log.info("备份文件跳过: 内容与最近备份一致, path={}", filePath);
					return;
				}
			}

			String timestamp = LocalDateTime.now().format(BACKUP_TIMESTAMP_FORMAT);
			String backupFileName = sourceName + ".back_" + timestamp;
			Path backupPath = (parentDir != null) ? parentDir.resolve(backupFileName) : Paths.get(backupFileName);
			Files.copy(sourcePath, backupPath, StandardCopyOption.REPLACE_EXISTING);
			log.info("备份文件完成: {} -> {}", filePath, backupPath.toAbsolutePath());

			cleanOldBackups(sourcePath, backupPrefix, 3);

		} catch (IOException e) {
			log.error("备份文件异常: path={}", filePath, e);
		}
	}

	/**
	 * 备份整个目录为 zip 压缩包。在安装 skill 等覆盖操作前调用。
	 * 备份文件命名格式：{目录路径}.back_yyyyMMddHHmmss.zip
	 * 若原始目录不存在，则跳过备份。
	 * 备份完成后，清理同目录下的旧备份，仅保留最近3次。
	 *
	 * @param dirPath 原始目录的绝对路径
	 */
	public static void backupDirectory(String dirPath) {
		if (dirPath == null || dirPath.trim().isEmpty()) {
			log.warn("备份目录失败: 路径为空");
			return;
		}
		Path sourceDir = Paths.get(dirPath);
		if (!Files.exists(sourceDir) || !Files.isDirectory(sourceDir)) {
			log.warn("备份目录失败: 目录不存在或不是目录, path={}", dirPath);
			return;
		}

		try {
			String timestamp = LocalDateTime.now().format(BACKUP_TIMESTAMP_FORMAT);
			String backupFileName = sourceDir.getFileName().toString() + ".back_" + timestamp + ".zip";
			Path parentDir = sourceDir.getParent();
			Path backupPath = (parentDir != null) ? parentDir.resolve(backupFileName) : Paths.get(backupFileName);

			try (OutputStream fos = Files.newOutputStream(backupPath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
				 ZipOutputStream zos = new ZipOutputStream(fos)) {

				Files.walkFileTree(sourceDir, new SimpleFileVisitor<>() {
					@Override
					public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
						String entryName = sourceDir.relativize(file).toString();
						entryName = entryName.replace('\\', '/');
						zos.putNextEntry(new ZipEntry(entryName));
						try (InputStream in = Files.newInputStream(file)) {
							in.transferTo(zos);
						}
						zos.closeEntry();
						return FileVisitResult.CONTINUE;
					}

					@Override
					public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
						if (!dir.equals(sourceDir)) {
							String entryName = sourceDir.relativize(dir).toString();
							entryName = entryName.replace('\\', '/') + "/";
							zos.putNextEntry(new ZipEntry(entryName));
							zos.closeEntry();
						}
						return FileVisitResult.CONTINUE;
					}
				});
			}

			log.info("备份目录完成: {} -> {}", dirPath, backupPath.toAbsolutePath());

			String backupPrefix = sourceDir.getFileName().toString() + ".back_";
			cleanOldBackups(sourceDir, backupPrefix, 3);

		} catch (IOException e) {
			log.error("备份目录异常: path={}", dirPath, e);
		}
	}

	/**
	 * 清理旧备份文件，仅保留指定数量的最近备份。
	 * 根据文件名前缀匹配备份文件，按时间戳后缀降序排列，删除超出保留数量的旧文件。
	 *
	 * @param sourcePath   原始文件或目录的 Path，用于确定父目录
	 * @param backupPrefix 备份文件名前缀（含 .back_）
	 * @param keepCount    保留的最近备份数量
	 */
	private static void cleanOldBackups(Path sourcePath, String backupPrefix, int keepCount) {
		Path parentDir = sourcePath.getParent();
		if (parentDir == null) {
			return;
		}

		try (var stream = Files.list(parentDir)) {
			List<Path> backups = stream
					.filter(p -> p.getFileName().toString().startsWith(backupPrefix))
					.filter(p -> !Files.isDirectory(p))
					.sorted(Comparator.reverseOrder())
					.collect(Collectors.toList());

			if (backups.size() <= keepCount) {
				return;
			}

			List<Path> toDelete = backups.subList(keepCount, backups.size());
			for (Path oldBackup : toDelete) {
				try {
					Files.delete(oldBackup);
					log.info("清理旧备份: {}", oldBackup.toAbsolutePath());
				} catch (IOException e) {
					log.warn("清理旧备份失败: {}", oldBackup.toAbsolutePath(), e);
				}
			}
		} catch (IOException e) {
			log.warn("清理旧备份异常: path={}", sourcePath.toAbsolutePath(), e);
		}
	}

}
