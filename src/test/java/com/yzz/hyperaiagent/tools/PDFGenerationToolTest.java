package com.yzz.hyperaiagent.tools;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PDFGenerationToolTest {

    @Test
    void generatePDF() {
        PDFGenerationTool tool = new PDFGenerationTool();
        String fileName = "123.pdf";
        String content = "123 https://www.baidu.com";
        String result = tool.generatePDF(fileName, content);
        assertNotNull(result);
    }
}