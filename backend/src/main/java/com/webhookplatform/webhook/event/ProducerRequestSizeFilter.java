package com.webhookplatform.webhook.event;

import java.io.IOException;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.webhookplatform.webhook.common.error.ApiErrorResponse;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class ProducerRequestSizeFilter extends OncePerRequestFilter {

    static final long MAX_REQUEST_BYTES = 1024L * 1024L;
    private static final String EVENTS_PATH = "/api/v1/events";
    private static final String TEST_EVENTS_PATH_SUFFIX = "/test-events";

    private final ObjectMapper objectMapper;

    public ProducerRequestSizeFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return !EVENTS_PATH.equals(path)
                && !path.matches("^/api/v1/applications/[^/]+" + TEST_EVENTS_PATH_SUFFIX + "$");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (request.getContentLengthLong() > MAX_REQUEST_BYTES) {
            writePayloadTooLarge(response);
            return;
        }

        try {
            filterChain.doFilter(new LimitedRequest(request, MAX_REQUEST_BYTES), response);
        } catch (PayloadTooLargeException exception) {
            if (!response.isCommitted()) {
                writePayloadTooLarge(response);
            }
        }
    }

    private void writePayloadTooLarge(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(),
                new ApiErrorResponse("PAYLOAD_TOO_LARGE", "Producer request exceeds the 1 MiB limit."));
    }

    private static final class LimitedRequest extends HttpServletRequestWrapper {

        private final long maximumBytes;
        private ServletInputStream inputStream;

        private LimitedRequest(HttpServletRequest request, long maximumBytes) {
            super(request);
            this.maximumBytes = maximumBytes;
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            if (inputStream == null) {
                inputStream = new LimitedServletInputStream(super.getInputStream(), maximumBytes);
            }
            return inputStream;
        }
    }

    private static final class LimitedServletInputStream extends ServletInputStream {

        private final ServletInputStream delegate;
        private final long maximumBytes;
        private long bytesRead;

        private LimitedServletInputStream(ServletInputStream delegate, long maximumBytes) {
            this.delegate = delegate;
            this.maximumBytes = maximumBytes;
        }

        @Override
        public int read() throws IOException {
            if (bytesRead == maximumBytes) {
                if (delegate.read() != -1) {
                    throw new PayloadTooLargeException();
                }
                return -1;
            }
            int value = delegate.read();
            if (value != -1) {
                bytesRead++;
            }
            return value;
        }

        @Override
        public int read(byte[] bytes, int offset, int length) throws IOException {
            if (length == 0) {
                return 0;
            }
            long remaining = maximumBytes - bytesRead;
            int requested = (int) Math.min(length, remaining + 1);
            int count = delegate.read(bytes, offset, requested);
            if (count == -1) {
                return -1;
            }
            if (count > remaining) {
                throw new PayloadTooLargeException();
            }
            bytesRead += count;
            return count;
        }

        @Override
        public boolean isFinished() {
            return delegate.isFinished();
        }

        @Override
        public boolean isReady() {
            return delegate.isReady();
        }

        @Override
        public void setReadListener(ReadListener readListener) {
            delegate.setReadListener(readListener);
        }
    }
}
