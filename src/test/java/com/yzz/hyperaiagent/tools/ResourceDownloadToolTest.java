package com.yzz.hyperaiagent.tools;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

//@SpringBootTest
class ResourceDownloadToolTest {

    @Test
    public void testDownloadResource() {
        ResourceDownloadTool tool = new ResourceDownloadTool();
        String url = "https://www.codefather.cn/logo.png";
        String fileName = "logo.png";
        String result = tool.downloadResource(url, fileName);
        Assertions.assertNotNull(result);
    }
}