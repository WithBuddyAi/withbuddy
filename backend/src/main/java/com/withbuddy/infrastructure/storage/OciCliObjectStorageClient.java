package com.withbuddy.infrastructure.storage;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
@ConditionalOnProperty(prefix = "app.storage", name = "provider", havingValue = "oci-cli")
public class OciCliObjectStorageClient implements ObjectStorageClient {

    private final StorageProperties storageProperties;
    private final ObjectMapper objectMapper;

    public OciCliObjectStorageClient(StorageProperties storageProperties, ObjectMapper objectMapper) {
        this.storageProperties = storageProperties;
        this.objectMapper = objectMapper;
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

    @Override
    public String createPreSignedGetUrl(String namespace, String bucket, String objectKey, int expiresInSeconds) {
        String profile = resolveProfile(namespace, bucket);
        String expiresAt = OffsetDateTime.now(ZoneOffset.UTC)
                .plusSeconds(Math.max(1, expiresInSeconds))
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ"));

        ExecResult result = exec(List.of(
                storageProperties.getOciCli().getExecutable(),
                "os", "preauth-request", "create",
                "--namespace-name", namespace,
                "--bucket-name", bucket,
                "--name", "withbuddy-" + UUID.randomUUID(),
                "--access-type", "ObjectRead",
                "--object-name", objectKey,
                "--time-expires", expiresAt,
                "--profile", profile
        ));
        if (result.exitCode != 0) {
            throw new IllegalStateException("OCI pre-auth URL 생성 실패: " + result.output);
        }

        try {
            JsonNode root = objectMapper.readTree(result.output);
            String accessUri = root.path("data").path("access-uri").asText("");
            if (!StringUtils.hasText(accessUri)) {
                throw new IllegalStateException("OCI pre-auth URL access-uri 응답이 비어 있습니다.");
            }
            return resolveObjectStorageEndpoint() + accessUri;
        } catch (IOException e) {
            throw new IllegalStateException("OCI pre-auth URL 응답 파싱 실패", e);
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

    private String resolveObjectStorageEndpoint() {
        String configured = trimTrailingSlash(storageProperties.getOciCli().getPublicEndpoint());
        if (StringUtils.hasText(configured)) {
            return configured;
        }

        String region = storageProperties.getOciCli().getRegion();
        if (!StringUtils.hasText(region)) {
            region = System.getenv("OCI_REGION");
        }
        if (!StringUtils.hasText(region)) {
            throw new IllegalStateException("Object Storage endpoint 구성을 위해 STORAGE_OCI_REGION 또는 STORAGE_OCI_PUBLIC_ENDPOINT 설정이 필요합니다.");
        }
        return "https://objectstorage." + region.trim() + ".oraclecloud.com";
    }

    private String trimTrailingSlash(String value) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        String normalized = value.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
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
