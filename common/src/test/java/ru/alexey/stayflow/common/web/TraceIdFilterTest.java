package ru.alexey.stayflow.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class TraceIdFilterTest {

    private final TraceIdFilter filter = new TraceIdFilter();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void keepsValidIncomingTraceId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(TraceHeaders.REQUEST_ID, "trace-123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> traceIdInChain = new AtomicReference<>();

        filter.doFilter(request, response, chainCapturing(traceIdInChain));

        assertThat(traceIdInChain.get()).isEqualTo("trace-123");
        assertThat(response.getHeader(TraceHeaders.REQUEST_ID)).isEqualTo("trace-123");
        assertThat(MDC.get(TraceHeaders.MDC_TRACE_ID)).isNull();
    }

    @Test
    void generatesTraceIdWhenHeaderIsInvalid() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(TraceHeaders.REQUEST_ID, "bad trace id");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> traceIdInChain = new AtomicReference<>();

        filter.doFilter(request, response, chainCapturing(traceIdInChain));

        assertThat(traceIdInChain.get()).isNotBlank();
        assertThat(traceIdInChain.get()).isNotEqualTo("bad trace id");
        assertThat(response.getHeader(TraceHeaders.REQUEST_ID)).isEqualTo(traceIdInChain.get());
    }

    private FilterChain chainCapturing(AtomicReference<String> traceId) {
        return (request, response) -> traceId.set(MDC.get(TraceHeaders.MDC_TRACE_ID));
    }
}