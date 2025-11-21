package ilove.quark.us;

public record Message(String message) {
    public Message() {
        this("");
    } // JSON-B/Jackson
}
