package org.acme.newsletter.domain;

import java.util.List;
import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonCreator;

public record NewsletterRequest(MarketMood mood, List<String> topMovers, String macroData, Tone tone, Length length) {

    public enum Length {
        SHORT,
        MEDIUM,
        LONG;

        @JsonCreator
        public static Length from(String v) {
            return v == null ? null : Length.valueOf(v.trim().toUpperCase(Locale.ROOT));
        }
    }

    public enum Tone {
        FRIENDLY,
        NEUTRAL,
        FORMAL,
        CAUTIOUS;

        @JsonCreator
        public static Tone from(String v) {
            return v == null ? null : Tone.valueOf(v.trim().toUpperCase(Locale.ROOT));
        }

        @Override
        public String toString() {
            return super.toString().toLowerCase();
        }
    }

    public enum MarketMood {
        BULLISH,
        BEARISH;

        @JsonCreator
        public static MarketMood from(String v) {
            return v == null ? null : MarketMood.valueOf(v.trim().toUpperCase(Locale.ROOT));
        }

        @Override
        public String toString() {
            return super.toString().toLowerCase();
        }
    }
}
