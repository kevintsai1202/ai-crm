package com.aicrm.crm.service.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

import com.aicrm.crm.domain.MediaPurpose;
import com.aicrm.crm.domain.MediaStatus;
import com.aicrm.crm.domain.TemporaryMedia;
import com.aicrm.crm.repository.TemporaryMediaRepository;
import com.aicrm.crm.service.JwtService.AuthPrincipal;
import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.zip.CRC32;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;

class TemporaryMediaServiceTest {
    private final TemporaryMediaStore store = mock(TemporaryMediaStore.class);
    private final TemporaryMediaRepository repository = mock(TemporaryMediaRepository.class);
    /** 這些單元測試不觸發 afterCommit 刪除路徑，交易管理器僅需一個佔位 mock。 */
    private final org.springframework.transaction.PlatformTransactionManager txManager =
            mock(org.springframework.transaction.PlatformTransactionManager.class);
    @TempDir
    Path testDirectory;
    private MediaTempFileManager tempFiles;
    private TemporaryMediaService service;
    private final AuthPrincipal principal = new AuthPrincipal("sales@example.com", "業務", com.aicrm.crm.domain.Role.SALES);

    @BeforeEach
    void setUpTempFiles() throws IOException {
        tempFiles = new MediaTempFileManager(testDirectory.resolve("media"), Duration.ofHours(24));
        tempFiles.initialize();
        service = new TemporaryMediaService(store, repository, Duration.ofHours(24), tempFiles, 25_000_000L, txManager);
    }

