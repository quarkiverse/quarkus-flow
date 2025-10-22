package org.acme.newsletter.domain;

import java.util.Locale;

public record HumanReview(String draft, String notes, ReviewStatus status) {

    public HumanReview(String draft, String notes, String status) {
        this(draft, notes, ReviewStatus.valueOf(status.toLowerCase(Locale.ROOT)));
    }

}
