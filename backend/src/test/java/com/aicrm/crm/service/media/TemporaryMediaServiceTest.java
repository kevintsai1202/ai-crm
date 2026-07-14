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
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

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

    @Test
    void stage_validatesWebpM4aWavAndMp3MagicBytes() {
        assertAccepted(MediaPurpose.BUSINESS_CARD, "image/webp", webp());
        assertAccepted(MediaPurpose.BUSINESS_CARD, "image/jpeg", jpeg());
        assertAccepted(MediaPurpose.MEETING_AUDIO, "audio/mp4", m4a());
        assertAccepted(MediaPurpose.MEETING_AUDIO, "audio/wav", wav());
        assertAccepted(MediaPurpose.MEETING_AUDIO, "audio/mpeg", new byte[] {(byte) 0xff, (byte) 0xfb, 0x10, 0x00});
        assertThatThrownBy(() -> service.stage(file("bad.m4a", "audio/mp4", new byte[] {0, 0, 0, 8, 'f', 't', 'y', 'p'}), MediaPurpose.MEETING_AUDIO, principal))
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

    private void assertAccepted(MediaPurpose purpose, String mime, byte[] bytes) {
        when(store.put(any())).thenReturn(new StoredMedia("media/" + mime.hashCode(), bytes.length, "hash"));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        assertThat(service.stage(file("sample", mime, bytes), purpose, principal).getStatus()).isEqualTo(MediaStatus.UPLOADED);
    }

    private MockMultipartFile file(String name, String mime, byte[] bytes) {
        return new MockMultipartFile("file", name, mime, bytes);
    }

    private byte[] webp() {
        return new byte[] {'R','I','F','F',8,0,0,0,'W','E','B','P','V','P','8','L'};
    }

    private byte[] m4a() {
        return new byte[] {0,0,0,16,'f','t','y','p','M','4','A',' ',0,0,0,0};
    }

    private byte[] wav() {
        byte[] bytes = new byte[44];
        byte[] header = new byte[] {'R','I','F','F',36,0,0,0,'W','A','V','E','f','m','t',' ',16,0,0,0,1,0,1,0};
        System.arraycopy(header, 0, bytes, 0, header.length);
        return bytes;
    }

    private byte[] png() {
        return new byte[] {(byte)0x89,'P','N','G',13,10,26,10,0,0,0,13,'I','H','D','R',0,0,0,1,0,0,0,1};
    }

    private byte[] jpeg() {
        return new byte[] {(byte)0xff,(byte)0xd8,(byte)0xff,(byte)0xe0,0,2,(byte)0xff,(byte)0xd9};
    }
}
