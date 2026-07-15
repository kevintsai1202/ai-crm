package com.aicrm.crm.service.media;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.BucketAlreadyExistsException;
import software.amazon.awssdk.services.s3.model.BucketAlreadyOwnedByYouException;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

class S3TemporaryMediaStoreTest {
    @Test
    void bootstrap_doesNotSwallowBucketOwnedByAnotherAccount() {
        S3Client client = mock(S3Client.class);
        when(client.headBucket(any(HeadBucketRequest.class))).thenThrow(notFound());
        when(client.createBucket(any(CreateBucketRequest.class))).thenThrow(
                BucketAlreadyExistsException.builder().message("owned by another account").build());

        assertThatThrownBy(() -> new S3TemporaryMediaStore(client, "shared-name"))
                .isInstanceOf(BucketAlreadyExistsException.class);
    }

    @Test
    void bootstrap_rechecksAccessAfterSameAccountCreationRace() {
        S3Client client = mock(S3Client.class);
        when(client.headBucket(any(HeadBucketRequest.class))).thenThrow(notFound())
                .thenReturn(HeadBucketResponse.builder().build());
        when(client.createBucket(any(CreateBucketRequest.class))).thenThrow(
                BucketAlreadyOwnedByYouException.builder().message("race").build());

        new S3TemporaryMediaStore(client, "shared-name");

        verify(client, times(2)).headBucket(any(HeadBucketRequest.class));
    }

    /** 建立 S3 head bucket 404 例外。 */
    private S3Exception notFound() {
        return (S3Exception) S3Exception.builder().statusCode(404).message("not found").build();
    }
}
