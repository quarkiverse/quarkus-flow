package org.acme.newsletter.domain;

import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum Length {
    SHORT,
    MEDIUM,
    LONG;


    @JsonCreator
    public static Length from(String v) {
        return v == null ? null : Length.valueOf(v.trim().toUpperCase(Locale.ROOT));
    }
}
