package com.claudecompanion;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Настройки мода: config/claudecompanion.json на компьютере того, кто держит мир.
 * У каждой нейросети свой профиль: имя, на что откликается, сервер, ключ и модель.
 */
public class Settings {

    /** Один напарник-нейросеть. */
    public static class Profile {
        public String name;                 // как его зовут в игре
        public String provider;             // anthropic | openai (любой сервис в формате OpenAI)
        public String baseUrl = "";         // пусто у anthropic = официальный api.anthropic.com
        public String apiKey = "";
        public String model;
        public List<String> names = new ArrayList<>(); // на что откликается в чате
        public String color = "6";          // цвет ника (код § в Minecraft)

        public Profile() {}

        Profile(String name, String provider, String baseUrl, String model, String color, String... names) {
            this.name = name;
            this.provider = provider;
            this.baseUrl = baseUrl;
            this.model = model;
            this.color = color;
            this.names = new ArrayList<>(List.of(names));
        }

        public boolean isOpenAi() { return "openai".equals(provider); }
        public boolean hasBaseUrl() { return baseUrl != null && !baseUrl.isBlank(); }
        public boolean hasKey() { return apiKey != null && !apiKey.isBlank(); }

        public String maskedKey() {
            if (!hasKey()) return "нет";
            if (apiKey.length() < 12) return "***";
            return apiKey.substring(0, 8) + "…" + apiKey.substring(apiKey.length() - 4);
        }

        public String server() {
            if (hasBaseUrl()) return baseUrl;
            return isOpenAi() ? "(не задан)" : "api.anthropic.com";
        }
    }

    /** Готовые профили. Адреса — официальные OpenAI-совместимые точки сервисов; модели можно поменять. */
    static Map<String, Profile> presets() {
        Map<String, Profile> m = new LinkedHashMap<>();
        m.put("claude", new Profile("Claude", "anthropic", "", "claude-haiku-5-5", "6",
                "claude", "клод", "клоди", "клодик"));
        m.put("deepseek", new Profile("DeepSeek", "openai", "https://api.deepseek.com/v1", "deepseek-chat", "9",
                "deepseek", "дипсик", "дипсек"));
        m.put("grok", new Profile("Grok", "openai", "https://api.x.ai/v1", "grok-4", "8",
                "grok", "грок"));
        m.put("chatgpt", new Profile("ChatGPT", "openai", "https://api.openai.com/v1", "gpt-4o-mini", "a",
                "chatgpt", "gpt", "чатгпт", "чат гпт", "гпт", "жпт"));
        m.put("gemini", new Profile("Gemini", "openai", "https://generativelanguage.googleapis.com/v1beta/openai", "gemini-2.5-flash", "d",
                "gemini", "джемини", "гемини", "джеминай"));
        return m;
    }

    public Map<String, Profile> profiles = new LinkedHashMap<>();
    public String effort = "medium";
    /** all — если в сообщении никого не назвали, отвечают все напарники; mention — только тот, кого назвали */
    public String respondTo = "all";

    // старые поля (версия 1.0) — переносятся в профиль claude
    public String apiKey;
    public String baseUrl;
    public String model;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Settings instance;

    private static Path file() {
        return FMLPaths.CONFIGDIR.get().resolve("claudecompanion.json");
    }

    public static synchronized Settings get() {
        if (instance == null) {
            try {
                Path f = file();
                instance = Files.exists(f) ? GSON.fromJson(Files.readString(f), Settings.class) : new Settings();
            } catch (Exception e) {
                ClaudeCompanion.LOG.warn("Не удалось прочитать настройки, беру стандартные", e);
                instance = new Settings();
            }
            if (instance == null) instance = new Settings();
            instance.fillDefaults();
        }
        return instance;
    }

    private void fillDefaults() {
        if (profiles == null) profiles = new LinkedHashMap<>();
        presets().forEach(profiles::putIfAbsent);
        for (Profile p : profiles.values()) if (p.names == null) p.names = new ArrayList<>();
        Profile claude = profiles.get("claude");
        if (apiKey != null && !apiKey.isBlank() && !claude.hasKey()) claude.apiKey = apiKey;
        if (baseUrl != null && !baseUrl.isBlank() && !claude.hasBaseUrl()) claude.baseUrl = baseUrl;
        if (model != null && !model.isBlank()) claude.model = model;
        apiKey = null;
        baseUrl = null;
        model = null;
    }

    public static synchronized void save() {
        try {
            Files.createDirectories(file().getParent());
            Files.writeString(file(), GSON.toJson(get()));
        } catch (Exception e) {
            ClaudeCompanion.LOG.error("Не удалось сохранить настройки", e);
        }
    }

    /** Профиль по id (или имени, или прозвищу); null — нет такого. */
    public Profile find(String idOrName) {
        if (idOrName == null) return null;
        String q = idOrName.trim().toLowerCase(Locale.ROOT);
        if (profiles.containsKey(q)) return profiles.get(q);
        for (Profile p : profiles.values())
            if (p.name.equalsIgnoreCase(q) || p.names.stream().anyMatch(n -> n.equalsIgnoreCase(q))) return p;
        return null;
    }

    public String idOf(Profile p) {
        for (var e : profiles.entrySet()) if (e.getValue() == p) return e.getKey();
        return "claude";
    }

    /** Профиль напарника; если его удалили из настроек — Claude. */
    public Profile profile(String id) {
        Profile p = profiles.get(id);
        return p != null ? p : profiles.get("claude");
    }
}
