package org.acme;

public class Message {
    public String message;

    public Message() {} // JSON-B/Jackson

    public Message(String message) {
        this.message = message;
    }
}
