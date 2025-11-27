package org.acme.newsletter.web;

import jakarta.enterprise.context.ApplicationScoped;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Simple ring buffer cache for the last N "review required" payloads (as raw JSON strings).
 */
@ApplicationScoped
public class NewsletterReviewCache {

    public static final class Item {
        public final String json;       // raw JSON payload
        public final Instant received;  // when it hit the app

        public Item(String json, Instant received) {
            this.json = json;
            this.received = received;
        }
    }

    private static final int MAX = 50;
    private final Deque<Item> buffer = new ArrayDeque<>(MAX);

    public synchronized void add(String json) {
        if (buffer.size() == MAX) buffer.removeFirst();
        buffer.addLast(new Item(json, Instant.now()));
    }

    /** Returns newest-first list. */
    public synchronized List<Item> latest(int limit) {
        int n = Math.min(limit, buffer.size());
        ArrayList<Item> out = new ArrayList<>(n);
        var it = buffer.descendingIterator();
        while (it.hasNext() && out.size() < n) out.add(it.next());
        return out;
    }

    public synchronized int size() { return buffer.size(); }
}
