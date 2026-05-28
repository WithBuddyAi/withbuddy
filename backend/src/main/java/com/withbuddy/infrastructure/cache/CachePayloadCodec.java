package com.withbuddy.infrastructure.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.msgpack.jackson.dataformat.MessagePackFactory;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

@Component
public class CachePayloadCodec {

    private static final String PREFIX_RAW = "m1:n:";
    private static final String PREFIX_GZIP = "m1:g:";

    private final ObjectMapper jsonObjectMapper;
    private final ObjectMapper msgpackObjectMapper;
    private final AppCacheProperties properties;

    public CachePayloadCodec(ObjectMapper jsonObjectMapper, AppCacheProperties properties) {
        this.jsonObjectMapper = jsonObjectMapper;
        this.msgpackObjectMapper = new ObjectMapper(new MessagePackFactory());
        this.properties = properties;
    }

    public String encode(JsonNode value) {
        try {
            byte[] packed = msgpackObjectMapper.writeValueAsBytes(value);
            boolean compress = properties.getCodec().isCompressionEnabled()
                    && packed.length >= properties.getCodec().getCompressionThresholdBytes();
            byte[] payload = compress ? gzip(packed) : packed;
            String encoded = Base64.getEncoder().encodeToString(payload);
            return (compress ? PREFIX_GZIP : PREFIX_RAW) + encoded;
        } catch (IOException e) {
            throw new IllegalArgumentException("cache value를 인코딩할 수 없습니다.", e);
        }
    }

    public JsonNode decode(String raw) {
        if (raw == null) {
            return null;
        }

        try {
            if (raw.startsWith(PREFIX_RAW) || raw.startsWith(PREFIX_GZIP)) {
                boolean compressed = raw.startsWith(PREFIX_GZIP);
                String encoded = raw.substring(5);
                byte[] decoded = Base64.getDecoder().decode(encoded);
                byte[] payload = compressed ? gunzip(decoded) : decoded;
                return msgpackObjectMapper.readTree(payload);
            }
            // Legacy plain JSON fallback
            return jsonObjectMapper.readTree(raw.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalArgumentException("cache value를 디코딩할 수 없습니다.", e);
        }
    }

    private byte[] gzip(byte[] input) throws IOException {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
             GZIPOutputStream gzip = new GZIPOutputStream(out)) {
            gzip.write(input);
            gzip.finish();
            return out.toByteArray();
        }
    }

    private byte[] gunzip(byte[] input) throws IOException {
        try (ByteArrayInputStream in = new ByteArrayInputStream(input);
             GZIPInputStream gzip = new GZIPInputStream(in);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            gzip.transferTo(out);
            return out.toByteArray();
        }
    }
}
