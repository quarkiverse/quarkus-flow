package org.acme.newsletter.domain;

import java.util.Locale;

public record NewsletterInput(String marketMood, String topMovers, String macroData, Tone tone, Length length,
                              String notes) {

    public NewsletterInput(String marketMood, String topMovers, String macroData, String tone, String length,
                           String notes) {
        this(marketMood, topMovers, macroData, Tone.valueOf(tone.toUpperCase(Locale.ROOT)), Length.valueOf(length.toUpperCase(Locale.ROOT)), notes);
    }

    public NewsletterInput(String marketMood, String topMovers, String macroData, Tone tone, Length length) {
        this(marketMood, topMovers, macroData, tone, length, "");
    }


}
