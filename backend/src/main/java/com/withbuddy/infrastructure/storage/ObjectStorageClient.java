package com.withbuddy.infrastructure.storage;

public interface ObjectStorageClient {

    default boolean supportsPreSignedGetUrl() {
        return true;
    }

    void putObject(String namespace, String bucket, String objectKey, byte[] payload);

    void deleteObject(String namespace, String bucket, String objectKey);

    boolean exists(String namespace, String bucket, String objectKey);

    byte[] getObject(String namespace, String bucket, String objectKey);

    String createPreSignedGetUrl(String namespace, String bucket, String objectKey, int expiresInSeconds);

    default String createPreSignedGetUrl(
            String namespace,
            String bucket,
            String objectKey,
            int expiresInSeconds,
            String downloadFileName
    ) {
        return createPreSignedGetUrl(namespace, bucket, objectKey, expiresInSeconds);
    }
}
