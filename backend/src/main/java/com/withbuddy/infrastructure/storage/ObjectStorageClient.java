package com.withbuddy.infrastructure.storage;

public interface ObjectStorageClient {

    void putObject(String namespace, String bucket, String objectKey, byte[] payload);

    void deleteObject(String namespace, String bucket, String objectKey);

    boolean exists(String namespace, String bucket, String objectKey);

    byte[] getObject(String namespace, String bucket, String objectKey);
}
