package com.aicrm.crm.service.media;

import com.aicrm.crm.domain.MediaPurpose;
import com.aicrm.crm.domain.MediaStatus;
import com.aicrm.crm.domain.TemporaryMedia;
import com.aicrm.crm.repository.TemporaryMediaRepository;
import com.aicrm.crm.service.JwtService.AuthPrincipal;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.audio.AudioHeader;
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
    /** 媒體驗證暫存檔管理器。 */
    private final MediaTempFileManager tempFiles;
    /** 單張圖片可解碼的最大像素數。 */
    private final long imageMaxPixels;

    /** 建立媒體服務。 */
    public TemporaryMediaService(TemporaryMediaStore store, TemporaryMediaRepository repository,
            @Value("${app.media.retention:PT24H}") Duration retention, MediaTempFileManager tempFiles,
            @Value("${app.media.image.max-pixels:25000000}") long imageMaxPixels) {
        this.store = store;
        this.repository = repository;
        this.retention = retention;
        this.tempFiles = tempFiles;
        if (imageMaxPixels <= 0) throw new IllegalArgumentException("圖片最大像素數必須大於 0");
        this.imageMaxPixels = imageMaxPixels;
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
            try { store.delete(stored.objectKey()); } catch (RuntimeException cleanupFailure) { exception.addSuppressed(cleanupFailure); }
            throw exception;
        }
    }

    /** 先用 multipart 宣告大小阻擋明顯超限內容。 */
    private void rejectDeclaredOversize(MultipartFile file, MediaPurpose purpose) {
        if (file == null) throw new IllegalArgumentException("媒體檔案不可為空");
        long max = purpose == MediaPurpose.BUSINESS_CARD ? IMAGE_MAX : AUDIO_MAX;
        if (file.getSize() > max) throw new IllegalArgumentException("媒體檔案超過大小限制");
    }

    /** 逐筆刪除到期物件，失敗時保留 metadata 供下次重試。 */
    public int deleteExpired(Instant now) {
        int deleted = 0;
        List<TemporaryMedia> candidates = repository.findCleanupCandidates(
                now, List.of(MediaStatus.UPLOADED, MediaStatus.REVIEW_PENDING, MediaStatus.FAILED));
        for (TemporaryMedia media : candidates) {
            try { store.delete(media.getObjectKey()); repository.delete(media); deleted++; }
            catch (RuntimeException exception) { log.warn("暫存媒體清理失敗，保留 metadata 供下次重試：mediaId={}", media.getId(), exception); }
        }
        return deleted;
    }

    /** 供既有 cleanup job 每輪重試刪除媒體解析暫存檔。 */
    public int retryPendingTempFiles() { return tempFiles.retryPending(); }

    /** 一次讀取 multipart 並將 IO 錯誤轉為輸入驗證錯誤。 */
    private byte[] readBytes(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("媒體檔案不可為空");
        try { return file.getBytes(); } catch (IOException exception) { throw new IllegalArgumentException("無法讀取媒體檔案", exception); }
    }

    /** 正規化 MIME。 */
    private String normalizeMime(String mime) { return mime == null ? "" : mime.toLowerCase(Locale.ROOT).trim(); }

    /** 避免空檔名與過長顯示名稱。 */
    private String safeFilename(String filename) {
        String value = filename == null || filename.isBlank() ? "upload" : filename.trim();
        return value.length() <= 255 ? value : value.substring(value.length() - 255);
    }

    /** 依用途驗證白名單、大小、magic bytes 與成熟解碼器結果。 */
    private void validate(byte[] bytes, String mime, MediaPurpose purpose) {
        boolean image = purpose == MediaPurpose.BUSINESS_CARD;
        if (!(image ? IMAGE_MIMES : AUDIO_MIMES).contains(mime)) throw new IllegalArgumentException("不支援的媒體 MIME 類型");
        if (bytes.length > (image ? IMAGE_MAX : AUDIO_MAX)) throw new IllegalArgumentException("媒體檔案超過大小限制");
        boolean valid = image ? hasImageMagic(bytes, mime) && decodeImage(bytes) : hasAudioMagic(bytes, mime) && parseAudio(bytes, mime);
        if (!valid) throw new IllegalArgumentException("媒體內容與 MIME 不符");
    }

    /** 僅以標準檔頭預篩圖片 MIME，完整結構交由 ImageIO 驗證。 */
    private boolean hasImageMagic(byte[] b, String mime) {
        return switch (mime) {
            case "image/jpeg" -> b.length >= 3 && u(b[0]) == 0xff && u(b[1]) == 0xd8 && u(b[2]) == 0xff;
            case "image/png" -> b.length >= 8 && u(b[0]) == 0x89 && ascii(b, 1, "PNG") && b[4] == 13 && b[5] == 10 && b[6] == 26 && b[7] == 10;
            case "image/webp" -> b.length >= 12 && ascii(b, 0, "RIFF") && ascii(b, 8, "WEBP");
            default -> false;
        };
    }

    /** 先讀尺寸防止解壓炸彈，再要求 ImageIO 真正解碼出非空影像。 */
    private boolean decodeImage(byte[] bytes) {
        try (ImageInputStream input = ImageIO.createImageInputStream(new java.io.ByteArrayInputStream(bytes))) {
            if (input == null) return false;
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) return false;
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || (long) width * (long) height > imageMaxPixels) return false;
                BufferedImage decoded = reader.read(0);
                return decoded != null && decoded.getWidth() > 0 && decoded.getHeight() > 0;
            } finally { reader.dispose(); }
        } catch (IOException | RuntimeException exception) { return false; }
    }

    /** 僅以標準檔頭預篩音訊 MIME，完整結構交由 jaudiotagger 驗證。 */
    private boolean hasAudioMagic(byte[] b, String mime) {
        return switch (mime) {
            case "audio/mpeg", "audio/mp3" -> b.length >= 3 && (ascii(b, 0, "ID3") || u(b[0]) == 0xff && (u(b[1]) & 0xe0) == 0xe0);
            case "audio/mp4", "audio/x-m4a" -> b.length >= 12 && ascii(b, 4, "ftyp");
            case "audio/wav", "audio/x-wav" -> b.length >= 12 && ascii(b, 0, "RIFF") && ascii(b, 8, "WAVE");
            default -> false;
        };
    }

    /** 將音訊寫入暫存檔，以成熟 parser 確認存在可播放的實際音軌，並保證刪除暫存檔。 */
    private boolean parseAudio(byte[] bytes, String mime) {
        Path temporary = null;
        try {
            temporary = tempFiles.create(suffix(mime));
            tempFiles.write(temporary, bytes);
            AudioFile audio = AudioFileIO.read(temporary.toFile());
            AudioHeader header = audio.getAudioHeader();
            return header != null && header.getPreciseTrackLength() > 0
                    && header.getSampleRateAsNumber() > 0 && header.getBitRateAsNumber() > 0
                    && (header.getAudioDataLength() != null && header.getAudioDataLength() > 0
                            || header.getNoOfSamples() != null && header.getNoOfSamples() > 0);
        } catch (Exception exception) {
            return false;
        } finally {
            if (temporary != null) try { tempFiles.delete(temporary); }
            catch (IOException | RuntimeException exception) {
                tempFiles.registerRetry(temporary);
                throw new IllegalArgumentException("音訊驗證暫存檔無法安全刪除", exception);
            }
        }
    }

    /** 依 MIME 提供 parser 可辨識的暫存副檔名。 */
    private String suffix(String mime) {
        if (mime.contains("mpeg") || mime.equals("audio/mp3")) return ".mp3";
        if (mime.contains("mp4") || mime.contains("m4a")) return ".m4a";
        return ".wav";
    }

    /** 比對固定 ASCII signature。 */
    private boolean ascii(byte[] b, int offset, String value) {
        if (offset + value.length() > b.length) return false;
        for (int i = 0; i < value.length(); i++) if (b[offset + i] != (byte) value.charAt(i)) return false;
        return true;
    }

    /** 將 signed byte 轉為 unsigned整數。 */
    private int u(byte value) { return value & 0xff; }
}
