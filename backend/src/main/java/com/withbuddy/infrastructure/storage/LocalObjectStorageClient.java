package com.withbuddy.infrastructure.storage;

import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
@ConditionalOnProperty(prefix = "app.storage", name = "provider", havingValue = "local", matchIfMissing = true)
public class LocalObjectStorageClient implements ObjectStorageClient {

    private final StorageProperties storageProperties;

    public LocalObjectStorageClient(StorageProperties storageProperties) {
        this.storageProperties = storageProperties;
    }

    @Override
    public void putObject(String namespace, String bucket, String objectKey, byte[] payload) {
        Path path = resolvePath(namespace, bucket, objectKey);
        try {
            Files.createDirectories(path.getParent());
            Files.write(path, payload);
        } catch (IOException e) {
            throw new IllegalStateException("로컬 스토리지 파일 저장에 실패했습니다.", e);
        }
    }

    @Override
    public void deleteObject(String namespace, String bucket, String objectKey) {
        Path path = resolvePath(namespace, bucket, objectKey);
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            throw new IllegalStateException("로컬 스토리지 파일 삭제에 실패했습니다.", e);
        }
    }

    @Override
    public boolean exists(String namespace, String bucket, String objectKey) {
        return Files.exists(resolvePath(namespace, bucket, objectKey));
    }

    @Override
    public byte[] getObject(String namespace, String bucket, String objectKey) {
        Path path = resolvePath(namespace, bucket, objectKey);
        try {
            return Files.readAllBytes(path);
        } catch (IOException e) {
            throw new IllegalStateException("로컬 스토리지 파일 조회에 실패했습니다.", e);
        }
    }

    @Override
    public String createPreSignedGetUrl(String namespace, String bucket, String objectKey, int expiresInSeconds) {
        return "";
    }

    private Path resolvePath(String namespace, String bucket, String objectKey) {
        String sanitizedKey = objectKey.replace("..", "").replace("\\", "/");
        return Path.of(storageProperties.getLocalBaseDir(), namespace, bucket, sanitizedKey);
    }
}
