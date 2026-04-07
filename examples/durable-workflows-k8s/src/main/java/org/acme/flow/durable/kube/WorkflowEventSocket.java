package org.acme.flow.durable.kube;

import java.util.Collections;
import java.util.Deque;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnError;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.ServerEndpoint;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

@ServerEndpoint("/api/workflow/stream")
@ApplicationScoped
public class WorkflowEventSocket {

    private static final Logger LOGGER = LoggerFactory.getLogger(WorkflowEventSocket.class);
    private final Set<Session> sessions = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final ObjectMapper mapper = new ObjectMapper();

    private final Deque<Map<String, Object>> history = new ConcurrentLinkedDeque<>();
    private static final int MAX_HISTORY = 50;

    @OnOpen
    public void onOpen(Session session) {
        sessions.add(session);
        LOGGER.info("UI connected: {}. Sending history payload...", session.getId());

        try {
            Map<String, Object> historyMsg = Map.of("type", "HISTORY", "events", history);
            session.getAsyncRemote().sendText(mapper.writeValueAsString(historyMsg));
        } catch (Exception e) {
            LOGGER.error("Failed to send history to new session", e);
        }
    }

    @OnClose
    public void onClose(Session session) {
        sessions.remove(session);
    }

    @OnError
    public void onError(Session session, Throwable throwable) {
        sessions.remove(session);
    }

    public void broadcast(Map<String, Object> eventPayload) {
        // Manage the rolling window
        history.addFirst(eventPayload);
        if (history.size() > MAX_HISTORY) {
            history.removeLast();
        }

        try {
            // Broadcast as a LIVE event to trigger UI animations
            Map<String, Object> liveMsg = Map.of("type", "LIVE", "event", eventPayload);
            String message = mapper.writeValueAsString(liveMsg);

            sessions.forEach(s -> {
                if (s.isOpen())
                    s.getAsyncRemote().sendText(message);
            });
        } catch (Exception e) {
            LOGGER.error("Failed to broadcast live event", e);
        }
    }
}