    @Test
    void stage_acceptsRealImageAndRejectsSpoofedOrOversizedFiles() {
        byte[] png = png();
        when(store.put(any())).thenReturn(new StoredMedia("media/random-key", png.length, "hash"));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        TemporaryMedia staged = service.stage(new MockMultipartFile("file", "card.png", "image/png", png), MediaPurpose.BUSINESS_CARD, principal);

        assertThat(staged.getStatus()).isEqualTo(MediaStatus.UPLOADED);
        assertThat(staged.getObjectKey()).isEqualTo("media/random-key");
        assertThatThrownBy(() -> service.stage(new MockMultipartFile("file", "fake.png", "image/png", "not-png".getBytes()), MediaPurpose.BUSINESS_CARD, principal))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.stage(file("header-only.png", "image/png", new byte[] {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10}), MediaPurpose.BUSINESS_CARD, principal))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.stage(new MockMultipartFile("file", "huge.png", "image/png", new byte[10 * 1024 * 1024 + 1]), MediaPurpose.BUSINESS_CARD, principal))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void stage_rejectsLongHeaderSpoofsAndImageDimensionBomb() {
        byte[] fakeMp3 = new byte[16_000];
        fakeMp3[0] = (byte) 0xff; fakeMp3[1] = (byte) 0xfb; fakeMp3[2] = (byte) 0x90;
        byte[] fakeM4a = new byte[16_000];
        System.arraycopy(new byte[] {0,0,0,16,'f','t','y','p','M','4','A',' ',0,0,0,0}, 0, fakeM4a, 0, 16);

        assertThatThrownBy(() -> service.stage(file("long-spoof.mp3", "audio/mpeg", fakeMp3), MediaPurpose.MEETING_AUDIO, principal))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.stage(file("long-spoof.m4a", "audio/mp4", fakeM4a), MediaPurpose.MEETING_AUDIO, principal))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.stage(file("bomb.png", "image/png", dimensionBombPng()), MediaPurpose.BUSINESS_CARD, principal))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void stage_enforcesConfiguredPixelLimitBeforeDecode() {
        byte[] twoByTwo = png();
        TemporaryMediaService exactLimit = new TemporaryMediaService(store, repository, Duration.ofHours(24), tempFiles, 4L, txManager);
        when(store.put(any())).thenReturn(new StoredMedia("media/exact-pixels", twoByTwo.length, "hash"));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(exactLimit.stage(file("exact.png", "image/png", twoByTwo), MediaPurpose.BUSINESS_CARD, principal))
                .isNotNull();
        TemporaryMediaService belowLimit = new TemporaryMediaService(store, repository, Duration.ofHours(24), tempFiles, 3L, txManager);
        assertThatThrownBy(() -> belowLimit.stage(file("over.png", "image/png", twoByTwo), MediaPurpose.BUSINESS_CARD, principal))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void stage_enforcesActualImageAndAudioByteBoundaries() {
        byte[] imageAtLimit = Arrays.copyOf(png(), 10 * 1024 * 1024);
        assertAccepted(MediaPurpose.BUSINESS_CARD, "image/png", imageAtLimit);
        byte[] imageOverLimit = Arrays.copyOf(imageAtLimit, imageAtLimit.length + 1);
        imageAtLimit = null;
        assertThatThrownBy(() -> service.stage(file("over.png", "image/png", imageOverLimit),
                MediaPurpose.BUSINESS_CARD, principal)).isInstanceOf(IllegalArgumentException.class);

        byte[] audioAtLimit = wavExactSize(100 * 1024 * 1024);
        assertAccepted(MediaPurpose.MEETING_AUDIO, "audio/wav", audioAtLimit);
        audioAtLimit = null;
        byte[] audioOverLimit = new byte[100 * 1024 * 1024 + 1];
        assertThatThrownBy(() -> service.stage(file("over.wav", "audio/wav", audioOverLimit),
                MediaPurpose.MEETING_AUDIO, principal)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void stage_failsClosedAndRegistersRetryWhenTempDeleteFails() throws IOException {
        MediaTempFileManager failingManager = spy(new MediaTempFileManager(testDirectory.resolve("failure"), Duration.ofHours(24)));
        failingManager.initialize();
        doThrow(new IOException("locked")).doCallRealMethod().when(failingManager).delete(any());
        TemporaryMediaService guarded = new TemporaryMediaService(store, repository, Duration.ofHours(24), failingManager, 25_000_000L, txManager);

        assertThatThrownBy(() -> guarded.stage(file("meeting.wav", "audio/wav", wav()), MediaPurpose.MEETING_AUDIO, principal))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("音訊驗證暫存檔無法安全刪除");
        verify(store, never()).put(any());
        assertThat(failingManager.pendingCount()).isEqualTo(1);
        assertThat(guarded.retryPendingTempFiles()).isEqualTo(1);
        assertThat(failingManager.pendingCount()).isZero();
    }

    @Test
    void stage_successfulAudioLeavesNoOwnedOrPendingTempFile() {
        assertAccepted(MediaPurpose.MEETING_AUDIO, "audio/wav", wav());

        assertThat(tempFiles.ownedCount()).isZero();
        assertThat(tempFiles.pendingCount()).isZero();
        assertThat(tempFiles.tempDirectory().toFile().list()).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("validFormats")
    void stage_acceptsStructurallyValidFormats(MediaPurpose purpose, String mime, byte[] bytes) {
        assertAccepted(purpose, mime, bytes);
    }

    @ParameterizedTest
    @MethodSource("spoofedFormats")
    void stage_rejectsHeaderOnlyAndShortFiles(MediaPurpose purpose, String mime, byte[] bytes) {
        assertThatThrownBy(() -> service.stage(file("spoof", mime, bytes), purpose, principal))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void stage_enforcesPurposeLimitsUsingDeclaredAndActualSize() throws IOException {
        byte[] imageAtLimit = png();
        assertAccepted(MediaPurpose.BUSINESS_CARD, "image/png", imageAtLimit);
        MultipartFile declaredTooLarge = mock(MultipartFile.class);
        when(declaredTooLarge.getSize()).thenReturn(10L * 1024 * 1024 + 1);
        assertThatThrownBy(() -> service.stage(declaredTooLarge, MediaPurpose.BUSINESS_CARD, principal))
                .isInstanceOf(IllegalArgumentException.class);
        verify(declaredTooLarge, never()).getBytes();

        byte[] audioAtLimit = wav();
        assertAccepted(MediaPurpose.MEETING_AUDIO, "audio/wav", audioAtLimit);
        byte[] audioOverLimit = new byte[100 * 1024 * 1024 + 1];
        MultipartFile understated = mock(MultipartFile.class);
        when(understated.getSize()).thenReturn(1L);
        when(understated.getContentType()).thenReturn("audio/wav");
        when(understated.getOriginalFilename()).thenReturn("understated.wav");
        when(understated.getBytes()).thenReturn(audioOverLimit);
        assertThatThrownBy(() -> service.stage(understated, MediaPurpose.MEETING_AUDIO, principal))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void stage_compensatesObjectWhenMetadataSaveFails() {
        byte[] png = png();
        when(store.put(any())).thenReturn(new StoredMedia("media/orphan", png.length, "hash"));
        when(repository.save(any())).thenThrow(new IllegalStateException("db unavailable"));

        assertThatThrownBy(() -> service.stage(file("card.png", "image/png", png), MediaPurpose.BUSINESS_CARD, principal))
                .isInstanceOf(IllegalStateException.class);
        verify(store).delete("media/orphan");
    }

    @Test
    void deleteExpired_onlyDeletesEligibleRowsAndKeepsMetadataAfterObjectFailure() {
        Instant now = Instant.parse("2026-07-15T00:00:00Z");
        TemporaryMedia success = TemporaryMedia.restoreForCleanup(1L, "media/one", MediaStatus.UPLOADED, now.minusSeconds(1));
        TemporaryMedia failedObject = TemporaryMedia.restoreForCleanup(2L, "media/two", MediaStatus.FAILED, now.minusSeconds(1));
        when(repository.findCleanupCandidates(now, List.of(MediaStatus.UPLOADED, MediaStatus.REVIEW_PENDING, MediaStatus.FAILED, MediaStatus.DELETE_PENDING)))
                .thenReturn(List.of(success, failedObject));
        doThrow(new IllegalStateException("s3 down")).when(store).delete("media/two");

        assertThat(service.deleteExpired(now)).isEqualTo(1);
        verify(repository).delete(success);
        verify(repository, never()).delete(failedObject);
    }

    @Test
    void deleteExpired_retriesAfterDatabaseDeleteFailureAndContinuesOtherRows() {
        Instant now = Instant.parse("2026-07-15T00:00:00Z");
        TemporaryMedia retry = TemporaryMedia.restoreForCleanup(1L, "media/retry", MediaStatus.REVIEW_PENDING, now);
        TemporaryMedia later = TemporaryMedia.restoreForCleanup(2L, "media/later", MediaStatus.FAILED, now.minusSeconds(1));
        List<MediaStatus> eligible = List.of(MediaStatus.UPLOADED, MediaStatus.REVIEW_PENDING, MediaStatus.FAILED, MediaStatus.DELETE_PENDING);
        when(repository.findCleanupCandidates(now, eligible)).thenReturn(List.of(retry, later), List.of(retry));
        doThrow(new IllegalStateException("db down")).doNothing().when(repository).delete(retry);

        assertThat(service.deleteExpired(now)).isEqualTo(1);
        verify(repository).delete(later);
        assertThat(service.deleteExpired(now)).isEqualTo(1);
        verify(store, org.mockito.Mockito.times(2)).delete("media/retry");
        verify(repository, org.mockito.Mockito.times(2)).delete(retry);
    }

    /**
     * deleteConfirmed 以獨立交易（REQUIRES_NEW）載入受管實體並推進 DELETE_PENDING → DELETED；
     * object 刪除失敗時保留 DELETE_PENDING 供下一輪 cleanup 冪等重試。
     */
    @Test
    void deleteConfirmed_marksDeletedAndStaysPendingWhenStoreDeleteFails() {
        // 成功路徑：載入受管實體並標記為 DELETED。
        TemporaryMedia media = TemporaryMedia.restoreForCleanup(9L, "media/pending", MediaStatus.CONFIRMED, Instant.EPOCH);
        when(repository.findById(9L)).thenReturn(java.util.Optional.of(media));
        service.deleteConfirmed(media);
        assertThat(media.getStatus()).isEqualTo(MediaStatus.DELETED);
        verify(store).delete("media/pending");

        // 失敗路徑：object 刪除拋錯，狀態停在 DELETE_PENDING 供重試。
        TemporaryMedia failing = TemporaryMedia.restoreForCleanup(10L, "media/fail", MediaStatus.CONFIRMED, Instant.EPOCH);
        when(repository.findById(10L)).thenReturn(java.util.Optional.of(failing));
        doThrow(new IllegalStateException("s3 down")).when(store).delete("media/fail");
        service.deleteConfirmed(failing);
        assertThat(failing.getStatus()).isEqualTo(MediaStatus.DELETE_PENDING);

        // 後續 cleanup 重試：DELETE_PENDING 完成刪除並標記 DELETED。
        Instant now = Instant.now();
        when(repository.findCleanupCandidates(eq(now), any())).thenReturn(List.of(failing));
        doNothing().when(store).delete("media/fail");
        assertThat(service.deleteExpired(now)).isEqualTo(1);
        assertThat(failing.getStatus()).isEqualTo(MediaStatus.DELETED);
    }

    private void assertAccepted(MediaPurpose purpose, String mime, byte[] bytes) {
        when(store.put(any())).thenReturn(new StoredMedia("media/" + mime.hashCode(), bytes.length, "hash"));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        assertThat(service.stage(file("sample", mime, bytes), purpose, principal).getStatus()).isEqualTo(MediaStatus.UPLOADED);
    }

    private MockMultipartFile file(String name, String mime, byte[] bytes) {
        return new MockMultipartFile("file", name, mime, bytes);
    }

    static Stream<Arguments> validFormats() {
        TemporaryMediaServiceTest fixture = new TemporaryMediaServiceTest();
        return Stream.of(
                Arguments.of(MediaPurpose.BUSINESS_CARD, "image/jpeg", fixture.progressiveJpeg()),
                Arguments.of(MediaPurpose.BUSINESS_CARD, "image/png", fixture.png()),
                Arguments.of(MediaPurpose.BUSINESS_CARD, "image/webp", fixture.resource("synthetic-extended.webp")),
                Arguments.of(MediaPurpose.MEETING_AUDIO, "audio/mpeg", fixture.resource("synthetic-tone.mp3")),
                Arguments.of(MediaPurpose.MEETING_AUDIO, "audio/mp4", fixture.resource("synthetic-tone.m4a")),
                Arguments.of(MediaPurpose.MEETING_AUDIO, "audio/wav", fixture.wav()));
    }

    static Stream<Arguments> spoofedFormats() {
        return Stream.of(
                Arguments.of(MediaPurpose.BUSINESS_CARD, "image/jpeg", new byte[] {(byte)0xff,(byte)0xd8,(byte)0xff,(byte)0xd9}),
                Arguments.of(MediaPurpose.BUSINESS_CARD, "image/png", new byte[] {(byte)0x89,'P','N','G',13,10,26,10}),
                Arguments.of(MediaPurpose.BUSINESS_CARD, "image/webp", new byte[] {'R','I','F','F',4,0,0,0,'W','E','B','P'}),
                Arguments.of(MediaPurpose.MEETING_AUDIO, "audio/mpeg", new byte[] {(byte)0xff,(byte)0xfb,(byte)0x90,0}),
                Arguments.of(MediaPurpose.MEETING_AUDIO, "audio/mp4", new byte[] {0,0,0,12,'f','t','y','p','M','4','A',' '}),
                Arguments.of(MediaPurpose.MEETING_AUDIO, "audio/wav", new byte[] {'R','I','F','F',4,0,0,0,'W','A','V','E'}));
    }

    /** 以 AudioSystem 產生一秒 PCM WAV，確保 fixture 可被真實音訊 parser 讀取。 */
    private byte[] wav() {
        try {
            AudioFormat format = new AudioFormat(8000, 16, 1, true, false);
            byte[] pcm = new byte[16000];
            try (AudioInputStream input = new AudioInputStream(new ByteArrayInputStream(pcm), format, 8000);
                    ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                AudioSystem.write(input, AudioFileFormat.Type.WAVE, output);
                return output.toByteArray();
            }
        } catch (IOException exception) { throw new IllegalStateException(exception); }
    }

    /** 產生 RIFF/data 長度完全吻合的零 PCM WAV，用於實際 100MB 邊界驗證。 */
    private byte[] wavExactSize(int size) {
        byte[] bytes = new byte[size];
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        buffer.put(new byte[] {'R','I','F','F'}).putInt(size - 8).put(new byte[] {'W','A','V','E'});
        buffer.put(new byte[] {'f','m','t',' '}).putInt(16).putShort((short) 1).putShort((short) 1)
                .putInt(8000).putInt(16000).putShort((short) 2).putShort((short) 16);
        buffer.put(new byte[] {'d','a','t','a'}).putInt(size - 44);
        return bytes;
    }

    /** 以 ImageIO 產生具有正確 CRC/zlib 資料的 PNG。 */
    private byte[] png() {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", output);
            return output.toByteArray();
        } catch (IOException exception) { throw new IllegalStateException(exception); }
    }

    /** 以 ImageIO JPEG writer 產生 progressive JPEG。 */
    private byte[] progressiveJpeg() {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        try (ByteArrayOutputStream output = new ByteArrayOutputStream(); ImageOutputStream imageOutput = ImageIO.createImageOutputStream(output)) {
            ImageWriteParam parameters = writer.getDefaultWriteParam();
            parameters.setProgressiveMode(ImageWriteParam.MODE_DEFAULT);
            writer.setOutput(imageOutput);
            writer.write(null, new javax.imageio.IIOImage(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), null, null), parameters);
            return output.toByteArray();
        } catch (IOException exception) { throw new IllegalStateException(exception); }
        finally { writer.dispose(); }
    }

    /** 讀取已簽入、來源可重現的合成媒體 fixture。 */
    private byte[] resource(String name) {
        try { return Files.readAllBytes(Path.of("src/test/resources/media", name)); }
        catch (IOException exception) { throw new IllegalStateException(exception); }
    }

    /** 建立超過一億像素且 IHDR CRC 正確的 PNG，驗證解碼前像素防護。 */
    private byte[] dimensionBombPng() {
        byte[] bytes = png();
        ByteBuffer.wrap(bytes, 16, 8).putInt(10_001).putInt(10_000);
        CRC32 crc = new CRC32();
        crc.update(bytes, 12, 17);
        ByteBuffer.wrap(bytes, 29, 4).putInt((int) crc.getValue());
        return Arrays.copyOf(bytes, bytes.length);
    }
}
