package com.wangqi.employee.observability;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link CorrelationIdFilter}.
 *
 * @author wangqi
 */
class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    @DisplayName("a request without a correlation id gets one, echoed in the response header")
    void shouldGenerateCorrelationId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/employees");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> duringRequest = new AtomicReference<>();

        filter.doFilter(request, response, (req, res) -> duringRequest.set(MDC.get(CorrelationIdFilter.MDC_KEY)));

        String header = response.getHeader(CorrelationIdFilter.HEADER);
        assertThat(header).isNotBlank();
        assertThat(duringRequest.get()).isEqualTo(header);
        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }

    @Test
    @DisplayName("a safe incoming correlation id is reused so gateway logs stay correlated")
    void shouldReuseIncomingCorrelationId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/employees");
        request.addHeader(CorrelationIdFilter.HEADER, "gateway-7f3a-01");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader(CorrelationIdFilter.HEADER)).isEqualTo("gateway-7f3a-01");
    }

    @Test
    @DisplayName("an unsafe incoming value is replaced instead of being echoed into logs")
    void shouldReplaceUnsafeCorrelationId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/employees");
        request.addHeader(CorrelationIdFilter.HEADER, "bad<script>id");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader(CorrelationIdFilter.HEADER))
                .isNotBlank()
                .isNotEqualTo("bad<script>id");
    }
}
