package com.withbuddy.infrastructure.storage;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Component
@ConditionalOnProperty(prefix = "app.storage", name = "provider", havingValue = "oci-cli")
public class OciCliObjectStorageClient implements ObjectStorageClient {

    private final StorageProperties storageProperties;

    public OciCliObjectStorageClient(StorageProperties storageProperties) {
        this.storageProperties = storageProperties;
    }

    @Override
    public void putObject(String namespace, String bucket, String objectKey, byte[] payload) {
        Path tempFile = null;
        try {
            tempFile = Files.createTempFile("withbuddy-upload-", ".bin");
            Files.write(tempFile, payload);

            String profile = resolveProfile(namespace, bucket);
            ExecResult result = exec(List.of(
                    storageProperties.getOciCli().getExecutable(),
                    "os", "object", "put",
                    "--namespace-name", namespace,
                    "--bucket-name", bucket,
                    "--name", objectKey,
                    "--file", tempFile.toString(),
                    "--force",
                    "--profile", profile
            ));
            if (result.exitCode != 0) {
                throw new IllegalStateException("OCI 업로드 실패: " + result.output);
            }
        } catch (IOException e) {
            throw new IllegalStateException("임시 파일 생성/쓰기 실패", e);
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException ignored) {
                }
            }
        }
    }

    @Override
    public void deleteObject(String namespace, String bucket, String objectKey) {
        String profile = resolveProfile(namespace, bucket);
        ExecResult result = exec(List.of(
                storageProperties.getOciCli().getExecutable(),
                "os", "object", "delete",
                "--namespace-name", namespace,
                "--bucket-name", bucket,
                "--name", objectKey,
                "--force",
                "--profile", profile
        ));

        if (result.exitCode == 0) {
            return;
        }

        String output = result.output == null ? "" : result.output;
        if (output.contains("ObjectNotFound") || output.contains("404")) {
            return;
        }

        throw new IllegalStateException("OCI 삭제 실패: " + output);
    }

    @Override
    public boolean exists(String namespace, String bucket, String objectKey) {
        String profile = resolveProfile(namespace, bucket);
        ExecResult result = exec(List.of(
                storageProperties.getOciCli().getExecutable(),
                "os", "object", "head",
                "--namespace-name", namespace,
                "--bucket-name", bucket,
                "--name", objectKey,
                "--profile", profile
        ));

        if (result.exitCode == 0) {
            return true;
        }

        String output = result.output == null ? "" : result.output;
        if (output.contains("ObjectNotFound") || output.contains("404")) {
            return false;
        }

        throw new IllegalStateException("OCI 존재 확인 실패: " + output);
    }

    @Override
    public byte[] getObject(String namespace, String bucket, String objectKey) {
        Path tempFile = null;
        try {
            tempFile = Files.createTempFile("withbuddy-download-", ".bin");
            String profile = resolveProfile(namespace, bucket);
            ExecResult result = exec(List.of(
                    storageProperties.getOciCli().getExecutable(),
                    "os", "object", "get",
                    "--namespace-name", namespace,
                    "--bucket-name", bucket,
                    "--name", objectKey,
                    "--file", tempFile.toString(),
                    "--profile", profile
            ));
            if (result.exitCode != 0) {
                throw new IllegalStateException("OCI 다운로드 실패: " + result.output);
            }
            return Files.readAllBytes(tempFile);
        } catch (IOException e) {
            throw new IllegalStateException("임시 파일 처리 실패", e);
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException ignored) {
                }
            }
        }
    }

    private String resolveProfile(String namespace, String bucket) {
        StorageProperties.Bucket primary = storageProperties.getPrimary();
        StorageProperties.Bucket backup = storageProperties.getBackup();

        if (namespace.equals(primary.getNamespace()) && bucket.equals(primary.getBucket())) {
            return storageProperties.getOciCli().getPrimaryProfile();
        }
        if (namespace.equals(backup.getNamespace()) && bucket.equals(backup.getBucket())) {
            return storageProperties.getOciCli().getBackupProfile();
        }
        if (namespace.equals(backup.getNamespace())) {
            return storageProperties.getOciCli().getBackupProfile();
        }
        return storageProperties.getOciCli().getPrimaryProfile();
    }

    private ExecResult exec(List<String> command) {
        ProcessBuilder processBuilder = new ProcessBuilder(new ArrayList<>(command));
        processBuilder.redirectErrorStream(true);
        if (storageProperties.getOciCli().isSuppressFilePermissionsWarning()) {
            processBuilder.environment().put("OCI_CLI_SUPPRESS_FILE_PERMISSIONS_WARNING", "True");
        }

        try {
            Process process = processBuilder.start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            int exitCode = process.waitFor();
            return new ExecResult(exitCode, output);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("OCI CLI 실행 실패", e);
        } catch (IOException e) {
            throw new IllegalStateException("OCI CLI 실행 실패", e);
        }
    }

    private record ExecResult(int exitCode, String output) {
    }
}
