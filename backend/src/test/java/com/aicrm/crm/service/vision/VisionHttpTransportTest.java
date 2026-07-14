package com.aicrm.crm.service.vision;

import static org.assertj.core.api.Assertions.*;
import java.io.*;
import java.net.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** DNS pinning 與有界 response stream 測試。 */
class VisionHttpTransportTest {
    /** policy 核准後即使後續 DNS 改成 private，transport resolver 仍只回核准 IP。 */
    @Test void pinnedResolverNeverUsesReboundSystemAddress() throws Exception {
        InetAddress publicAddress=InetAddress.getByAddress("provider.example",new byte[]{8,8,8,8});
        AtomicInteger lookups=new AtomicInteger();
        ProviderEndpointPolicy policy=new ProviderEndpointPolicy("",host->{lookups.incrementAndGet();return new InetAddress[]{publicAddress};});
        var approved=policy.resolveAndValidate("https://provider.example/v1");
        // 模擬此刻 system DNS 已可能回 127.0.0.1；pinned resolver 不會再呼叫 policy/system lookup。
        assertThat(new VisionHttpTransport().pinnedResolver(approved).resolve("provider.example")).containsExactly(publicAddress);
        assertThat(lookups).hasValue(1);
    }

    /** 超過 1 MiB 時最多讀 limit+1 並立即關閉，不配置完整回應。 */
    @Test void boundedReaderStopsAtLimitPlusOneAndCloses() {
        CountingStream stream=new CountingStream(VisionHttpTransport.MAX_BODY+100_000);
        assertThatThrownBy(()->new VisionHttpTransport().readBounded(stream)).isInstanceOf(VisionServiceException.class).hasMessage("Vision provider 回應過大");
        assertThat(stream.readCount).isEqualTo(VisionHttpTransport.MAX_BODY+1); assertThat(stream.closed).isTrue();
    }
    private static final class CountingStream extends InputStream {int remaining,readCount;boolean closed;CountingStream(int size){remaining=size;}@Override public int read(byte[] b,int o,int l){if(remaining==0)return -1;int n=Math.min(l,remaining);remaining-=n;readCount+=n;return n;}@Override public int read(){if(remaining--<=0)return -1;readCount++;return 0;}@Override public void close(){closed=true;}}
}
