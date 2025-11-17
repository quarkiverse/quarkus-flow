package io.quarkiverse.flow.providers;

import java.io.IOException;
import java.util.Map;

import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.client.ClientRequestFilter;

class StaticHeadersFilter implements ClientRequestFilter {

    private final Map<String, String> headers;

    StaticHeadersFilter(Map<String, String> headers) {
        this.headers = headers;
    }

    @Override
    public void filter(ClientRequestContext requestContext) throws IOException {
        if (headers == null || headers.isEmpty()) {
            return;
        }
        headers.forEach((name, value) -> {
            if (value != null) {
                requestContext.getHeaders().add(name, value);
            }
        });
    }
}
