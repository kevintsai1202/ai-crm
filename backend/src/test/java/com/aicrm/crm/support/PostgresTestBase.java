package com.aicrm.crm.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 整合測試基底：以 Testcontainers 啟動 pgvector Postgres，並注入為 DataSource。
 * 函式級註解：空 OpenAI 金鑰強制 AI fallback；容器以「手動啟動的 static singleton」模式共用，
 * 不使用 @Testcontainers/@Container（其生命週期綁定每個測試類別會在多類別同 JVM 執行時
 * 提早關閉容器，導致後續類別共用的 Spring context 連到已關閉容器）。容器只啟動一次，
 * 由 JVM 結束時 Ryuk 自動回收。
 */
@SpringBootTest(properties = "spring.ai.openai.api-key=")
@ActiveProfiles("test")
public abstract class PostgresTestBase {

    /** 跨所有子類別共用的 pgvector 容器（singleton，啟動一次不關閉）。 */
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    static {
        // 手動啟動容器；不呼叫 stop()，讓 Ryuk 在 JVM 結束時回收，避免類別間被提早關閉
        postgres.start();
    }
}
