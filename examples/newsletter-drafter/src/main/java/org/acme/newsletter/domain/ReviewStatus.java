package org.acme.newsletter.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import java.util.Locale;

public enum ReviewStatus {
    DONE,
    NEEDS_REVISION;

    @JsonCreator
    public static ReviewStatus from(String v) {
        return v == null ? null : ReviewStatus.valueOf(v.trim().toUpperCase(Locale.ROOT));
    }
}
