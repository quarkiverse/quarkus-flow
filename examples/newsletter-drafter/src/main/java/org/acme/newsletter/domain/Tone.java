package org.acme.newsletter.domain;

public enum Tone {
    FRIENDLY,
    NEUTRAL;

    @Override
    public String toString() {
        return super.toString().toLowerCase();
    }
}
