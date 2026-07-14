package com.aicrm.crm.service.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.aicrm.crm.domain.AiProvider;
import com.aicrm.crm.domain.CapabilitySource;
import com.aicrm.crm.domain.ModelCapability;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * OpenAI-compatible 模型目錄的 metadata 能力探索測試。
 */
class OpenAiCompatibleModelCatalogClientTest {

    /** Provider 明確回傳 image input modality 時標為 VISION/AUTO。 */
    @Test
    void discover_mapsReliableImageMetadataToVisionAuto() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://models.example/v1/models"))
                .andExpect(header("Authorization", "Bearer secret"))
                .andRespond(withSuccess("""
                        {"data":[{"id":"vision-model","input_modalities":["text","image"]}]}
                        """, MediaType.APPLICATION_JSON));

        var provider = new AiProvider("Example", "https://models.example/v1", "secret", "admin");
        var result = new OpenAiCompatibleModelCatalogClient(builder).discover(provider);

        assertThat(result).singleElement().satisfies(option -> {
            assertThat(option.model()).isEqualTo("vision-model");
            assertThat(option.capabilities()).containsExactly(ModelCapability.VISION);
            assertThat(option.capabilitySource()).isEqualTo(CapabilitySource.AUTO);
        });
        server.verify();
    }

    /** 只有 OpenAI 基本模型欄位時必須 UNKNOWN，不得由看似 vision 的名稱猜測。 */
    @Test
    void discover_withoutCapabilityMetadataRemainsUnknown() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://models.example/v1/models"))
                .andRespond(withSuccess("""
                        {"data":[{"id":"gpt-4o-vision","object":"model","created":123,"owned_by":"vendor"}]}
                        """, MediaType.APPLICATION_JSON));

        var provider = new AiProvider("Example", "https://models.example", null, "admin");
        var result = new OpenAiCompatibleModelCatalogClient(builder).discover(provider);

        assertThat(result).singleElement().satisfies(option -> {
            assertThat(option.capabilities()).isEmpty();
            assertThat(option.capabilitySource()).isEqualTo(CapabilitySource.UNKNOWN);
        });
        server.verify();
    }

    /** Provider 明確回傳 audio input modality 時標為 AUDIO_TRANSCRIPTION/AUTO。 */
    @Test
    void discover_mapsReliableAudioMetadataToTranscriptionAuto() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://models.example/v1/models"))
                .andRespond(withSuccess("""
                        {"data":[{"id":"audio-model","input_modalities":["audio"]}]}
                        """, MediaType.APPLICATION_JSON));

        var provider = new AiProvider("Example", "https://models.example/v1/", null, "admin");
        var result = new OpenAiCompatibleModelCatalogClient(builder).discover(provider);

        assertThat(result.getFirst().capabilities()).containsExactly(ModelCapability.AUDIO_TRANSCRIPTION);
        assertThat(result.getFirst().capabilitySource()).isEqualTo(CapabilitySource.AUTO);
        server.verify();
    }

    /** input_modalities 為 null、字串或含非字串元素時不是可靠 metadata。 */
    @Test
    void discover_withMalformedInputModalitiesRemainsUnknown() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://models.example/v1/models"))
                .andRespond(withSuccess("""
                        {"data":[
                          {"id":"null-modalities","input_modalities":null},
                          {"id":"string-modalities","input_modalities":"image"},
                          {"id":"mixed-modalities","input_modalities":["image",7]},
                          {"id":"blank-modalities","input_modalities":[""]}
                        ]}
                        """, MediaType.APPLICATION_JSON));

        var provider = new AiProvider("Example", "https://models.example", null, "admin");
        var result = new OpenAiCompatibleModelCatalogClient(builder).discover(provider);

        assertThat(result).allSatisfy(option -> {
            assertThat(option.capabilities()).isEmpty();
            assertThat(option.capabilitySource()).isEqualTo(CapabilitySource.UNKNOWN);
        });
        server.verify();
    }

    /** 有效空陣列明確表示沒有特殊輸入能力，來源仍為 AUTO。 */
    @Test
    void discover_withValidEmptyModalitiesIsAutoWithNoCapabilities() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://models.example/v1/models"))
                .andRespond(withSuccess("{\"data\":[{\"id\":\"text-only\",\"input_modalities\":[]}]}",
                        MediaType.APPLICATION_JSON));

        var provider = new AiProvider("Example", "https://models.example", null, "admin");
        var result = new OpenAiCompatibleModelCatalogClient(builder).discover(provider);

        assertThat(result.getFirst().capabilities()).isEmpty();
        assertThat(result.getFirst().capabilitySource()).isEqualTo(CapabilitySource.AUTO);
        server.verify();
    }
}
