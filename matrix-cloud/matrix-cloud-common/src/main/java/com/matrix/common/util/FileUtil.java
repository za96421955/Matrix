package com.matrix.common.util;

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

/**
 * 文件工具类 (基于 JDK 17+ NIO.2)
 *
 * @author 陈晨
 */
@Slf4j
public final class FileUtil {

	private FileUtil() {}

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
		// 如果文件不存在，Files.write会抛出NoSuchFileException。
		// 此方法设计为仅追加，调用前请确保文件已存在，或使用appendAndCreateFile。
		Files.writeString(path, content, StandardCharsets.UTF_8, StandardOpenOption.APPEND);
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
		// 使用 CREATE 保证文件不存在时创建，TRUNCATE_EXISTING 覆盖原有内容
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

}


