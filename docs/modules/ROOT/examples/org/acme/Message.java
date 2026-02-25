package org.acme;

public record Message(String message) {
    public Message() {
        this("");
    }
}
