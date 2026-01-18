package com.yzz.hyperaiagent.controller;

import com.yzz.hyperaiagent.entity.vo.Result;
import com.yzz.hyperaiagent.repository.ChatHistoryRepository;
import com.yzz.hyperaiagent.repository.FileRepository;
import com.yzz.hyperaiagent.repository.LocalPdfFileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.ExtractedTextFormatter;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor.CHAT_MEMORY_CONVERSATION_ID_KEY;

@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/ai/pdf")
public class PdfController {

    private final FileRepository fileRepository;

    private final LocalPdfFileRepository localPdfFileRepository;

    private final VectorStore vectorStore;

    private final ChatClient pdfChatClient;

    private final ChatHistoryRepository chatHistoryRepository;

    @RequestMapping(value = "/chat", produces = "text/html;charset=utf-8")
    public Flux<String> chat(String prompt, String chatId) {
        try {
            // 1.找到会话文件
            Resource file = fileRepository.getFile(chatId);
            if (!file.exists()) {
                // 文件不存在，不回答
                throw new RuntimeException("会话文件不存在！");
            }

            // 2.获取原始文件名（从Repository直接获取，避免文件名解析问题）
            String originalFilename = localPdfFileRepository.getOriginalFilename(chatId);
            if (originalFilename == null) {
                log.warn("Original filename not found for chatId: {}, falling back to stored filename", chatId);
                // 兜底逻辑：从存储文件名中提取
                String storedFilename = file.getFilename();
                if (storedFilename != null && storedFilename.contains("_")) {
                    originalFilename = storedFilename.substring(storedFilename.indexOf("_") + 1);
                } else {
                    originalFilename = storedFilename;
                }
            }

            log.info("Chatting with PDF document for chatId: {}, original filename: {}", chatId, originalFilename);

            // 3.保存会话id
            chatHistoryRepository.save("pdf", chatId);

            // 4.从向量库检索相关文档
            SearchRequest searchRequest = SearchRequest.builder()
                    .query(prompt)
                    .topK(3)  // 获取前3个最相关的文档片段
                    .similarityThreshold(0.5f)  // 相似度阈值
                    .filterExpression("file_name == '" + originalFilename + "'")
                    .build();

            List<Document> relevantDocs = vectorStore.similaritySearch(searchRequest);
            log.info("Found {} relevant documents for query", relevantDocs.size());

            // 5.构建包含文档内容的提示词
            String enhancedPrompt;
            if (!relevantDocs.isEmpty()) {
                StringBuilder context = new StringBuilder();
                context.append("以下是从PDF文档中检索到的相关内容：\n\n");

                for (int i = 0; i < relevantDocs.size(); i++) {
                    Document doc = relevantDocs.get(i);
                    context.append(String.format("[文档片段 %d]:\n%s\n\n",
                            i + 1, doc.getText()));
                }

                context.append(String.format("用户问题：%s\n\n", prompt));
                context.append("请基于上述文档内容回答用户问题。如果文档中没有相关信息，请明确告知用户。");

                enhancedPrompt = context.toString();
                log.info("Enhanced prompt with {} document fragments", relevantDocs.size());
            } else {
                // 没有找到相关文档，直接使用用户的问题
                enhancedPrompt = prompt;
                log.warn("No relevant documents found, using original prompt");
            }

            // 6.请求模型（使用增强后的提示词）
            return pdfChatClient.prompt()
                    .user(enhancedPrompt)
                    .advisors(a -> a.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId))
                    .stream()
                    .content();
        } catch (Exception e) {
            log.error("Error during chat for chatId: {}", chatId, e);
            throw new RuntimeException("聊天失败：" + e.getMessage(), e);
        }
    }

    /**
     * 文件上传
     */
    @RequestMapping("/upload/{chatId}")
    public Result uploadPdf(@PathVariable String chatId, @RequestParam("file") MultipartFile file) {
        try {
            // 1. 校验参数
            if (chatId == null || chatId.trim().isEmpty()) {
                return Result.fail("会话ID不能为空！");
            }

            // 2. 校验文件是否为空
            if (file == null || file.isEmpty()) {
                return Result.fail("文件内容为空！");
            }

            // 3. 校验文件名
            String originalFilename = file.getOriginalFilename();
            if (originalFilename == null || originalFilename.trim().isEmpty()) {
                return Result.fail("文件名不能为空！");
            }

            // 4. 校验文件是否为PDF格式（通过扩展名和Content-Type双重校验）
            boolean isPdfByExtension = originalFilename.toLowerCase().endsWith(".pdf");
            boolean isPdfByContentType = Objects.equals(file.getContentType(), "application/pdf");

            if (!isPdfByExtension && !isPdfByContentType) {
                return Result.fail("只能上传PDF文件！");
            }

            // 5. 校验文件大小（限制为50MB）
            long maxSize = 50 * 1024 * 1024; // 50MB
            if (file.getSize() > maxSize) {
                return Result.fail("文件大小不能超过50MB！");
            }

            // 6. 保存文件
            boolean success = fileRepository.save(chatId, file.getResource());
            if (!success) {
                return Result.fail("保存文件失败！");
            }

            // 7. 写入向量库（传入原始文件名用于元数据）
            this.writeToVectorStore(file.getResource(), originalFilename);

            // 8. 立即持久化向量库和映射关系（防止应用崩溃导致数据丢失）
            localPdfFileRepository.persist();

            log.info("PDF uploaded and persisted successfully for chatId: {}, filename: {}, size: {} bytes",
                    chatId, originalFilename, file.getSize());
            return Result.ok();
        } catch (Exception e) {
            log.error("Failed to upload PDF for chatId: {}", chatId, e);
            return Result.fail("上传文件失败：" + e.getMessage());
        }
    }

    /**
     * 文件下载
     */
    @GetMapping("/file/{chatId}")
    public ResponseEntity<Resource> download(@PathVariable("chatId") String chatId) {
        try {
            // 1.读取文件
            Resource resource = fileRepository.getFile(chatId);

            // 2.检查文件是否存在
            if (resource == null || !resource.exists() || resource.getFilename() == null || resource.getFilename().isEmpty()) {
                log.warn("File not found for chatId: {}", chatId);
                return ResponseEntity.notFound().build();
            }

            // 3.文件名编码，写入响应头（使用原始文件名）
            String storedFilename = resource.getFilename();
            String originalFilename = storedFilename;
            if (storedFilename.contains("_")) {
                // 去掉chatId前缀，返回原始文件名
                originalFilename = storedFilename.substring(storedFilename.indexOf("_") + 1);
            }

            String encodedFilename = URLEncoder.encode(originalFilename, StandardCharsets.UTF_8);

            log.info("Downloading file for chatId: {}, original filename: {}", chatId, originalFilename);

            // 4.返回文件
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)  // 使用PDF媒体类型
                    .header("Content-Disposition", "attachment; filename=\"" + encodedFilename + "\"")
                    .body(resource);
        } catch (Exception e) {
            log.error("Error downloading file for chatId: {}", chatId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    private void writeToVectorStore(Resource resource, String filename) {
        try {
            // 1.创建PDF的读取器
            PagePdfDocumentReader reader = new PagePdfDocumentReader(
                    resource, // 文件源
                    PdfDocumentReaderConfig.builder()
                            .withPageExtractedTextFormatter(ExtractedTextFormatter.defaults())
                            .withPagesPerDocument(1) // 每1页PDF作为一个Document
                            .build()
            );

            // 2.读取PDF文档，拆分为Document
            List<Document> documents = reader.read();
            log.info("Read {} documents from PDF file", documents.size());

            // 3.为每个Document添加file_name元数据
            List<Document> documentsWithMetadata = documents.stream()
                    .map(doc -> {
                        Map<String, Object> metadata = new HashMap<>(doc.getMetadata());
                        metadata.put("file_name", filename);
                        // 创建新的Document对象（因为Document是不可变的）
                        Document newDoc = new Document(doc.getText(), metadata);
                        log.debug("Document metadata: file_name={}, metadata={}", filename, metadata);
                        return newDoc;
                    })
                    .toList();

            // 4.写入向量库
            vectorStore.add(documentsWithMetadata);

            log.info("Successfully wrote {} documents from file '{}' to vector store with file_name metadata",
                    documentsWithMetadata.size(), filename);
        } catch (Exception e) {
            log.error("Failed to write PDF content to vector store for file: {}", filename, e);
            throw new RuntimeException("写入向量库失败：" + e.getMessage(), e);
        }
    }
}