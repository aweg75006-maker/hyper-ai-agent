package com.yzz.hyperaiagent.tools;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class WebSearchToolTest {

    // 需要加载配置文件 application-local.yml 必须打上 @SpringBootTest
    @Value("${search-api.api-key}")
    private String searchApiKey;

    @Test
    void searchWeb() {
        WebSearchTool webSearchTool = new WebSearchTool(searchApiKey);
        String query = "ChatGPT's official website";
        String result = webSearchTool.searchWeb(query);
        Assertions.assertNotNull(result);
    }
}