package org.acme.newsletter.domain;

import java.util.Locale;

public record CriticInput(String draft, Tone tone, String compliance) {
    public CriticInput(String draft, String tone, String compliance) {
        this(draft, Tone.valueOf(tone.toUpperCase(Locale.ENGLISH)), compliance);
    }
}
