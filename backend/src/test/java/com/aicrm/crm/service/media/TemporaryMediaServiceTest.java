package com.aicrm.crm.service.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aicrm.crm.domain.MediaPurpose;
import com.aicrm.crm.domain.MediaStatus;
import com.aicrm.crm.domain.TemporaryMedia;
import com.aicrm.crm.repository.TemporaryMediaRepository;
import com.aicrm.crm.service.JwtService.AuthPrincipal;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

class TemporaryMediaServiceTest {
    private final TemporaryMediaStore store = mock(TemporaryMediaStore.class);
    private final TemporaryMediaRepository repository = mock(TemporaryMediaRepository.class);
    private final TemporaryMediaService service = new TemporaryMediaService(store, repository, Duration.ofHours(24));
    private final AuthPrincipal principal = new AuthPrincipal("sales@example.com", "業務", com.aicrm.crm.domain.Role.SALES);

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
        byte[] imageAtLimit = png(10 * 1024 * 1024);
        assertAccepted(MediaPurpose.BUSINESS_CARD, "image/png", imageAtLimit);
        MultipartFile declaredTooLarge = mock(MultipartFile.class);
        when(declaredTooLarge.getSize()).thenReturn(10L * 1024 * 1024 + 1);
        assertThatThrownBy(() -> service.stage(declaredTooLarge, MediaPurpose.BUSINESS_CARD, principal))
                .isInstanceOf(IllegalArgumentException.class);
        verify(declaredTooLarge, never()).getBytes();

        byte[] audioAtLimit = wav(100 * 1024 * 1024);
        assertAccepted(MediaPurpose.MEETING_AUDIO, "audio/wav", audioAtLimit);
        byte[] audioOverLimit = wav(100 * 1024 * 1024 + 1);
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
        when(repository.findCleanupCandidates(now, List.of(MediaStatus.UPLOADED, MediaStatus.REVIEW_PENDING, MediaStatus.FAILED)))
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
        List<MediaStatus> eligible = List.of(MediaStatus.UPLOADED, MediaStatus.REVIEW_PENDING, MediaStatus.FAILED);
        when(repository.findCleanupCandidates(now, eligible)).thenReturn(List.of(retry, later), List.of(retry));
        doThrow(new IllegalStateException("db down")).doNothing().when(repository).delete(retry);

        assertThat(service.deleteExpired(now)).isEqualTo(1);
        verify(repository).delete(later);
        assertThat(service.deleteExpired(now)).isEqualTo(1);
        verify(store, org.mockito.Mockito.times(2)).delete("media/retry");
        verify(repository, org.mockito.Mockito.times(2)).delete(retry);
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
                Arguments.of(MediaPurpose.BUSINESS_CARD, "image/jpeg", fixture.jpeg()),
                Arguments.of(MediaPurpose.BUSINESS_CARD, "image/png", fixture.png()),
                Arguments.of(MediaPurpose.BUSINESS_CARD, "image/webp", fixture.webp()),
                Arguments.of(MediaPurpose.MEETING_AUDIO, "audio/mpeg", fixture.mp3()),
                Arguments.of(MediaPurpose.MEETING_AUDIO, "audio/mp4", fixture.m4a()),
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

    private byte[] webp() {
        return new byte[] {'R','I','F','F',18,0,0,0,'W','E','B','P','V','P','8','L',5,0,0,0,1,2,3,4,5,0};
    }

    private byte[] m4a() {
        return new byte[] {0,0,0,16,'f','t','y','p','M','4','A',' ',0,0,0,0,
                0,0,0,9,'m','o','o','v',1, 0,0,0,9,'m','d','a','t',1};
    }

    private byte[] wav() {
        return wav(46);
    }

    private byte[] wav(int size) {
        byte[] bytes = new byte[size];
        byte[] header = new byte[] {'R','I','F','F',36,0,0,0,'W','A','V','E','f','m','t',' ',16,0,0,0,1,0,1,0};
        System.arraycopy(header, 0, bytes, 0, header.length);
        putLeInt(bytes, 4, size - 8);
        bytes[36]='d'; bytes[37]='a'; bytes[38]='t'; bytes[39]='a';
        putLeInt(bytes, 40, size - 44);
        bytes[44]=1;
        return bytes;
    }

    private byte[] png() {
        return png(58);
    }

    private byte[] png(int size) {
        byte[] bytes = new byte[size];
        byte[] signature = new byte[] {(byte)0x89,'P','N','G',13,10,26,10,0,0,0,13,'I','H','D','R',0,0,0,1,0,0,0,1,8,2,0,0,0,0,0,0,0};
        System.arraycopy(signature, 0, bytes, 0, signature.length);
        int idatLength = size - 57;
        putBeInt(bytes, 33, idatLength); bytes[37]='I'; bytes[38]='D'; bytes[39]='A'; bytes[40]='T'; bytes[41]=1;
        int iend = 45 + idatLength;
        bytes[iend+4]='I'; bytes[iend+5]='E'; bytes[iend+6]='N'; bytes[iend+7]='D';
        return bytes;
    }

    private byte[] jpeg() {
        return new byte[] {(byte)0xff,(byte)0xd8,(byte)0xff,(byte)0xda,0,6,1,1,0,0,1,2,3,(byte)0xff,(byte)0xd9};
    }

    private byte[] mp3() {
        byte[] bytes = new byte[417];
        bytes[0]=(byte)0xff; bytes[1]=(byte)0xfb; bytes[2]=(byte)0x90; bytes[3]=0; bytes[4]=1;
        return bytes;
    }

    private void putLeInt(byte[] bytes, int offset, int value) {
        ByteBuffer.wrap(bytes, offset, 4).order(java.nio.ByteOrder.LITTLE_ENDIAN).putInt(value);
    }

    private void putBeInt(byte[] bytes, int offset, int value) {
        ByteBuffer.wrap(bytes, offset, 4).putInt(value);
    }
}
