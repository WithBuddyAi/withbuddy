package com.withbuddy.infrastructure.storage;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.storage")
public class StorageProperties {

    private String provider = "local";
    private String localBaseDir = "storage-data";
    private int maxDocumentSizeMb = 20;
    private int maxImageSizeMb = 5;
    private int downloadUrlTtlSeconds = 300;

    private Bucket primary = new Bucket("primary", "withbuddy-primary");
    private Bucket backup = new Bucket("backup", "withbuddy-backup");
    private OciCli ociCli = new OciCli();
    private Retry retry = new Retry();

    @Getter
    @Setter
    public static class Bucket {
        private String namespace;
        private String bucket;

        public Bucket() {
        }

        public Bucket(String namespace, String bucket) {
            this.namespace = namespace;
            this.bucket = bucket;
        }
    }

    @Getter
    @Setter
    public static class OciCli {
        private String executable = "oci";
        private String primaryProfile = "DEFAULT";
        private String backupProfile = "DEFAULT";
        private String region = "";
        private String publicEndpoint = "";
        private int preauthTtlSeconds = 30;
        private boolean suppressFilePermissionsWarning = true;
    }

    @Getter
    @Setter
    public static class Retry {
        private boolean enabled = true;
        private int maxAttempts = 5;
        private long intervalMs = 60000;
        private long baseBackoffSeconds = 60;
        private long maxBackoffSeconds = 1800;
        private int batchSize = 20;
    }
}
