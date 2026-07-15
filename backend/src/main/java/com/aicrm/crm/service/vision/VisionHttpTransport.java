package com.aicrm.crm.service.vision;

import com.aicrm.crm.service.vision.ProviderEndpointPolicy.ApprovedEndpoint;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.util.Timeout;
import org.springframework.stereotype.Component;

/** 只使用 policy 核准 IP 的 Vision HTTP transport，保留原 hostname 供 TLS SNI 驗證。 */
@Component
public class VisionHttpTransport {
    static final int MAX_BODY=1_000_000;

    /** 以單次 pinned DNS resolver 發送請求，停用 redirect 並限制回應串流。 */
    public Response post(ApprovedEndpoint endpoint,String apiKey,String json) {
        String approvedHost=endpoint.uri().getHost(); InetAddress[] approved=endpoint.addresses();
        DnsResolver pinned=pinnedResolver(endpoint);
        var manager=PoolingHttpClientConnectionManagerBuilder.create().setDnsResolver(pinned).build();
        var config=RequestConfig.custom().setConnectTimeout(Timeout.ofSeconds(5)).setResponseTimeout(Timeout.ofSeconds(30)).build();
        try(var client=HttpClients.custom().setConnectionManager(manager).disableRedirectHandling().setDefaultRequestConfig(config).build()) {
            HttpPost request=new HttpPost(endpoint.uri()); request.setHeader("Content-Type","application/json");
            request.setHeader("Authorization","Bearer "+apiKey); request.setEntity(new StringEntity(json,StandardCharsets.UTF_8));
            return client.execute(request,response->{int status=response.getCode();InputStream input=response.getEntity()==null?InputStream.nullInputStream():response.getEntity().getContent();return new Response(status,readBounded(input));});
        } catch(VisionServiceException e){throw e;} catch(Exception e){throw new VisionServiceException("Vision provider HTTP 請求失敗",e);}
    }

    /** 建立只認核准 hostname/IP 的 resolver，不再呼叫 system DNS。 */
    DnsResolver pinnedResolver(ApprovedEndpoint endpoint){String approvedHost=endpoint.uri().getHost();InetAddress[] approved=endpoint.addresses();return new DnsResolver(){
            @Override public InetAddress[] resolve(String host) throws UnknownHostException {if(!approvedHost.equalsIgnoreCase(host))throw new UnknownHostException("未核准 host");return approved.clone();}
            @Override public String resolveCanonicalHostname(String host) throws UnknownHostException {if(!approvedHost.equalsIgnoreCase(host))throw new UnknownHostException("未核准 host");return approvedHost;}
        };}

    /** 最多配置 1 MiB，讀到第 1 MiB+1 byte 立即關閉並拒絕。 */
    byte[] readBounded(InputStream input) throws IOException {
        try(input;ByteArrayOutputStream out=new ByteArrayOutputStream(Math.min(MAX_BODY,8192))){byte[] buffer=new byte[8192];int total=0,read;while((read=input.read(buffer,0,Math.min(buffer.length,MAX_BODY+1-total)))!=-1){total+=read;if(total>MAX_BODY)throw new VisionServiceException("Vision provider 回應過大");out.write(buffer,0,read);}return out.toByteArray();}
    }
    /** 有界 transport 回應。 */ public record Response(int status,byte[] body){public Response{body=Arrays.copyOf(body,body.length);}@Override public byte[] body(){return Arrays.copyOf(body,body.length);}}
}
