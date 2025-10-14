package org.acme.newsletter.domain;

import java.util.Locale;

public record NewsletterReview(String draft, String notes, ReviewStatus status) {

    public NewsletterReview(String draft, String notes, String status) {
        this(draft, notes, ReviewStatus.valueOf(status.toLowerCase(Locale.ROOT)));
    }

}
