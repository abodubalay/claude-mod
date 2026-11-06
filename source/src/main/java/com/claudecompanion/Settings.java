package com.claudecompanion;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Настройки мода: лежат в config/claudecompanion.json на компьютере того, кто держит мир. */
public class Settings {
    public String apiKey = "";
    public String model = "claude-haiku-5-5";
    public String effort = "medium";
    /** all — отвечать на все сообщения хозяина, mention — только если есть имя бота */
    public String respondTo = "all";
    public List<String> names = new ArrayList<>(List.of("claude", "клод", "клоди", "клодик"));

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
            if (instance.names == null) instance.names = new ArrayList<>();
        }
        return instance;
    }

    public static synchronized void save() {
        try {
            Files.createDirectories(file().getParent());
            Files.writeString(file(), GSON.toJson(get()));
        } catch (Exception e) {
            ClaudeCompanion.LOG.error("Не удалось сохранить настройки", e);
        }
    }

    public boolean hasKey() {
        return apiKey != null && !apiKey.isBlank();
    }

    public String maskedKey() {
        if (!hasKey()) return "нет";
        return apiKey.substring(0, Math.min(10, apiKey.length())) + "…" + apiKey.substring(Math.max(0, apiKey.length() - 4));
    }
}
