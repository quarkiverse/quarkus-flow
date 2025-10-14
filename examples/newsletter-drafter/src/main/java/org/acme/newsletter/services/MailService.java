package org.acme.newsletter.services;

public interface MailService {
    void send(String to, String subject, String body);
}
