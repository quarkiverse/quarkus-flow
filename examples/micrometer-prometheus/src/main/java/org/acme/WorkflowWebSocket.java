package org.acme;

import io.quarkus.logging.Log;
import jakarta.inject.Singleton;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnError;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.ServerEndpoint;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@ServerEndpoint("/workflow-events")
@Singleton
public class WorkflowWebSocket {

    private final Map<String, Session> sessions = new ConcurrentHashMap<>();

    @OnOpen
    public void onOpen(Session session) {
        sessions.put(session.getId(), session);
        Log.info("WebSocket connection opened: " + session.getId());
    }

    @OnClose
    public void onClose(Session session) {
        sessions.remove(session.getId());
        Log.info("WebSocket connection closed: " + session.getId());
    }

    @OnError
    public void onError(Session session, Throwable throwable) {
        Log.error("WebSocket error for session " + session.getId(), throwable);
        sessions.remove(session.getId());
    }

    public void broadcast(String message) {
        sessions.values().forEach(session -> {
            session.getAsyncRemote().sendText(message, result -> {
                if (result.getException() != null) {
                    Log.error("Unable to send message to session " + session.getId(), result.getException());
                }
            });
        });
    }
}
