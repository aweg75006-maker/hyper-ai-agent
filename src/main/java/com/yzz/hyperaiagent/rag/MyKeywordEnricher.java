package com.yzz.hyperaiagent.rag;

import com.yzz.hyperaiagent.gateway.application.GatewayChatModelFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.model.transformer.KeywordMetadataEnricher;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 基于 AI 的文档元信息增强器（为文档补充元信息）
 */
@Component
public class MyKeywordEnricher {

    private final GatewayChatModelFactory gatewayChatModelFactory;

    public MyKeywordEnricher(GatewayChatModelFactory gatewayChatModelFactory) {
        this.gatewayChatModelFactory = gatewayChatModelFactory;
    }

    public List<Document> enrichDocuments(List<Document> documents) {
        KeywordMetadataEnricher keywordMetadataEnricher = new KeywordMetadataEnricher(
                gatewayChatModelFactory.create("pdf-rag"), 5
        );
        return  keywordMetadataEnricher.apply(documents);
    }
}
