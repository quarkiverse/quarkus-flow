package io.quarkiverse.flow.runner.model;

import java.util.HashMap;
import java.util.Map;

/**
 * Container for HATEOAS links.
 */
public class Links {

    private final Map<String, Link> links = new HashMap<>();

    public Links add(String rel, String href) {
        links.put(rel, Link.of(href));
        return this;
    }

    public Links self(String href) {
        return add("self", href);
    }

    public Links execute(String href) {
        return add("execute", href);
    }

    public Map<String, Link> asMap() {
        return Map.copyOf(links);
    }

    public static Links empty() {
        return new Links();
    }
}
