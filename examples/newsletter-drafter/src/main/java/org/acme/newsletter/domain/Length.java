package org.acme.newsletter.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import java.util.Locale;

public enum Length {
    SHORT,
    MEDIUM,
    LONG;


    @JsonCreator
    public static Length from(String v) {
        return v == null ? null : Length.valueOf(v.trim().toUpperCase(Locale.ROOT));
    }
}
