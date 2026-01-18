package com.yzz.hyperaiagent.repository;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Properties;

@Slf4j
@Component
@RequiredArgsConstructor
public class LocalPdfFileRepository implements FileRepository {

    private final VectorStore vectorStore;

    // 会话id 与 存储文件名的对应关系
    private final Properties chatFiles = new Properties();

    // 会话id 与 原始文件名的对应关系
    private final Properties originalFileNames = new Properties();

    // PDF文件存储目录
    private static final String PDF_DIR = System.getProperty("user.dir") + "/pdf-files";

    // 向量存储和映射文件存储路径
    private static final String STORAGE_DIR = System.getProperty("user.dir") + "/storage";

    @Override
    public boolean save(String chatId, Resource resource) {
        try {
            // 1.确保目录存在
            File pdfDir = new File(PDF_DIR);
            if (!pdfDir.exists()) {
                pdfDir.mkdirs();
            }

            // 2.获取原始文件名并构建新文件名（使用chatId前缀避免冲突）
            String originalFilename = resource.getFilename();
            if (originalFilename == null || originalFilename.trim().isEmpty()) {
                log.error("Filename is null or empty for chatId: {}", chatId);
                return false;
            }

            String filename = chatId + "_" + originalFilename;
            File target = new File(pdfDir, filename);

            // 3.保存文件
            if (!target.exists()) {
                Files.copy(resource.getInputStream(), target.toPath());
                log.info("PDF file saved successfully: {}", target.getAbsolutePath());
            } else {
                log.warn("PDF file already exists, will be overwritten: {}", target.getAbsolutePath());
            }

            // 4.保存映射关系
            chatFiles.put(chatId, filename);  // chatId -> 存储文件名
            originalFileNames.put(chatId, originalFilename);  // chatId -> 原始文件名
            return true;
        } catch (Exception e) {
            log.error("Failed to save PDF resource for chatId: {}", chatId, e);
            return false;
        }
    }

    @Override
    public Resource getFile(String chatId) {
        String filename = chatFiles.getProperty(chatId);
        if (filename == null) {
            log.warn("No file found for chatId: {}", chatId);
            return new FileSystemResource(""); // 返回空的Resource
        }
        File file = new File(PDF_DIR, filename);
        return new FileSystemResource(file);
    }

    /**
     * 获取原始文件名（用于向量过滤）
     * @param chatId 会话ID
     * @return 原始文件名
     */
    public String getOriginalFilename(String chatId) {
        return originalFileNames.getProperty(chatId);
    }

    @PostConstruct
    private void init() {
        try {
            // 确保存储目录存在
            File storageDir = new File(STORAGE_DIR);
            if (!storageDir.exists()) {
                storageDir.mkdirs();
                log.info("Created storage directory: {}", storageDir.getAbsolutePath());
            }

            // 加载chatId到存储文件名的映射关系
            File mappingFile = new File(STORAGE_DIR, "chat-pdf.properties");
            if (mappingFile.exists()) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                        new FileSystemResource(mappingFile).getInputStream(), StandardCharsets.UTF_8))) {
                    chatFiles.load(reader);
                    log.info("Loaded chat file mappings from: {}", mappingFile.getAbsolutePath());
                }
            } else {
                log.info("No existing chat file mappings found, starting fresh");
            }

            // 加载chatId到原始文件名的映射关系
            File originalMappingFile = new File(STORAGE_DIR, "chat-pdf-original.properties");
            if (originalMappingFile.exists()) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                        new FileSystemResource(originalMappingFile).getInputStream(), StandardCharsets.UTF_8))) {
                    originalFileNames.load(reader);
                    log.info("Loaded original filename mappings from: {}", originalMappingFile.getAbsolutePath());
                }
            } else {
                log.info("No existing original filename mappings found, starting fresh");
            }

            // 加载向量存储
            File vectorFile = new File(STORAGE_DIR, "chat-pdf.json");
            if (vectorFile.exists()) {
                SimpleVectorStore simpleVectorStore = (SimpleVectorStore) vectorStore;
                simpleVectorStore.load(new FileSystemResource(vectorFile));
                log.info("Loaded vector store from: {}", vectorFile.getAbsolutePath());
            } else {
                log.info("No existing vector store found, starting fresh");
            }
        } catch (Exception e) {
            log.error("Error during initialization", e);
            // 不抛出异常，允许应用启动
        }
    }

    /**
     * 手动触发持久化（用于在每次上传文件后立即保存）
     */
    public void persist() {
        try {
            // 确保存储目录存在
            File storageDir = new File(STORAGE_DIR);
            if (!storageDir.exists()) {
                storageDir.mkdirs();
            }

            // 保存chatId到存储文件名的映射关系
            File mappingFile = new File(STORAGE_DIR, "chat-pdf.properties");
            try (FileWriter writer = new FileWriter(mappingFile)) {
                chatFiles.store(writer, "PDF Chat Mappings - " + LocalDateTime.now());
                log.info("Saved chat file mappings to: {}", mappingFile.getAbsolutePath());
            }

            // 保存chatId到原始文件名的映射关系
            File originalMappingFile = new File(STORAGE_DIR, "chat-pdf-original.properties");
            try (FileWriter writer = new FileWriter(originalMappingFile)) {
                originalFileNames.store(writer, "Original Filename Mappings - " + LocalDateTime.now());
                log.info("Saved original filename mappings to: {}", originalMappingFile.getAbsolutePath());
            }

            // 保存向量存储
            File vectorFile = new File(STORAGE_DIR, "chat-pdf.json");
            SimpleVectorStore simpleVectorStore = (SimpleVectorStore) vectorStore;
            simpleVectorStore.save(vectorFile);
            log.info("Saved vector store to: {}", vectorFile.getAbsolutePath());
        } catch (Exception e) {
            log.error("Error during persistence", e);
            throw new RuntimeException("Failed to persist data", e);
        }
    }

    @PreDestroy
    private void shutdownHook() {
        // 应用关闭时再次保存，确保数据不丢失
        persist();
        log.info("Persisted data on application shutdown");
    }
}