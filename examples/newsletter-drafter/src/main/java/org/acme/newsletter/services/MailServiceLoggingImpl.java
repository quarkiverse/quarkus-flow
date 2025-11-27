package org.acme.newsletter.services;

import jakarta.enterprise.context.ApplicationScoped;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Dumb service mail - in a real-world this could be any other service with mailing capabilities.
 */
@ApplicationScoped
public class MailServiceLoggingImpl implements MailService {
    private static final Logger LOG = LoggerFactory.getLogger(MailServiceLoggingImpl.class);

    @Override
    public void send(String to, String subject, String body) {
        LOG.info("\n\n-------------------------- NEWSLETTER -------------------------\n\nSending {} to {} \n--\n\n{}\n--", subject, to, body);
    }
}
