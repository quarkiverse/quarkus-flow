package org.acme.newsletter.web;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnError;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Simple WebSocket endpoint used to broadcast review-required CloudEvents payloads
 * to all connected browsers. The payload string broadcasted should already be JSON.
 */
@ServerEndpoint("/ws/newsletter")
@ApplicationScoped
public class NewsletterUpdatesSocket {

    private static final Set<Session> SESSIONS = ConcurrentHashMap.newKeySet();
    private static final Logger LOG = LoggerFactory.getLogger(NewsletterUpdatesSocket.class.getName());

    @OnOpen
    public void onOpen(Session session) {
        SESSIONS.add(session);
    }

    @OnClose
    public void onClose(Session session) {
        SESSIONS.remove(session);
    }

    @OnError
    public void onError(Session session, Throwable t) {
        SESSIONS.remove(session);
        LOG.error("Error while processing websocket request", t);
    }

    /** Broadcast a JSON string to all connected clients. */
    public static void broadcast(String json) {
        for (Session s : SESSIONS) {
            if (s.isOpen()) {
                try {
                    s.getBasicRemote().sendText(json);
                } catch (IOException ignored) { }
            }
        }
    }
}
