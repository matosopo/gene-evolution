package sk.blueai.evolution.web;

import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.json.JSONObject;

import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.sse.SseClient;
import io.javalin.http.staticfiles.Location;
import sk.blueai.evolution.config.ConfigLoader;
import sk.blueai.evolution.config.SimulationConfig;
import sk.blueai.evolution.engine.SimulationEngine;

public final class WebServer {

    public static final int DEFAULT_PORT = 8080;
    private static final int MAX_CONCURRENT_RUNS = 4;

    private final RunRegistry registry = new RunRegistry();
    private final ExecutorService engineExecutor;
    private final Javalin app;

    private WebServer() {
        this.engineExecutor = Executors.newFixedThreadPool(MAX_CONCURRENT_RUNS, r -> {
            Thread t = new Thread(r, "sim-engine");
            t.setDaemon(false);
            return t;
        });
        this.app = Javalin.create(cfg -> {
            cfg.staticFiles.add(staticFiles -> {
                staticFiles.hostedPath = "/";
                staticFiles.directory = "/web";
                staticFiles.location = Location.CLASSPATH;
            });
            cfg.showJavalinBanner = false;
        });
        wireRoutes();
    }

    public static WebServer start(int port) {
        WebServer server = new WebServer();
        server.app.start(port);
        System.out.println("WebServer listening on http://0.0.0.0:" + server.app.port());
        return server;
    }

    public int port() {
        return app.port();
    }

    public void stop() {
        app.stop();
        engineExecutor.shutdownNow();
        try { engineExecutor.awaitTermination(2, TimeUnit.SECONDS); } catch (InterruptedException ignored) {}
        registry.shutdown();
    }

    private void wireRoutes() {
        app.post("/api/runs", this::handleCreateRun);
        app.get("/api/runs/{id}", this::handleGetRun);
        app.sse("/api/runs/{id}/events", this::handleSse);
    }

    private void handleCreateRun(Context ctx) {
        if (registry.activeCount() >= MAX_CONCURRENT_RUNS) {
            writeJson(ctx, 429, new JSONObject().put("error", "too many active runs"));
            return;
        }
        SimulationConfig config;
        try {
            JSONObject body = new JSONObject(ctx.body());
            config = ConfigLoader.parse(body);
        } catch (Exception e) {
            writeJson(ctx, 400, new JSONObject().put("error", "invalid config: " + e.getMessage()));
            return;
        }
        String id = UUID.randomUUID().toString();
        Run run = registry.create(id, config);
        engineExecutor.submit(() -> executeRun(run));
        writeJson(ctx, 201, new JSONObject().put("runId", id));
    }

    private static void writeJson(Context ctx, int status, JSONObject body) {
        ctx.status(status).contentType("application/json").result(body.toString());
    }

    private void executeRun(Run run) {
        try {
            run.markRunning();
            WebSseListener listener = new WebSseListener(run);
            SimulationEngine engine = new SimulationEngine(run.config(), listener);
            long t0 = System.nanoTime();
            engine.run();
            long elapsedMs = (System.nanoTime() - t0) / 1_000_000L;
            JSONObject end = new JSONObject();
            end.put("steps", run.config().finalStepCount());
            end.put("totalN", engine.population().totalN());
            end.put("activeSpecies", engine.population().activeSpeciesCount());
            end.put("elapsedMs", elapsedMs);
            run.emit(new SseMessage("end", end.toString()));
            run.markDone(elapsedMs);
            System.out.println("[run " + run.id() + "] done in " + elapsedMs + " ms");
        } catch (Throwable e) {
            String msg = e.getClass().getSimpleName() + ": " + e.getMessage();
            JSONObject err = new JSONObject();
            err.put("message", msg);
            run.emit(new SseMessage("error", err.toString()));
            run.markError(msg);
            System.err.println("[run " + run.id() + "] error: " + msg);
        }
    }

    private void handleGetRun(Context ctx) {
        Run run = registry.get(ctx.pathParam("id"));
        if (run == null) { ctx.status(404); return; }
        JSONObject body = new JSONObject();
        body.put("status", run.status().name().toLowerCase());
        body.put("stepsEmitted", run.stepsEmitted());
        if (run.errorMessage() != null) body.put("errorMessage", run.errorMessage());
        if (run.finishedAt() != null) body.put("elapsedMs", run.elapsedMs());
        ctx.contentType("application/json").result(body.toString());
    }

    private void handleSse(SseClient client) {
        String id = client.ctx().pathParam("id");
        Run run = registry.get(id);
        if (run == null) {
            try { client.sendEvent("error", "{\"message\":\"unknown run\"}"); } catch (Exception ignored) {}
            return;
        }
        Run.Status snapshotStatus = run.status();
        if (snapshotStatus == Run.Status.DONE || snapshotStatus == Run.Status.ERROR) {
            // Run already finished — synchronously stream the replay buffer and let the handler
            // return so Javalin can close the response cleanly. No keepAlive() needed.
            run.replayInto(client);
            return;
        }
        // Run still active — keep the connection open, register as a live subscriber,
        // and let the engine thread fan-out push events.
        client.keepAlive();
        client.onClose(() -> run.detach(client));
        run.attach(client);
    }
}
