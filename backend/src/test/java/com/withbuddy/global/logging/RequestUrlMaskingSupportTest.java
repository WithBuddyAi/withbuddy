package com.withbuddy.global.logging;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class RequestUrlMaskingSupportTest {

    @Test
    void returnsPathWhenQueryStringMissing() {
        String masked = RequestUrlMaskingSupport.maskPathAndQuery("/api/v1/documents/10/file", null);
        assertThat(masked).isEqualTo("/api/v1/documents/10/file");
    }

    @Test
    void masksEveryQueryValueAndKeepsKeys() {
        String masked = RequestUrlMaskingSupport.maskPathAndQuery(
                "/api/v1/documents/10/file",
                "source=PRIMARY&token=abc123&expires=900"
        );
        assertThat(masked).isEqualTo("/api/v1/documents/10/file?source=***&token=***&expires=***");
    }

    @Test
    void masksKeylessSegmentAndPreservesEmptyValue() {
        String masked = RequestUrlMaskingSupport.maskPathAndQuery(
                "/api/v1/documents/10/file",
                "abc123&source="
        );
        assertThat(masked).isEqualTo("/api/v1/documents/10/file?***&source=");
    }

    @Test
    void resolvesMaskedPathFromRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/documents/10/file");
        request.setQueryString("source=PRIMARY&signature=secret");

        String masked = RequestUrlMaskingSupport.resolveMaskedPath(request);
        assertThat(masked).isEqualTo("/api/v1/documents/10/file?source=***&signature=***");
    }
}
