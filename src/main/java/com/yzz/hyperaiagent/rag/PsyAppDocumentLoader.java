package com.yzz.hyperaiagent.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.ExtractedTextFormatter;
import org.springframework.ai.reader.markdown.MarkdownDocumentReader;
import org.springframework.ai.reader.markdown.config.MarkdownDocumentReaderConfig;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Component
@Slf4j
public class PsyAppDocumentLoader {
    private final ResourcePatternResolver resourcePatternResolver;

    public PsyAppDocumentLoader(ResourcePatternResolver resourcePatternResolver) {
        this.resourcePatternResolver = resourcePatternResolver;
    }

    public List<Document> loadMarkdowns() {
        List<Document> allDocuments = new ArrayList<>();
        // 加载多篇MD文件
        try {
            Resource[] resources = resourcePatternResolver.getResources("classpath:document/*.md");
            for (Resource resource : resources) {
                String filename = resource.getFilename();
                // 提取文档倒数第 8 和第 4 个字作为标签
                String status = filename.substring(filename.length() - 8, filename.length() - 4);
                MarkdownDocumentReaderConfig config = MarkdownDocumentReaderConfig.builder()
                        .withHorizontalRuleCreateDocument(true)
                        .withIncludeCodeBlock(false)
                        .withIncludeBlockquote(false)
                        .withAdditionalMetadata("filename", filename)
                        .withAdditionalMetadata("status", status)
                        .build();
                MarkdownDocumentReader markdownDocumentReader = new MarkdownDocumentReader(resource, config);
                allDocuments.addAll(markdownDocumentReader.get());
            }
        } catch (IOException e) {
            log.error("Markdown 文档加载失败", e);
        }
        return allDocuments;
    }

    public List<Document> loadPdfs() {
        List<Document> allDocuments = new ArrayList<>();
        // 加载多篇PDF文件
        try {
            Resource[] resources = resourcePatternResolver.getResources("classpath:document2/*.pdf");
            for (Resource resource : resources) {
                PdfDocumentReaderConfig config = PdfDocumentReaderConfig.builder()
                        .withPageTopMargin(0)
                        .withPageExtractedTextFormatter(ExtractedTextFormatter.builder()
                                .withNumberOfTopTextLinesToDelete(0)
                                .withNumberOfBottomTextLinesToDelete(0)
                                .build())
                        .withPagesPerDocument(1)
                        .build();
                PagePdfDocumentReader pdfDocumentReader = new PagePdfDocumentReader(resource, config);
                allDocuments.addAll(pdfDocumentReader.get());
            }
        } catch (IOException e) {
            log.error("PDF 文档加载失败", e);
        }
        return allDocuments;
    }
}
