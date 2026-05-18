package sk.blueai.evolution.web;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import io.javalin.http.sse.SseClient;
import sk.blueai.evolution.config.SimulationConfig;

public final class Run {

    public enum Status { PENDING, RUNNING, DONE, ERROR }

    private static final int REPLAY_BUFFER_MAX = 200;

    private final String id;
    private final SimulationConfig config;
    private final Instant createdAt = Instant.now();

    private final List<SseMessage> replayBuffer = new ArrayList<>();
    private final CopyOnWriteArrayList<SseClient> clients = new CopyOnWriteArrayList<>();

    private volatile Status status = Status.PENDING;
    private volatile long stepsEmitted = 0L;
    private volatile String errorMessage = null;
    private volatile Instant finishedAt = null;
    private volatile long elapsedMs = 0L;
    private boolean closed = false;

    public Run(String id, SimulationConfig config) {
        this.id = id;
        this.config = config;
    }

    public String id() { return id; }
    public SimulationConfig config() { return config; }
    public Status status() { return status; }
    public long stepsEmitted() { return stepsEmitted; }
    public String errorMessage() { return errorMessage; }
    public Instant finishedAt() { return finishedAt; }
    public long elapsedMs() { return elapsedMs; }
    public Instant createdAt() { return createdAt; }

    public synchronized void emit(SseMessage msg) {
        if (closed) return;

        if (replayBuffer.size() >= REPLAY_BUFFER_MAX) {
            int dropIndex = (!replayBuffer.isEmpty() && "start".equals(replayBuffer.get(0).event())) ? 1 : 0;
            if (dropIndex < replayBuffer.size()) replayBuffer.remove(dropIndex);
        }
        replayBuffer.add(msg);
        if ("step".equals(msg.event())) stepsEmitted++;

        for (SseClient c : clients) {
            try {
                c.sendEvent(msg.event(), msg.data());
            } catch (Exception ignored) {
                // best-effort fan-out; failed clients are dropped on close callback
            }
        }
    }

    public synchronized void attach(SseClient client) {
        for (SseMessage m : replayBuffer) {
            try { client.sendEvent(m.event(), m.data()); } catch (Exception e) { return; }
        }
        if (status == Status.DONE || status == Status.ERROR) {
            try { client.close(); } catch (Exception ignored) {}
            return;
        }
        clients.add(client);
    }

    public synchronized void replayInto(SseClient client) {
        for (SseMessage m : replayBuffer) {
            try { client.sendEvent(m.event(), m.data()); } catch (Exception e) { return; }
        }
    }

    public void detach(SseClient client) {
        clients.remove(client);
    }

    public synchronized void markRunning() {
        status = Status.RUNNING;
    }

    public synchronized void markDone(long elapsedMs) {
        status = Status.DONE;
        this.elapsedMs = elapsedMs;
        finishedAt = Instant.now();
        closeAllClients();
    }

    public synchronized void markError(String msg) {
        status = Status.ERROR;
        errorMessage = msg;
        finishedAt = Instant.now();
        closeAllClients();
    }

    private void closeAllClients() {
        closed = true;
        for (SseClient c : clients) {
            try { c.close(); } catch (Exception ignored) {}
        }
        clients.clear();
    }
}
