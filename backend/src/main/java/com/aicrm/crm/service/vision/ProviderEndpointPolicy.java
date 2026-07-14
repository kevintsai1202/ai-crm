package com.aicrm.crm.service.vision;

import java.net.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** 驗證 AI Provider endpoint，避免管理員設定被利用進行 SSRF。 */
@Component
public class ProviderEndpointPolicy {
    private final Set<String> privateHostAllowlist;

    /** 建立端點政策；私有位址必須由部署設定明確允許。 */
    public ProviderEndpointPolicy(@Value("${app.ai.provider-private-host-allowlist:}") String allowlist) {
        Set<String> values = new HashSet<>();
        for (String value : allowlist.split(",")) if (!value.isBlank()) values.add(value.trim().toLowerCase(Locale.ROOT));
        this.privateHostAllowlist = Set.copyOf(values);
    }

    /** 驗證並安全組成 OpenAI-compatible chat completions endpoint。 */
    public URI chatCompletions(String baseUrl) {
        String value = baseUrl == null || baseUrl.isBlank() ? "https://api.openai.com" : baseUrl.trim();
        URI base;
        try { base = URI.create(value); } catch (RuntimeException e) { throw rejected(); }
        String scheme = lower(base.getScheme()), host = lower(base.getHost());
        if (!("http".equals(scheme) || "https".equals(scheme)) || host == null || base.getUserInfo() != null
                || base.getFragment() != null || base.getQuery() != null) throw rejected();
        boolean allowedPrivate = privateHostAllowlist.contains(host);
        if (!allowedPrivate && !"https".equals(scheme)) throw rejected();
        validateAddresses(host, allowedPrivate);
        String path = Optional.ofNullable(base.getPath()).orElse("").replaceAll("/+$", "");
        if (path.endsWith("/v1")) path = path.substring(0, path.length() - 3);
        try { return new URI(scheme, null, host, base.getPort(), path + "/v1/chat/completions", null, null); }
        catch (URISyntaxException e) { throw rejected(); }
    }

    /** request 前再次解析所有 A/AAAA，降低 DNS rebinding 風險。 */
    public void revalidate(URI endpoint) { validateAddresses(endpoint.getHost(), privateHostAllowlist.contains(lower(endpoint.getHost()))); }

    private void validateAddresses(String host, boolean allowPrivate) {
        try {
            InetAddress[] addresses = InetAddress.getAllByName(host);
            if (addresses.length == 0) throw rejected();
            for (InetAddress a : addresses) if (!allowPrivate && (a.isAnyLocalAddress() || a.isLoopbackAddress()
                    || a.isLinkLocalAddress() || a.isSiteLocalAddress() || a.isMulticastAddress())) throw rejected();
        } catch (UnknownHostException e) { throw rejected(); }
    }
    private String lower(String value) { return value == null ? null : value.toLowerCase(Locale.ROOT); }
    private VisionServiceException rejected() { return new VisionServiceException("Vision provider endpoint 不符合安全政策"); }
}
