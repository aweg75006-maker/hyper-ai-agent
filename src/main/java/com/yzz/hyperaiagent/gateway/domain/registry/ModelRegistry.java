package com.yzz.hyperaiagent.gateway.domain.registry;

import com.yzz.hyperaiagent.gateway.infrastructure.persistence.GatewayConfigRepository;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 模型与路由的内存注册表。
 *
 * <p>先完整读取数据库，再通过 AtomicReference 一次发布；刷新失败时旧快照保持可用。</p>
 */
@Slf4j
@Component
@DependsOn("flyway")
public class ModelRegistry {

    private final GatewayConfigRepository repository;
    private final AtomicReference<ModelRegistrySnapshot> current =
            new AtomicReference<>(ModelRegistrySnapshot.empty());
    private final AtomicLong versionSequence = new AtomicLong();

    public ModelRegistry(GatewayConfigRepository repository) {
        this.repository = repository;
    }

    /** Flyway 完成后立即加载，确保启动阶段需要调用模型的旧业务 Bean 也能读到路由。 */
    @PostConstruct
    public void initialize() {
        refresh();
    }

    public ModelRegistrySnapshot snapshot() {
        return current.get();
    }

    public synchronized ModelRegistrySnapshot refresh() {
        long nextVersion = versionSequence.incrementAndGet();
        ModelRegistrySnapshot next = repository.loadSnapshot(nextVersion);
        current.set(next);
        log.info("AI Gateway 注册表刷新完成: version={}, providers={}, models={}, routes={}",
                next.version(), next.providersById().size(), next.modelsByKey().size(), next.routesByKey().size());
        return next;
    }
}
