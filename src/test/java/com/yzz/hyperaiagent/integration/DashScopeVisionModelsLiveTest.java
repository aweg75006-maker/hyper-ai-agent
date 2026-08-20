package com.yzz.hyperaiagent.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Queries the OpenAI-compatible model catalogue for the current DashScope API key.
 *
 * <p>This is deliberately opt-in: it makes a real network request but does not invoke
 * inference. Run it with:
 * {@code ./mvnw -Ddashscope.live=true -Dtest=DashScopeVisionModelsLiveTest test}</p>
 */
@Tag("integration")
@EnabledIfSystemProperty(named = "dashscope.live", matches = "true")
class DashScopeVisionModelsLiveTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void listsVisionLanguageModelsAvailableToThisApiKey() throws Exception {
        Properties env = loadDotEnv();
        String apiKey = require(env, "DASHSCOPE_API_KEY");
        String baseUrl = env.getProperty(
                "DASHSCOPE_OPENAI_BASE_URL",
                "https://dashscope.aliyuncs.com/compatible-mode/v1"
        ).replaceAll("/+$", "");

        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/models"))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();
        HttpResponse<String> response = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build()
                .send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode(), () -> "DashScope model-list request failed: "
                + response.body().substring(0, Math.min(response.body().length(), 500)));

        JsonNode models = OBJECT_MAPPER.readTree(response.body()).path("data");
        List<String> visionModels = new ArrayList<>();
        for (JsonNode model : models) {
            String modelId = model.path("id").asText();
            if (isVisionLanguageModel(modelId)) {
                visionModels.add(modelId);
            }
        }

        assertFalse(visionModels.isEmpty(), "No visual-language model was returned for this API key.");
        System.out.println("DashScope visual-language models available to this API key:");
        visionModels.stream().sorted().forEach(model -> System.out.println(" - " + model));
    }

    private static boolean isVisionLanguageModel(String modelId) {
        String normalized = modelId.toLowerCase(Locale.ROOT);
        return normalized.contains("-vl")
                || normalized.contains("vision")
                || normalized.contains("omni")
                || normalized.startsWith("qvq");
    }

    private static Properties loadDotEnv() throws IOException {
        Path dotEnv = Path.of(".env");
        if (!Files.isRegularFile(dotEnv)) {
            throw new IllegalStateException("Missing .env in the project root. Copy .env.example first.");
        }
        Properties properties = new Properties();
        try (var input = Files.newInputStream(dotEnv)) {
            properties.load(input);
        }
        return properties;
    }

    private static String require(Properties env, String name) {
        String value = env.getProperty(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " must be configured in .env");
        }
        return value;
    }
}
