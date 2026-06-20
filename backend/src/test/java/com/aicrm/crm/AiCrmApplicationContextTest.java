package com.aicrm.crm;

import com.aicrm.crm.support.PostgresTestBase;
import org.junit.jupiter.api.Test;

/**
 * 冒煙測試：完整啟動 Spring context（觸發 Flyway migration、JPA metamodel、derived query 解析）。
 * 以空金鑰啟動避免任何真實 LLM 設定影響；只要 context 能 boot 即通過。
 * DB 由 PostgresTestBase 的 Testcontainers pgvector Postgres 提供。
 */
class AiCrmApplicationContextTest extends PostgresTestBase {

    /** context 啟動驗證：無內容，能成功 boot 即為通過。 */
    @Test
    void contextLoads() {
    }
}
