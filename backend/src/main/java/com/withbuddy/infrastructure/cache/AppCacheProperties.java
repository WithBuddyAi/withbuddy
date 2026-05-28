package com.withbuddy.infrastructure.cache;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.cache")
public class AppCacheProperties {

    private String env = "prod";
    private String keyPrefix = "ai";
    private String keyVersion = "v1";
    private L1 l1 = new L1();
    private L2 l2 = new L2();
    private Codec codec = new Codec();

    @Getter
    @Setter
    public static class L1 {
        private boolean enabled = true;
        private String spec = "maximumSize=5000,expireAfterWrite=10m,recordStats";
    }

    @Getter
    @Setter
    public static class L2 {
        private int defaultTtlSeconds = 300;
        private int minTtlSeconds = 5;
        private int maxTtlSeconds = 86400;
        private double jitterRatio = 0.2;
    }

    @Getter
    @Setter
    public static class Codec {
        private boolean compressionEnabled = true;
        private int compressionThresholdBytes = 1024;
        private String format = "msgpack";
    }
}
