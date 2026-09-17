package io.quarkiverse.flow.runner.resources;

/**
 * Mapped to a {@code 400 Bad Request} response by {@link InvalidStatusFilterExceptionMapper}.
 */
public class InvalidStatusFilterException extends RuntimeException {

    public InvalidStatusFilterException(String message) {
        super(message);
    }
}
