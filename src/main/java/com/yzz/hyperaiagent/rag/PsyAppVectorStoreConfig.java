package com.yzz.hyperaiagent.rag;

import jakarta.annotation.Resource;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * 心理顾问大师向量数据库配置（初始化基于内存的向量数据库 Bean）
 */
@Configuration
public class PsyAppVectorStoreConfig {

    @Resource
    private PsyAppDocumentLoader psyAppDocumentLoader;

    @Resource
    private MyKeywordEnricher myKeywordEnricher;

    // dashscopeEmbeddingModel
    @Bean
    VectorStore psyAppVectorStore(EmbeddingModel dashscopeEmbeddingModel) {
        SimpleVectorStore simpleVectorStore = SimpleVectorStore.builder(dashscopeEmbeddingModel).build();
        // 加载文档
        List<Document> documentList = psyAppDocumentLoader.loadMarkdowns();
        // 自主切分文档 容易破坏语义完整性 推荐使用云知识库的智能切分 或者仍二次校验
//        List<Document> splitDocuments = myTokenTextSplitter.splitCustomized(documentList);
        // 自动补充关键词元信息
        List<Document> enrichedDocuments = myKeywordEnricher.enrichDocuments(documentList);
        simpleVectorStore.add(enrichedDocuments);
        return simpleVectorStore;
    }
}
