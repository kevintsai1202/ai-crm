package com.aicrm.crm.service.media;

import com.aicrm.crm.domain.MediaPurpose;
import com.aicrm.crm.domain.MediaStatus;
import com.aicrm.crm.domain.TemporaryMedia;
import com.aicrm.crm.repository.TemporaryMediaRepository;
import com.aicrm.crm.service.JwtService.AuthPrincipal;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/** 暫存媒體驗證、上傳、metadata 與到期清理服務。 */
@Service
@ConditionalOnProperty(name = "app.media.enabled", havingValue = "true", matchIfMissing = true)
public class TemporaryMediaService {
    private static final Logger log = LoggerFactory.getLogger(TemporaryMediaService.class);
    private static final long IMAGE_MAX = 10L * 1024 * 1024;
    private static final long AUDIO_MAX = 100L * 1024 * 1024;
    private static final Set<String> IMAGE_MIMES = Set.of("image/jpeg", "image/png", "image/webp");
    private static final Set<String> AUDIO_MIMES = Set.of("audio/mpeg", "audio/mp3", "audio/mp4", "audio/x-m4a", "audio/wav", "audio/x-wav");
    /** Object storage adapter。 */
    private final TemporaryMediaStore store;
    /** 媒體 metadata repository。 */
    private final TemporaryMediaRepository repository;
    /** pending/failed 媒體保留時間。 */
    private final Duration retention;

    /** 建立媒體服務。 */
    public TemporaryMediaService(TemporaryMediaStore store, TemporaryMediaRepository repository,
            @Value("${app.media.retention:PT24H}") Duration retention) {
        this.store = store;
        this.repository = repository;
        this.retention = retention;
    }

    /** 驗證完整內容後上傳 object，再寫 metadata；DB 失敗時補償刪除 object。 */
    public TemporaryMedia stage(MultipartFile file, MediaPurpose purpose, AuthPrincipal principal) {
        byte[] bytes = readBytes(file);
        String mime = normalizeMime(file.getContentType());
        validate(bytes, mime, purpose);
        String filename = safeFilename(file.getOriginalFilename());
        StoredMedia stored = store.put(new MediaUpload(filename, mime, bytes));
        try {
            return repository.save(new TemporaryMedia(stored.objectKey(), filename, mime, stored.sizeBytes(),
                    stored.sha256(), purpose, principal.username(), Instant.now().plus(retention)));
        } catch (RuntimeException exception) {
            try {
                store.delete(stored.objectKey());
            } catch (RuntimeException cleanupFailure) {
                exception.addSuppressed(cleanupFailure);
            }
            throw exception;
        }
    }

    /** 逐筆刪除到期 uploaded/failed 物件，物件刪除失敗時保留 metadata 供下次重試。 */
    public int deleteExpired(Instant now) {
        int deleted = 0;
        List<TemporaryMedia> candidates = repository.findCleanupCandidates(
                now, List.of(MediaStatus.UPLOADED, MediaStatus.REVIEW_PENDING, MediaStatus.FAILED));
        for (TemporaryMedia media : candidates) {
            try {
                store.delete(media.getObjectKey());
                repository.delete(media);
                deleted++;
            } catch (RuntimeException exception) {
                log.warn("暫存媒體清理失敗，保留 metadata 供下次重試：mediaId={}", media.getId(), exception);
            }
        }
        return deleted;
    }

    /** 一次讀取 multipart 並將 IO 錯誤轉為輸入驗證錯誤。 */
    private byte[] readBytes(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("媒體檔案不可為空");
        try {
            return file.getBytes();
        } catch (IOException exception) {
            throw new IllegalArgumentException("無法讀取媒體檔案", exception);
        }
    }

    /** 正規化 MIME，缺少時以空字串進入白名單拒絕。 */
    private String normalizeMime(String mime) {
        return mime == null ? "" : mime.toLowerCase(Locale.ROOT).trim();
    }

    /** 避免空檔名與過長顯示名稱；object key 不使用此值。 */
    private String safeFilename(String filename) {
        String value = filename == null || filename.isBlank() ? "upload" : filename.trim();
        return value.length() <= 255 ? value : value.substring(value.length() - 255);
    }

