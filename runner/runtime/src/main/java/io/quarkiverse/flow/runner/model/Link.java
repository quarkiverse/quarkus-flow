package io.quarkiverse.flow.runner.model;

/**
 * Represents a hypermedia link following HATEOAS principles.
 */
public record Link(String href) {

    public static Link of(String href) {
        return new Link(href);
    }
}
