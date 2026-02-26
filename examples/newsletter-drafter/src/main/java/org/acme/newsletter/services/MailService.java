package org.acme.newsletter.services;

import org.acme.newsletter.domain.NewsletterDraft;

public interface MailService {
    void send(String to, NewsletterDraft draft);
}
