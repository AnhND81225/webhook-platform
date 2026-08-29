package com.webhookplatform.webhook.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequestWrapper;

class ProducerRequestSizeFilterTest {

    private final ProducerRequestSizeFilter filter = new ProducerRequestSizeFilter(new ObjectMapper());

    @Test
    void rejectsAnOversizedChunkedRequestWithoutTrustingContentLength() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/events");
        request.setContent(new byte[(int) ProducerRequestSizeFilter.MAX_REQUEST_BYTES + 1]);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(new HttpServletRequestWrapper(request) {
            @Override
            public long getContentLengthLong() {
                return -1;
            }
        }, response, (servletRequest, servletResponse) ->
                servletRequest.getInputStream().transferTo(OutputStream.nullOutputStream()));

        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(response.getContentAsString(StandardCharsets.UTF_8)).contains("PAYLOAD_TOO_LARGE");
    }
}
