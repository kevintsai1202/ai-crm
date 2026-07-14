package com.aicrm.crm.service.vision;

import static org.assertj.core.api.Assertions.*;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;
import tools.jackson.databind.ObjectMapper;

/** OpenAI-compatible Vision adapter 的 payload、成功與不可信回應測試。 */
class OpenAiBusinessCardRecognitionClientTest {
    private HttpServer server; private final AtomicReference<String> body=new AtomicReference<>();

    /** 每案啟動本機 deterministic HTTP fake。 */
    @BeforeEach void start() throws Exception {server=HttpServer.create(new InetSocketAddress(0),0);server.start();}
    /** 關閉 fake server。 */ @AfterEach void stop(){server.stop(0);}

    /** 使用 provider baseURL/key/model 並以 data URL 傳圖，可解析嚴格 JSON。 */
    @Test void recognize_sendsProviderPayloadAndParsesStructuredJson(){
        server.createContext("/v1/chat/completions",exchange->{body.set(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));
            String response="{\"choices\":[{\"message\":{\"content\":\"{\\\"personName\\\":\\\"王小明\\\",\\\"title\\\":\\\"採購\\\",\\\"email\\\":\\\"a@example.com\\\",\\\"phone\\\":\\\"0912\\\",\\\"companyName\\\":\\\"未來科技\\\",\\\"website\\\":null,\\\"confidence\\\":{},\\\"warnings\\\":[]}\"}}]}";
            byte[] bytes=response.getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(200,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();});
        var result=new OpenAiBusinessCardRecognitionClient(new ObjectMapper()).recognize(new byte[]{1,2},"image/png",assignment());
        assertThat(result.companyName()).isEqualTo("未來科技"); assertThat(body.get()).contains("vision-test","data:image/png;base64,AQI=","json_object");
    }

    /** 非 JSON 或缺少 choices 內容必須 fail closed。 */
    @Test void recognize_invalidResponse_throwsSanitizedServiceException(){
        server.createContext("/v1/chat/completions",exchange->{byte[] bytes="{}".getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(200,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();});
        assertThatThrownBy(()->new OpenAiBusinessCardRecognitionClient(new ObjectMapper()).recognize(new byte[]{1},"image/png",assignment()))
                .isInstanceOf(VisionServiceException.class).hasMessage("Vision provider 回應缺少內容");
    }

    /** 建立指向本機 fake 的 assignment。 */
    private AiModelAssignment assignment(){return new AiModelAssignment("vision-test",9L,"http://localhost:"+server.getAddress().getPort(),"secret");}
}
