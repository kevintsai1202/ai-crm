package com.aicrm.crm.service.transcription;

import com.aicrm.crm.service.vision.AiModelAssignment;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * OpenAI-compatible 語音轉錄 adapter，使用 Provider base URL 與憑證，
 * 以 multipart/form-data 呼叫 {@code /v1/audio/transcriptions}。第一版僅解析回應的 {@code text} 欄位。
 * 未設定或呼叫失敗一律拋 {@link TranscriptionException}，由服務轉為 session FAILED。
 */
@Component
public class OpenAiCompatibleTranscriptionClient implements TranscriptionClient {
    /** Jackson 3 parser。 */
    private final ObjectMapper mapper;
    /** 回應內容上限，避免異常大回應占用記憶體。 */
    private static final int MAX_CONTENT = 200_000;

    /** 建立轉錄 adapter。 */
    public OpenAiCompatibleTranscriptionClient(ObjectMapper mapper) { this.mapper = mapper; }

    /** 以 multipart 上傳音訊並要求模型輸出逐字稿。 */
    @Override
    public Transcript transcribe(byte[] audio, String mimeType, AiModelAssignment assignment) {
        if (assignment == null || audio == null || audio.length == 0) throw new TranscriptionException("轉錄輸入不完整");
        try {
            URI endpoint = resolveEndpoint(assignment.baseUrl());
            String boundary = "----aicrm" + Long.toHexString(System.nanoTime());
            byte[] body = multipartBody(boundary, assignment.model(), audio, mimeType);
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();
            HttpRequest request = HttpRequest.newBuilder(endpoint)
                    .timeout(Duration.ofMinutes(5))
                    .header("Authorization", "Bearer " + assignment.apiKey())
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body)).build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) throw new TranscriptionException("轉錄 provider HTTP 錯誤");
            String raw = response.body();
            if (raw == null || raw.isBlank() || raw.length() > MAX_CONTENT) throw new TranscriptionException("轉錄 provider 回應無效");
            JsonNode node = mapper.readTree(raw);
            String text = node.path("text").asText("");
            if (text.isBlank()) throw new TranscriptionException("轉錄 provider 回應缺少逐字稿");
            return new Transcript(text.trim());
        } catch (TranscriptionException exception) { throw exception; }
        catch (Exception exception) { throw new TranscriptionException("轉錄 provider 回應無法處理", exception); }
    }

    /** 僅接受 http/https base URL，組成 audio transcription endpoint。 */
    private URI resolveEndpoint(String baseUrl) {
        String value = baseUrl == null || baseUrl.isBlank() ? "https://api.openai.com" : baseUrl.trim();
        URI base;
        try { base = URI.create(value); } catch (RuntimeException e) { throw new TranscriptionException("轉錄 provider endpoint 不合法"); }
        String scheme = base.getScheme() == null ? "" : base.getScheme().toLowerCase(Locale.ROOT);
        if (!("http".equals(scheme) || "https".equals(scheme)) || base.getAuthority() == null) throw new TranscriptionException("轉錄 provider endpoint 不合法");
        // 去除尾端斜線與既有 /v1，再統一補上 audio 轉錄路徑，避免路徑重複。
        String path = base.getPath() == null ? "" : base.getPath().replaceAll("/+$", "");
        if (path.endsWith("/v1")) path = path.substring(0, path.length() - 3);
        try { return URI.create(scheme + "://" + base.getAuthority() + path + "/v1/audio/transcriptions"); }
        catch (RuntimeException e) { throw new TranscriptionException("轉錄 provider endpoint 不合法"); }
    }

    /** 組裝 model 與 file 兩個 multipart part。 */
    private byte[] multipartBody(String boundary, String model, byte[] audio, String mimeType) throws Exception {
        String dash = "--";
        String crlf = "\r\n";
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write((dash + boundary + crlf).getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Disposition: form-data; name=\"model\"" + crlf + crlf + model + crlf).getBytes(StandardCharsets.UTF_8));
        out.write((dash + boundary + crlf).getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Disposition: form-data; name=\"file\"; filename=\"meeting" + suffix(mimeType) + "\"" + crlf).getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Type: " + (mimeType == null ? "application/octet-stream" : mimeType) + crlf + crlf).getBytes(StandardCharsets.UTF_8));
        out.write(audio);
        out.write(crlf.getBytes(StandardCharsets.UTF_8));
        out.write((dash + boundary + dash + crlf).getBytes(StandardCharsets.UTF_8));
        return out.toByteArray();
    }

    /** 依 MIME 提供 provider 可辨識的檔名副檔名。 */
    private String suffix(String mime) {
        if (mime == null) return ".bin";
        if (mime.contains("mpeg") || mime.contains("mp3")) return ".mp3";
        if (mime.contains("mp4") || mime.contains("m4a")) return ".m4a";
        if (mime.contains("wav")) return ".wav";
        return ".bin";
    }
}
