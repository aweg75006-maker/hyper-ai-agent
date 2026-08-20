package com.yzz.hyperaiagent.gateway.domain.quota;

import com.yzz.hyperaiagent.gateway.infrastructure.persistence.GatewayConsumerRepository;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

/** Gateway 调用方 Key 的签发与不可逆摘要服务。 */
@Service
public class GatewayApiKeyService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private final GatewayConsumerRepository repository;

    public GatewayApiKeyService(GatewayConsumerRepository repository) {
        this.repository = repository;
    }

    public IssuedApiKey create(String name) {
        String consumerId = "consumer-" + UUID.randomUUID().toString().replace("-", "");
        String apiKey = generateApiKey();
        repository.createConsumer(consumerId, name, hash(apiKey), prefix(apiKey));
        return new IssuedApiKey(consumerId, apiKey, prefix(apiKey));
    }

    public IssuedApiKey rotate(String consumerId) {
        String apiKey = generateApiKey();
        repository.rotateKey(consumerId, hash(apiKey), prefix(apiKey));
        return new IssuedApiKey(consumerId, apiKey, prefix(apiKey));
    }

    public String hash(String apiKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(apiKey.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("当前 JDK 不支持 SHA-256", impossible);
        }
    }

    private String generateApiKey() {
        byte[] random = new byte[32];
        SECURE_RANDOM.nextBytes(random);
        return "gwk_" + Base64.getUrlEncoder().withoutPadding().encodeToString(random);
    }

    private String prefix(String apiKey) {
        return apiKey.substring(0, Math.min(apiKey.length(), 12));
    }

    /** apiKey 只在创建或轮换响应中返回一次，后续查询只能看到 prefix。 */
    public record IssuedApiKey(String consumerId, String apiKey, String prefix) {
    }
}
