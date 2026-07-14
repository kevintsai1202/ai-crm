package com.aicrm.crm.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aicrm.crm.domain.AiProvider;
import com.aicrm.crm.domain.CapabilitySource;
import com.aicrm.crm.domain.ModelCapability;
import com.aicrm.crm.repository.AiProviderRepository;
import com.aicrm.crm.service.SystemSettingService;
import com.aicrm.crm.service.ai.ModelCatalogClient;
import com.aicrm.crm.support.PostgresTestBase;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.client.RestClientException;

/**
 * Admin 模型能力 refresh、人工設定與 assignment API 整合測試。
 */
@Transactional
class AdminModelCapabilityIntegrationTest extends PostgresTestBase {

    @Autowired WebApplicationContext context;
    @Autowired FilterChainProxy springSecurityFilterChain;
    @Autowired AiProviderRepository providerRepository;
    @Autowired SystemSettingService systemSettings;
    @MockitoBean ModelCatalogClient modelCatalogClient;

    /** 建立套用 security filter 的 MockMvc。 */
    private MockMvc mockMvc() {
        return MockMvcBuilders.webAppContextSetup(context).addFilters(springSecurityFilterChain).build();
    }

    /** 取得 seed ADMIN 的 JWT。 */
    private String adminToken() throws Exception {
        var result = mockMvc().perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin@aurora.local\",\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.token");
    }

    /** 人工能力設定後可指定 OCR；不相容轉錄 assignment 仍回 400。 */
    @Test
    void manualCapabilityAndAssignments_areValidatedByBackend() throws Exception {
        var provider = providerRepository.saveAndFlush(
                new AiProvider("V21-manual", "https://example.invalid", null, "admin"));
        var unknown = new Dtos.ModelOptionItem(
                "manual-model", provider.getId(), Set.of(), CapabilitySource.UNKNOWN);
        systemSettings.updateAiSettings("", null, List.of(unknown), null, null, null, "admin");
        var token = adminToken();

        mockMvc().perform(put("/api/admin/settings/ai/models/manual-model/capabilities")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"providerId\":" + provider.getId() + ",\"capabilities\":[\"VISION\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.capabilities[0]").value("VISION"))
                .andExpect(jsonPath("$.capabilitySource").value("MANUAL"));

        mockMvc().perform(put("/api/admin/settings/ai/assignments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"chatModel\":\"\",\"ocrModel\":\"manual-model\",\"ocrProviderId\":"
                                + provider.getId() + ",\"transcriptionModel\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ocrModel").value("manual-model"))
                .andExpect(jsonPath("$.ocrProviderId").value(provider.getId()));

        mockMvc().perform(put("/api/admin/settings/ai/assignments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"chatModel\":\"\",\"ocrModel\":\"\",\"transcriptionModel\":\"manual-model\","
                                + "\"transcriptionProviderId\":" + provider.getId() + "}"))
                .andExpect(status().isBadRequest());
    }

    /** refresh 只替換目標 Provider 模型，並保留 client 判定的 AUTO 能力。 */
    @Test
    void refreshModels_replacesProviderCatalogAndPreservesOtherProviders() throws Exception {
        var target = providerRepository.saveAndFlush(
                new AiProvider("V21-refresh", "https://example.invalid", null, "admin"));
        var other = providerRepository.saveAndFlush(
                new AiProvider("V21-other", "https://example.invalid", null, "admin"));
        systemSettings.updateAiSettings("", null, List.of(
                new Dtos.ModelOptionItem("old-target", target.getId(), Set.of(), CapabilitySource.UNKNOWN),
                new Dtos.ModelOptionItem("other-model", other.getId(), Set.of(), CapabilitySource.UNKNOWN)
        ), null, null, null, "admin");
        when(modelCatalogClient.discover(any())).thenReturn(List.of(
                new Dtos.ModelOptionItem("new-vision", target.getId(),
                        Set.of(ModelCapability.VISION), CapabilitySource.AUTO)));

        mockMvc().perform(post("/api/admin/settings/ai/providers/{id}/models/refresh", target.getId())
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].model").value("other-model"))
                .andExpect(jsonPath("$[1].model").value("new-vision"))
                .andExpect(jsonPath("$[1].capabilitySource").value("AUTO"));

        assertThat(systemSettings.getModelOptions()).extracting(Dtos.ModelOptionItem::model)
                .containsExactly("other-model", "new-vision");
    }

    /** Provider 目錄呼叫失敗時應回 502，不可誤報一般伺服器錯誤。 */
    @Test
    void refreshModels_whenProviderFails_returnsBadGateway() throws Exception {
        var provider = providerRepository.saveAndFlush(
                new AiProvider("V21-failing", "https://example.invalid", null, "admin"));
        when(modelCatalogClient.discover(any())).thenThrow(new RestClientException("provider unavailable"));

        mockMvc().perform(post("/api/admin/settings/ai/providers/{id}/models/refresh", provider.getId())
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isBadGateway());
    }
}
