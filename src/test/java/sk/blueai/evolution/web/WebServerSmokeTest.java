package sk.blueai.evolution.web;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebServerSmokeTest {

    private WebServer server;
    private HttpClient http;

    @BeforeEach
    void setUp() {
        server = WebServer.start(0);
        http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    @AfterEach
    void tearDown() {
        if (server != null) server.stop();
    }

    @Test
    void postsConfigAndReceivesStepAndEndEvents() throws Exception {
        String config = """
                {
                  "finalStepCount": 10,
                  "crowdingFactor": 1000,
                  "randomSeed": 7,
                  "spontaneous": {
                    "spawnProbability": 0.5,
                    "deathProbability": 0.05,
                    "replicationProbability": 0.20,
                    "mutationProbability": 0.02,
                    "variation": 0.05
                  }
                }
                """;

        URI base = URI.create("http://127.0.0.1:" + server.port());

        HttpResponse<String> create = http.send(
                HttpRequest.newBuilder(base.resolve("/api/runs"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(config))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(201, create.statusCode(), "POST /api/runs should return 201, got " + create.body());

        String runId = new JSONObject(create.body()).getString("runId");
        assertNotNull(runId);

        HttpResponse<Stream<String>> stream = http.send(
                HttpRequest.newBuilder(base.resolve("/api/runs/" + runId + "/events"))
                        .header("Accept", "text/event-stream")
                        .timeout(Duration.ofSeconds(10))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofLines());

        assertEquals(200, stream.statusCode());

        List<String> lines = new ArrayList<>();
        boolean sawStep = false;
        boolean sawEnd = false;
        boolean sawStepData = false;
        String pendingEvent = null;

        Iterable<String> iterable = stream.body()::iterator;
        for (String line : iterable) {
            lines.add(line);
            if (line.startsWith("event:")) {
                pendingEvent = line.substring(6).trim();
                if ("step".equals(pendingEvent)) sawStep = true;
                if ("end".equals(pendingEvent)) sawEnd = true;
            } else if (line.startsWith("data:") && "step".equals(pendingEvent) && !sawStepData) {
                String payload = line.substring(5).trim();
                JSONObject parsed = new JSONObject(payload);
                assertTrue(parsed.has("step"));
                assertTrue(parsed.has("species"));
                sawStepData = true;
            }
            if (sawEnd) break;
        }

        assertTrue(sawStep, "expected at least one 'event: step' line; got: " + lines);
        assertTrue(sawEnd, "expected an 'event: end' line; got: " + lines);
        assertTrue(sawStepData, "expected a parseable 'step' data payload; got: " + lines);
    }
}
