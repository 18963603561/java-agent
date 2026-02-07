package com.example.agent.runtime.raw.store;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 本地文件原始结果读写支持组件。
 */
@Component
public class FileRawStorageSupport {

    private static final Logger log = LoggerFactory.getLogger(FileRawStorageSupport.class);

    private static final DateTimeFormatter FILE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
            .withZone(ZoneOffset.UTC);

    /**
     * 将文本保存到本地文件。
     *
     * @param baseDir 基础目录
     * @param source 来源
     * @param text 文本内容
     * @return 文件名
     */
    public String writeText(String baseDir, String source, String text) {
        String filename = buildFileName(source);
        Path dir = resolveBaseDir(baseDir);
        Path file = dir.resolve(filename);
        try {
            Files.createDirectories(dir);
            Files.writeString(file,
                    text == null ? "" : text,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE);
            log.info("原始结果写入本地文件完成, file={}, bytes={}", file, text == null ? 0 : text.getBytes(StandardCharsets.UTF_8).length);
            return filename;
        } catch (IOException ex) {
            log.error("原始结果写入本地文件失败, baseDir={}, filename={}", baseDir, filename, ex);
            throw new IllegalStateException("写入本地文件失败", ex);
        }
    }

    /**
     * 读取本地文本文件内容。
     *
     * @param baseDir 基础目录
     * @param filename 文件名
     * @return 文本内容
     */
    public String readText(String baseDir, String filename) {
        Path dir = resolveBaseDir(baseDir);
        String safeFileName = sanitizeFilename(filename);
        Path file = dir.resolve(safeFileName).normalize();
        if (!file.startsWith(dir)) {
            throw new IllegalArgumentException("非法文件路径");
        }
        try {
            if (!Files.exists(file) || !Files.isRegularFile(file)) {
                return null;
            }
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            log.error("读取本地原始结果文件失败, file={}", file, ex);
            throw new IllegalStateException("读取本地文件失败", ex);
        }
    }

    /**
     * 构建对外访问 URL。
     *
     * @param publicBaseUrl 公共 URL 前缀
     * @param filename 文件名
     * @return 完整访问 URL
     */
    public String buildAccessUrl(String publicBaseUrl, String filename) {
        String safe = sanitizeFilename(filename);
        String base = StringUtils.hasText(publicBaseUrl) ? publicBaseUrl.trim() : "";
        if (base.endsWith("/")) {
            return base + safe;
        }
        return base + "/" + safe;
    }

    private String buildFileName(String source) {
        String time = FILE_TIME_FORMATTER.format(Instant.now());
        String safeSource = sanitizeSource(source);
        return time + "-" + safeSource + "-" + UUID.randomUUID() + ".txt";
    }

    private String sanitizeSource(String source) {
        if (!StringUtils.hasText(source)) {
            return "unknown";
        }
        return source.trim().toLowerCase().replaceAll("[^a-z0-9_-]", "_");
    }

    private String sanitizeFilename(String filename) {
        if (!StringUtils.hasText(filename)) {
            throw new IllegalArgumentException("文件名不能为空");
        }
        String normalized = Paths.get(filename).getFileName().toString();
        if (!normalized.endsWith(".txt")) {
            throw new IllegalArgumentException("仅允许 .txt 文件");
        }
        return normalized;
    }

    private Path resolveBaseDir(String baseDir) {
        String safe = StringUtils.hasText(baseDir) ? baseDir.trim() : "./data/raw";
        return Paths.get(safe).toAbsolutePath().normalize();
    }
}

