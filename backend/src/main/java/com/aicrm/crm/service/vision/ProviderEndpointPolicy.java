package com.aicrm.crm.service.vision;

import java.net.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** 驗證 AI Provider endpoint，避免管理員設定被利用進行 SSRF。 */
@Component
public class ProviderEndpointPolicy {
    private final Set<String> privateHostAllowlist;
    private final DnsLookup dnsLookup;

    /** 建立端點政策；私有位址必須由部署設定明確允許。 */
    @Autowired public ProviderEndpointPolicy(@Value("${app.ai.provider-private-host-allowlist:}") String allowlist) {
        this(allowlist, InetAddress::getAllByName);
    }

    /** 測試可注入 deterministic DNS，正式環境使用系統 resolver。 */
    ProviderEndpointPolicy(String allowlist, DnsLookup dnsLookup) {
        Set<String> values = new HashSet<>();
        for (String value : allowlist.split(",")) if (!value.isBlank()) values.add(value.trim().toLowerCase(Locale.ROOT));
        this.privateHostAllowlist = Set.copyOf(values);
        this.dnsLookup=dnsLookup;
    }

    /** 驗證並安全組成 OpenAI-compatible chat completions endpoint。 */
    public ApprovedEndpoint resolveAndValidate(String baseUrl) {
        String value = baseUrl == null || baseUrl.isBlank() ? "https://api.openai.com" : baseUrl.trim();
        URI base;
        try { base = URI.create(value); } catch (RuntimeException e) { throw rejected(); }
        String scheme = lower(base.getScheme()), host = lower(base.getHost());
        if (!("http".equals(scheme) || "https".equals(scheme)) || host == null || base.getUserInfo() != null
                || base.getFragment() != null || base.getQuery() != null) throw rejected();
        boolean allowedPrivate = privateHostAllowlist.contains(host);
        if (!allowedPrivate && !"https".equals(scheme)) throw rejected();
        InetAddress[] approved=validateAddresses(host, allowedPrivate);
        String path = Optional.ofNullable(base.getPath()).orElse("").replaceAll("/+$", "");
        if(!path.isEmpty()&&!path.equals("/v1"))throw rejected();
        if (path.endsWith("/v1")) path = path.substring(0, path.length() - 3);
        try { return new ApprovedEndpoint(new URI(scheme, null, host, base.getPort(), path + "/v1/chat/completions", null, null),approved); }
        catch (URISyntaxException e) { throw rejected(); }
    }

    private InetAddress[] validateAddresses(String host, boolean allowPrivate) {
        try {
            InetAddress[] addresses = dnsLookup.resolve(host);
            if (addresses.length == 0) throw rejected();
            for (InetAddress a : addresses) if (!allowPrivate && (a.isAnyLocalAddress() || a.isLoopbackAddress()
                    || a.isLinkLocalAddress() || a.isSiteLocalAddress() || a.isMulticastAddress())) throw rejected();
            return addresses.clone();
        } catch (UnknownHostException e) { throw rejected(); }
    }
    private String lower(String value) { return value == null ? null : value.toLowerCase(Locale.ROOT); }
    private VisionServiceException rejected() { return new VisionServiceException("Vision provider endpoint 不符合安全政策"); }

    /** 單次驗證核准且須由 transport 釘選的端點。 */
    public record ApprovedEndpoint(URI uri, InetAddress[] addresses) { public ApprovedEndpoint { addresses=addresses.clone(); } @Override public InetAddress[] addresses(){return addresses.clone();} }
    /** DNS 查詢邊界，便於驗證 rebinding。 */
    @FunctionalInterface interface DnsLookup { InetAddress[] resolve(String host) throws UnknownHostException; }
}
