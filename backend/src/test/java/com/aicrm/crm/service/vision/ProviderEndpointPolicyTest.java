package com.aicrm.crm.service.vision;

import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;

/** Provider endpoint SSRF 與路徑正規化測試。 */
class ProviderEndpointPolicyTest {
    private final ProviderEndpointPolicy publicOnly=new ProviderEndpointPolicy("");

    /** 公開 HTTPS base 的 /v1 與 trailing slash 皆安全正規化。 */
    @Test void normalizesSafePaths(){
        assertThat(publicOnly.resolveAndValidate("https://api.openai.com").uri().toString()).isEqualTo("https://api.openai.com/v1/chat/completions");
        assertThat(publicOnly.resolveAndValidate("https://api.openai.com/v1/").uri().toString()).isEqualTo("https://api.openai.com/v1/chat/completions");
    }

    /** userinfo、非 HTTPS、loopback 與 IPv6 loopback 預設拒絕。 */
    @Test void rejectsUnsafeEndpoints(){
        assertThatThrownBy(()->publicOnly.resolveAndValidate("https://user@api.openai.com")).isInstanceOf(VisionServiceException.class);
        assertThatThrownBy(()->publicOnly.resolveAndValidate("https://api.openai.com/v1?target=internal")).isInstanceOf(VisionServiceException.class);
        assertThatThrownBy(()->publicOnly.resolveAndValidate("https://api.openai.com/proxy/internal")).isInstanceOf(VisionServiceException.class);
        assertThatThrownBy(()->publicOnly.resolveAndValidate("http://api.openai.com")).isInstanceOf(VisionServiceException.class);
        assertThatThrownBy(()->publicOnly.resolveAndValidate("https://127.0.0.1")).isInstanceOf(VisionServiceException.class);
        assertThatThrownBy(()->publicOnly.resolveAndValidate("https://[::1]")).isInstanceOf(VisionServiceException.class);
    }

    /** 測試／自架 Provider 只有明確 allowlist 才可使用本機 HTTP。 */
    @Test void allowlistPermitsExplicitPrivateHost(){
        assertThat(new ProviderEndpointPolicy("localhost").resolveAndValidate("http://localhost:8080/v1/").uri().toString())
                .isEqualTo("http://localhost:8080/v1/chat/completions");
    }
}
