package org.acme.newsletter.domain;

import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum Tone {
    FRIENDLY,
    NEUTRAL;

    @Override
    public String toString() {
        return super.toString().toLowerCase();
    }

    @JsonCreator
    public static Tone from(String v) {
        return v == null ? null : Tone.valueOf(v.trim().toUpperCase(Locale.ROOT));
    }
}
