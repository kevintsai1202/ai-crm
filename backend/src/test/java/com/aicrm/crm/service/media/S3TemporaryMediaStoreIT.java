package com.aicrm.crm.service.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

class S3TemporaryMediaStoreIT {
    private static final GenericContainer<?> minio = new GenericContainer<>(DockerImageName.parse("minio/minio:RELEASE.2025-04-22T22-12-26Z"))
            .withEnv("MINIO_ROOT_USER", "minioadmin")
            .withEnv("MINIO_ROOT_PASSWORD", "minioadmin")
            .withCommand("server", "/data")
            .withExposedPorts(9000);
    private static S3TemporaryMediaStore store;

    @BeforeAll
    static void startMinio() {
        minio.start();
        store = new S3TemporaryMediaStore("http://" + minio.getHost() + ":" + minio.getMappedPort(9000),
                "minioadmin", "minioadmin", "temporary-media-test", "us-east-1");
    }

    @AfterAll
    static void stopMinio() {
        if (store != null) store.close();
        minio.stop();
    }

    @Test
    void putGetDelete_roundTripsAgainstRealMinio_andDeleteIsIdempotent() throws Exception {
        byte[] bytes = "real-minio-content".getBytes(StandardCharsets.UTF_8);
        StoredMedia stored = store.put(new MediaUpload("customer-card.png", "image/png", bytes));

        try (var input = store.get(stored.objectKey())) {
            assertThat(input.readAllBytes()).isEqualTo(bytes);
        }
        assertThat(stored.objectKey()).doesNotContain("customer-card").doesNotContain("minioadmin");
        store.delete(stored.objectKey());
        assertThatCode(() -> store.delete(stored.objectKey())).doesNotThrowAnyException();
    }
}
