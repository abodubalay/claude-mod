package com.claudecompanion.brain;

import com.google.gson.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Подключение к нейросетям в «формате OpenAI» (/chat/completions с function calling):
 * OpenAI, DeepSeek, Gemini (OpenAI-совместимый адрес), OpenRouter, Groq, Mistral, Ollama, LM Studio и другие.
 */
public final class OpenAiChat {
    private OpenAiChat() {}

    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();
    private static final Gson GSON = new Gson();

    /** Ошибка HTTP от сервиса. */
    public static class ApiError extends Exception {
        public final int status;
        public ApiError(int status, String body) {
            super("HTTP " + status + ": " + (body.length() > 300 ? body.substring(0, 300) : body));
            this.status = status;
        }
    }

    public static String endpoint(String baseUrl) {
        String b = baseUrl.trim();
        while (b.endsWith("/")) b = b.substring(0, b.length() - 1);
        return b.endsWith("/chat/completions") ? b : b + "/chat/completions";
    }

    /** Один запрос. Возвращает choices[0] (с полями message и finish_reason). */
    public static JsonObject complete(String baseUrl, String key, String model, JsonArray messages, JsonArray tools) throws Exception {
        JsonObject body = new JsonObject();
        body.addProperty("model", model);
        body.add("messages", messages);
        if (tools != null && !tools.isEmpty()) {
            body.add("tools", tools);
            body.addProperty("tool_choice", "auto");
        }
        HttpRequest.Builder req = HttpRequest.newBuilder(URI.create(endpoint(baseUrl)))
                .timeout(Duration.ofSeconds(180))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body)));
        if (key != null && !key.isBlank() && !key.equalsIgnoreCase("none")) req.header("Authorization", "Bearer " + key.trim());
        HttpResponse<String> res = HTTP.send(req.build(), HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() / 100 != 2) throw new ApiError(res.statusCode(), res.body());
        JsonObject json = JsonParser.parseString(res.body()).getAsJsonObject();
        JsonArray choices = json.getAsJsonArray("choices");
        if (choices == null || choices.isEmpty()) throw new ApiError(res.statusCode(), "пустой ответ: " + res.body());
        return choices.get(0).getAsJsonObject();
    }

    public static JsonArray toolsJson() {
        return GSON.toJsonTree(Tools.OPENAI_SPECS).getAsJsonArray();
    }

    public static JsonObject msg(String role, String content) {
        JsonObject m = new JsonObject();
        m.addProperty("role", role);
        m.addProperty("content", content);
        return m;
    }
}
