package com.aicrm.crm.service.media;

import jakarta.annotation.PreDestroy;
import java.io.InputStream;
import java.net.URI;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.BucketAlreadyOwnedByYouException;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

/** AWS SDK v2 S3-compatible 暫存媒體 adapter，正式環境與 MinIO 共用。 */
@Component
@ConditionalOnProperty(name = "app.media.enabled", havingValue = "true", matchIfMissing = true)
public class S3TemporaryMediaStore implements TemporaryMediaStore, AutoCloseable {
    /** 所有暫存物件共用、啟動時保證存在的 bucket。 */
    private final String bucket;
    /** 可連 S3-compatible endpoint 的同步 client。 */
    private final S3Client client;

    /** 由應用設定建立 path-style S3 client，避免 MinIO 虛擬主機解析問題。 */
    @Autowired
    public S3TemporaryMediaStore(
            @Value("${app.media.s3.endpoint}") String endpoint,
            @Value("${app.media.s3.access-key}") String accessKey,
            @Value("${app.media.s3.secret-key}") String secretKey,
            @Value("${app.media.s3.bucket:ai-crm-temporary-media}") String bucket,
            @Value("${app.media.s3.region:us-east-1}") String region) {
        this.bucket = bucket;
        this.client = S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
                .region(Region.of(region))
                .forcePathStyle(true)
                .build();
        ensureBucket();
    }

    /** 注入既有 client 以測試 bucket bootstrap 的權限與競爭分支。 */
    S3TemporaryMediaStore(S3Client client, String bucket) {
        this.client = client;
        this.bucket = bucket;
        ensureBucket();
    }

    /** 檢查 bucket，不存在時建立；多實例同時建立視為成功。 */
    private void ensureBucket() {
        try {
            client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
        } catch (NoSuchBucketException exception) {
            createBucketConcurrentlySafe();
        } catch (S3Exception exception) {
            if (exception.statusCode() == 404) {
                createBucketConcurrentlySafe();
            } else {
                throw exception;
            }
        }
    }

    /** 建立 bucket，容忍其他 instance 已先完成建立。 */
    private void createBucketConcurrentlySafe() {
        try {
            client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
        } catch (BucketAlreadyOwnedByYouException ignored) {
            // 多 instance 使用同一帳號競爭建立時，再確認目前帳號確實可存取。
            client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
        }
    }

    /** 以隨機 key 寫入媒體，key 不包含原始檔名、帳號或租戶資訊。 */
    @Override
    public StoredMedia put(MediaUpload upload) {
        String objectKey = "temporary/" + UUID.randomUUID();
        client.putObject(PutObjectRequest.builder()
                        .bucket(bucket).key(objectKey).contentType(upload.contentType()).build(),
                RequestBody.fromBytes(upload.bytes()));
        return new StoredMedia(objectKey, upload.bytes().length, sha256(upload.bytes()));
    }

    /** 取得由呼叫端負責關閉的 S3 response stream。 */
    @Override
    public InputStream get(String objectKey) {
        return client.getObject(GetObjectRequest.builder().bucket(bucket).key(objectKey).build());
    }

    /** 冪等刪除物件；S3 delete 本身對不存在 key 視為成功。 */
    @Override
    public void delete(String objectKey) {
        try {
            client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(objectKey).build());
        } catch (NoSuchKeyException ignored) {
            // 冪等重試：物件已不存在即完成目標。
        }
    }

    /** 計算媒體內容 SHA-256 hex。 */
    private String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JVM 不支援 SHA-256", exception);
        }
    }

    /** 關閉 AWS client 及其連線資源。 */
    @Override
    @PreDestroy
    public void close() {
        client.close();
    }
}
