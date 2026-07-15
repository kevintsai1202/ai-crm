package com.aicrm.crm.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.aicrm.crm.support.PostgresTestBase;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.annotation.Transactional;

/**
 * V21 JSONB backfill 的真實 PostgreSQL 整合測試。
 */
@Transactional
class AiModelCapabilityMigrationIntegrationTest extends PostgresTestBase {

    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;

    /** 執行完整 V21 SQL，證明 migration 在已套用資料庫亦可安全重跑。 */
    private void rerunV21() {
        new ResourceDatabasePopulator(
                new ClassPathResource("db/migration/V21__add_ai_model_capabilities.sql"))
                .execute(dataSource);
    }

    /** 讀取目前 model options 原始 JSON。 */
    private String modelOptionsJson() {
        return jdbc.queryForObject("""
                SELECT setting_value FROM system_settings WHERE setting_key = 'ai.chat.model_options'
                """, String.class);
    }

    /** 缺欄位與 JSON null 會補預設，既有可信欄位保留，非 array 值不變。 */
    @Test
    void v21Backfill_handlesLegacyNullExistingAndNonArrayValues() {
        jdbc.update("""
                UPDATE system_settings SET setting_value = ? WHERE setting_key = 'ai.chat.model_options'
                """, "[{\"model\":\"legacy\",\"providerId\":null}]");
        rerunV21();
        assertThat(modelOptionsJson()).contains("\"capabilities\": []", "\"capabilitySource\": \"UNKNOWN\"");

        jdbc.update("""
                UPDATE system_settings SET setting_value = ? WHERE setting_key = 'ai.chat.model_options'
                """, "[{\"model\":\"null-fields\",\"providerId\":1,\"capabilities\":null,\"capabilitySource\":null}]");
        rerunV21();
        assertThat(modelOptionsJson()).contains("\"capabilities\": []", "\"capabilitySource\": \"UNKNOWN\"");

        var trusted = "[{\"model\":\"trusted\",\"providerId\":2,\"capabilities\":[\"VISION\"],"
                + "\"capabilitySource\":\"MANUAL\"}]";
        jdbc.update("""
                UPDATE system_settings SET setting_value = ? WHERE setting_key = 'ai.chat.model_options'
                """, trusted);
        rerunV21();
        assertThat(modelOptionsJson()).contains("\"capabilities\": [\"VISION\"]", "\"capabilitySource\": \"MANUAL\"");

        var nonArray = "{\"model\":\"not-an-array\"}";
        jdbc.update("""
                UPDATE system_settings SET setting_value = ? WHERE setting_key = 'ai.chat.model_options'
                """, nonArray);
        rerunV21();
        assertThat(modelOptionsJson()).isEqualTo(nonArray);
    }
}
