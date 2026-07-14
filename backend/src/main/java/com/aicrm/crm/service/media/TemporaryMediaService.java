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
        rejectDeclaredOversize(file, purpose);
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

    /** 先用 multipart 宣告大小阻擋明顯超限內容，實際 bytes 仍會再次驗證以防偽造。 */
    private void rejectDeclaredOversize(MultipartFile file, MediaPurpose purpose) {
        if (file == null) throw new IllegalArgumentException("媒體檔案不可為空");
        long max = purpose == MediaPurpose.BUSINESS_CARD ? IMAGE_MAX : AUDIO_MAX;
        if (file.getSize() > max) throw new IllegalArgumentException("媒體檔案超過大小限制");
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

    /** 解析 JPEG marker/segment，必須包含有 entropy payload 的 SOS 並以 EOI 結束。 */
    private boolean jpeg(byte[] b) {
        if (b.length < 11 || u(b[0]) != 0xff || u(b[1]) != 0xd8) return false;
        int position = 2;
        while (position + 1 < b.length && u(b[position]) == 0xff) {
            while (position < b.length && u(b[position]) == 0xff) position++;
            if (position >= b.length) return false;
            int marker = u(b[position++]);
            if (marker == 0xd9) return false;
            if (marker == 0x01 || marker >= 0xd0 && marker <= 0xd7) continue;
            if (position + 2 > b.length) return false;
            int segmentLength = (u(b[position]) << 8) | u(b[position + 1]);
            if (segmentLength < 2 || position + segmentLength > b.length) return false;
            if (marker == 0xda && segmentLength < 6) return false;
            position += segmentLength;
            if (marker == 0xda) {
                int payload = 0;
                while (position < b.length) {
                    if (u(b[position]) != 0xff) { payload++; position++; continue; }
                    if (position + 1 >= b.length) return false;
                    int next = u(b[position + 1]);
                    if (next == 0x00) { payload++; position += 2; continue; }
                    return next == 0xd9 && payload > 0 && position + 2 == b.length;
                }
            }
        }
        return false;
    }

    /** 解析 PNG chunk 邊界，要求合法 IHDR、非空 IDAT 與最後 IEND。 */
    private boolean png(byte[] b) {
        if (b.length < 57 || u(b[0]) != 0x89 || !ascii(b, 1, "PNG") || b[4]!=13 || b[5]!=10 || b[6]!=26 || b[7]!=10) return false;
        int position = 8;
        boolean ihdr = false, idat = false;
        while (position + 12 <= b.length) {
            long length = uint32be(b, position);
            if (length > Integer.MAX_VALUE || position + 12L + length > b.length) return false;
            String type = asciiValue(b, position + 4, 4);
            int payload = position + 8;
            if (!ihdr) {
                if (!"IHDR".equals(type) || length != 13 || uint32be(b, payload) == 0 || uint32be(b, payload + 4) == 0) return false;
                ihdr = true;
            } else if ("IDAT".equals(type) && length > 0) {
                idat = true;
            } else if ("IEND".equals(type)) {
                return length == 0 && idat && position + 12 == b.length;
            }
            position += 12 + (int) length;
        }
        return false;
    }

    /** 解析 WebP RIFF chunk，宣告大小須完全一致且主要影像 chunk 必須有 payload。 */
    private boolean webp(byte[] b) {
        if (!riffExact(b, "WEBP")) return false;
        int position = 12;
        while (position + 8 <= b.length) {
            String type = asciiValue(b, position, 4);
            long length = uint32le(b, position + 4);
            long end = position + 8L + length;
            if (length == 0 || end > b.length) return false;
            int minimum = "VP8X".equals(type) ? 10 : "VP8L".equals(type) ? 5 : "VP8 ".equals(type) ? 10 : Integer.MAX_VALUE;
            if (length >= minimum) return end + (length & 1) == b.length;
            position = (int) (end + (length & 1));
        }
        return false;
    }

    /** 解析 WAV RIFF chunks，要求 fmt 結構與非空 data chunk。 */
    private boolean wav(byte[] b) {
        if (!riffExact(b, "WAVE")) return false;
        int position = 12;
        boolean format = false, data = false;
        while (position + 8 <= b.length) {
            String type = asciiValue(b, position, 4);
            long length = uint32le(b, position + 4);
            long end = position + 8L + length;
            if (end > b.length) return false;
            if ("fmt ".equals(type)) format = length >= 16;
            if ("data".equals(type)) data = length > 0;
            position = (int) (end + (length & 1));
        }
        return format && data && position == b.length;
    }

    /** 驗證 RIFF 宣告大小必須與實際 bytes 完全一致。 */
    private boolean riffExact(byte[] b, String type) {
        return b.length >= 12 && ascii(b, 0, "RIFF") && ascii(b, 8, type) && uint32le(b, 4) + 8L == b.length;
    }

    /** 解析 ISO BMFF box，要求有效 ftyp、非空 moov 與非空 mdat。 */
    private boolean m4a(byte[] b) {
        int position = 0;
        boolean ftyp = false, moov = false, mdat = false;
        while (position + 8 <= b.length) {
            long size = uint32be(b, position);
            String type = asciiValue(b, position + 4, 4);
            if (size < 8 || size > Integer.MAX_VALUE || position + size > b.length) return false;
            int payloadLength = (int) size - 8;
            if ("ftyp".equals(type)) {
                if (payloadLength < 8) return false;
                String brand = asciiValue(b, position + 8, 4);
                ftyp = Set.of("M4A ", "M4B ", "isom", "mp41", "mp42", "qt  ").contains(brand);
            } else if ("moov".equals(type)) moov = payloadLength > 0;
            else if ("mdat".equals(type)) mdat = payloadLength > 0;
            position += (int) size;
        }
        return position == b.length && ftyp && moov && mdat;
    }

    /** 解析選填 ID3 後的 MPEG-1 Layer III frame，要求完整 frame payload。 */
    private boolean mp3(byte[] b) {
        int offset = 0;
        if (b.length >= 10 && ascii(b, 0, "ID3")) {
            for (int i = 6; i < 10; i++) if ((u(b[i]) & 0x80) != 0) return false;
            int tagSize = (u(b[6]) << 21) | (u(b[7]) << 14) | (u(b[8]) << 7) | u(b[9]);
            offset = 10 + tagSize;
        }
        if (offset + 4 > b.length || u(b[offset]) != 0xff || (u(b[offset + 1]) & 0xfe) != 0xfa) return false;
        int bitrateIndex = (u(b[offset + 2]) >>> 4) & 0x0f;
        int sampleRateIndex = (u(b[offset + 2]) >>> 2) & 0x03;
        if (bitrateIndex == 0 || bitrateIndex == 15 || sampleRateIndex == 3) return false;
        int[] bitrates = {0,32,40,48,56,64,80,96,112,128,160,192,224,256,320};
        int[] sampleRates = {44100,48000,32000};
        int padding = (u(b[offset + 2]) >>> 1) & 1;
        int frameLength = 144 * bitrates[bitrateIndex] * 1000 / sampleRates[sampleRateIndex] + padding;
        return frameLength > 4 && offset + frameLength <= b.length;
    }

    /** 讀取 big-endian unsigned 32-bit。 */
    private long uint32be(byte[] b, int offset) { return Integer.toUnsignedLong(ByteBuffer.wrap(b, offset, 4).getInt()); }
    /** 讀取 little-endian unsigned 32-bit。 */
    private long uint32le(byte[] b, int offset) { return Integer.toUnsignedLong(ByteBuffer.wrap(b, offset, 4).order(ByteOrder.LITTLE_ENDIAN).getInt()); }
    /** 取得固定長度 ASCII 值。 */
    private String asciiValue(byte[] b, int offset, int length) { return new String(b, offset, length, java.nio.charset.StandardCharsets.US_ASCII); }
    /** 比對固定 ASCII signature。 */
    private boolean ascii(byte[] b, int offset, String value) {
        if (offset + value.length() > b.length) return false;
        for (int i = 0; i < value.length(); i++) if (b[offset + i] != (byte) value.charAt(i)) return false;
        return true;
    }
    /** 將 signed byte 轉為 unsigned 整數。 */
    private int u(byte value) { return value & 0xff; }
}
