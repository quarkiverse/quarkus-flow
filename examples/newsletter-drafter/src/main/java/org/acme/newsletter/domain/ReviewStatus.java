package org.acme.newsletter.domain;

import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum ReviewStatus {
    DONE,
    NEEDS_REVISION;

    @JsonCreator
    public static ReviewStatus from(String v) {
        return v == null ? null : ReviewStatus.valueOf(v.trim().toUpperCase(Locale.ROOT));
    }
}
