package com.yzz.hyperaiagent.rag;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;

@SpringBootTest
class PgVectorVectorStoreConfigTest {

    @Resource
    private VectorStore pgVectorVectorStore;

    @Test
    void pgVectorVectorStore() {
        List<Document> documents = List.of(
                new Document(
                        "LeetCode 是一个用于练习算法和数据结构的在线平台，常用于面试准备和编程能力提升。",
                        Map.of(
                                "source", "qa-demo",
                                "topic", "programming",
                                "lang", "zh",
                                "id", "doc-1"
                        )
                ),
                new Document(
                        "Codeforces 是一个全球知名的算法竞赛平台，网址为 codeforces.com，常用于编程竞赛和算法训练。",
                        Map.of(
                                "source", "qa-demo",
                                "topic", "competitive-programming",
                                "lang", "zh",
                                "id", "doc-2"
                        )
                ),
                new Document(
                        "金融危机通常指金融体系出现严重失衡，引发资产价格暴跌、流动性枯竭和经济衰退的系统性事件。",
                        Map.of(
                                "source", "qa-demo",
                                "topic", "economics",
                                "lang", "zh",
                                "id", "doc-3"
                        )
                )
        );
        // 添加文档
        // 添加之前需要清空数据库  TRUNCATE TABLE vector_store2;
        // 1024维 切片最大长度和
        //
        pgVectorVectorStore.add(documents);
        // 相似度查询
        List<Document> results = pgVectorVectorStore.similaritySearch(SearchRequest.builder().query("怎么学编程啊").topK(3).build());
        Assertions.assertNotNull(results);
    }
}