    /** 依用途驗 MIME/大小及對應 magic bytes。 */
    private void validate(byte[] bytes, String mime, MediaPurpose purpose) {
        boolean image = purpose == MediaPurpose.BUSINESS_CARD;
        Set<String> allowed = image ? IMAGE_MIMES : AUDIO_MIMES;
        long max = image ? IMAGE_MAX : AUDIO_MAX;
        if (!allowed.contains(mime)) throw new IllegalArgumentException("不支援的媒體 MIME 類型");
        if (bytes.length > max) throw new IllegalArgumentException("媒體檔案超過大小限制");
        boolean signatureMatches = switch (mime) {
            case "image/jpeg" -> jpeg(bytes);
            case "image/png" -> png(bytes);
            case "image/webp" -> webp(bytes);
            case "audio/mpeg", "audio/mp3" -> mp3(bytes);
            case "audio/mp4", "audio/x-m4a" -> m4a(bytes);
            case "audio/wav", "audio/x-wav" -> wav(bytes);
            default -> false;
        };
        if (!signatureMatches) throw new IllegalArgumentException("媒體內容與 MIME 不符");
    }

    /** 驗證 JPEG SOI/marker 與至少一段內容。 */
    private boolean jpeg(byte[] b) { return b.length >= 8 && u(b[0]) == 0xff && u(b[1]) == 0xd8 && u(b[2]) == 0xff && u(b[b.length - 2]) == 0xff && u(b[b.length - 1]) == 0xd9; }
    /** 驗證 PNG signature 與必要的首個 IHDR chunk，拒絕只有假 header 的短檔。 */
    private boolean png(byte[] b) { return b.length >= 24 && u(b[0]) == 0x89 && b[1]=='P' && b[2]=='N' && b[3]=='G' && b[4]==13 && b[5]==10 && b[6]==26 && b[7]==10 && ascii(b, 12, "IHDR"); }
    /** 驗證 RIFF/WEBP 與有效的首個影像 chunk。 */
    private boolean webp(byte[] b) { return b.length >= 16 && riff(b, "WEBP") && (ascii(b, 12, "VP8 ") || ascii(b, 12, "VP8L") || ascii(b, 12, "VP8X")); }
    /** 驗證 RIFF/WAVE 與必要的 fmt chunk，拒絕只有 12-byte header 的偽檔。 */
    private boolean wav(byte[] b) { return b.length >= 44 && riff(b, "WAVE") && ascii(b, 12, "fmt "); }
    /** 驗證 RIFF 宣告長度不超出檔案並符合 form type。 */
    private boolean riff(byte[] b, String type) {
        if (b.length < 12 || !ascii(b, 0, "RIFF") || !ascii(b, 8, type)) return false;
        long declared = Integer.toUnsignedLong(ByteBuffer.wrap(b, 4, 4).order(ByteOrder.LITTLE_ENDIAN).getInt());
        return declared >= 4 && declared + 8L <= b.length;
    }
    /** 驗證 ISO BMFF ftyp box 與常見 M4A major brand。 */
    private boolean m4a(byte[] b) {
        if (b.length < 12 || !ascii(b, 4, "ftyp")) return false;
        long boxSize = Integer.toUnsignedLong(ByteBuffer.wrap(b, 0, 4).getInt());
        if (boxSize < 12 || boxSize > b.length) return false;
        String brand = new String(b, 8, 4, java.nio.charset.StandardCharsets.US_ASCII);
        return Set.of("M4A ", "M4B ", "isom", "mp41", "mp42", "qt  ").contains(brand);
    }
    /** 驗證 ID3 header/declared size 或 MPEG audio frame sync。 */
    private boolean mp3(byte[] b) {
        if (b.length >= 4 && u(b[0]) == 0xff && (u(b[1]) & 0xe0) == 0xe0 && (u(b[1]) & 0x18) != 0x08) {
            int bitrateIndex = (u(b[2]) >>> 4) & 0x0f;
            int sampleRateIndex = (u(b[2]) >>> 2) & 0x03;
            return bitrateIndex > 0 && bitrateIndex < 15 && sampleRateIndex < 3;
        }
        if (b.length < 10 || !ascii(b, 0, "ID3")) return false;
        for (int i = 6; i < 10; i++) if ((u(b[i]) & 0x80) != 0) return false;
        int tagSize = (u(b[6]) << 21) | (u(b[7]) << 14) | (u(b[8]) << 7) | u(b[9]);
        return 10L + tagSize <= b.length;
    }
    /** 比對固定 ASCII signature。 */
    private boolean ascii(byte[] b, int offset, String value) {
        if (offset + value.length() > b.length) return false;
        for (int i = 0; i < value.length(); i++) if (b[offset + i] != (byte) value.charAt(i)) return false;
        return true;
    }
    /** 將 signed byte 轉為 unsigned 整數。 */
    private int u(byte value) { return value & 0xff; }
}
