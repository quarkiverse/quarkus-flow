package org.acme.newsletter.domain;

import java.util.Locale;

public record NewsletterInput(String marketMood, String topMovers, String macroData, Tone tone, Length length,
                              String notes) {

    public NewsletterInput(String marketMood, String topMovers, String macroData, String tone, String length,
                           String notes) {
        this(marketMood, topMovers, macroData, Tone.valueOf(tone.toUpperCase(Locale.ENGLISH)), Length.valueOf(length.toUpperCase(Locale.ENGLISH)), notes);
    }

    public NewsletterInput(String marketMood, String topMovers, String macroData, Tone tone, Length length) {
        this(marketMood, topMovers, macroData, tone, length, "");
    }


}
