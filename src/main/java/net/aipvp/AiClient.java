package net.aipvp;

import com.google.gson.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.function.Consumer;

/** Запросы к API ИИ. Поддерживает Anthropic Messages и OpenAI-совместимый chat/completions. */
public class AiClient {
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private static final Gson GSON = new Gson();

    public static final String SYSTEM = """
You control a Minecraft PvP player. You get a JSON game state and must answer with ONE JSON object only, no text around it:
{"target":"<player name or null>","move":"forward|back|none","strafe":"left|right|none","jump":true|false,"sprint":true|false,"attack":true|false,"slot":-1..8,"eat":true|false}
Rules: "slot" selects a hotbar slot (-1 = keep current). Use a sword/weapon slot to fight; use "eat" with a food/golden apple slot selected when health is low (stop moving forward while eating). Keep distance ~2.5-3 blocks, strafe to dodge, retreat if health is very low. Never target names listed in "ignored". The game executes your answer for the next ~0.7 seconds.""";

    public static void ask(String stateJson, Consumer<JsonObject> ok, Consumer<String> err) {
        Config c = Config.I;
        try {
            boolean anth = c.provider.equalsIgnoreCase("anthropic");
            String base = c.baseUrl == null || c.baseUrl.isBlank()
                    ? (anth ? "https://api.anthropic.com/v1" : "https://api.openai.com/v1")
                    : c.baseUrl.replaceAll("/+$", "");
            JsonObject body = new JsonObject();
            body.addProperty("model", c.model);
            body.addProperty("max_tokens", 150);
            HttpRequest.Builder rb = HttpRequest.newBuilder().timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json");
            if (anth) {
                body.addProperty("system", SYSTEM);
                JsonArray ms = new JsonArray();
                JsonObject m = new JsonObject(); m.addProperty("role", "user"); m.addProperty("content", stateJson);
                ms.add(m); body.add("messages", ms);
                rb.uri(URI.create(base + "/messages")).header("x-api-key", c.apiKey).header("anthropic-version", "2023-06-01");
            } else {
                JsonArray ms = new JsonArray();
                JsonObject s = new JsonObject(); s.addProperty("role", "system"); s.addProperty("content", SYSTEM);
                JsonObject m = new JsonObject(); m.addProperty("role", "user"); m.addProperty("content", stateJson);
                ms.add(s); ms.add(m); body.add("messages", ms);
                rb.uri(URI.create(base + "/chat/completions")).header("Authorization", "Bearer " + c.apiKey);
            }
            HttpRequest req = rb.POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body))).build();
            HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString()).whenComplete((resp, ex) -> {
                try {
                    if (ex != null) { err.accept(String.valueOf(ex.getMessage())); return; }
                    if (resp.statusCode() / 100 != 2) { err.accept("HTTP " + resp.statusCode()); return; }
                    JsonObject root = JsonParser.parseString(resp.body()).getAsJsonObject();
                    String text = anth
                            ? root.getAsJsonArray("content").get(0).getAsJsonObject().get("text").getAsString()
                            : root.getAsJsonArray("choices").get(0).getAsJsonObject().getAsJsonObject("message").get("content").getAsString();
                    int a = text.indexOf('{'), b = text.lastIndexOf('}');
                    if (a < 0 || b < a) { err.accept("bad reply"); return; }
                    ok.accept(JsonParser.parseString(text.substring(a, b + 1)).getAsJsonObject());
                } catch (Exception e) { err.accept(String.valueOf(e.getMessage())); }
            });
        } catch (Exception e) { err.accept(String.valueOf(e.getMessage())); }
    }
}
