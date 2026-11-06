package com.claudecompanion.brain;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.PermissionDeniedException;
import com.anthropic.errors.RateLimitException;
import com.anthropic.errors.UnauthorizedException;
import com.anthropic.models.messages.*;
import com.claudecompanion.ClaudeCompanion;
import com.claudecompanion.Settings;
import com.claudecompanion.entity.Actions;
import com.claudecompanion.entity.CompanionEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.*;
import java.util.concurrent.*;

/**
 * «Мозг» одного напарника. Каждая просьба — отдельный эпизод разговора с Claude (только дозапись),
 * долгий контекст переносится через память напарника и недавний чат.
 */
public class Brain {
    private static final String SYSTEM = """
            Ты — живой напарник игрока в Minecraft (Java 1.20.1 с модами на Forge). Ты сам управляешь своим персонажем через инструменты: ходишь, добываешь, крафтишь, сражаешься, строишь и общаешься в чате.

            Как себя вести:
            - Ты друг и товарищ по игре, а не слуга: шути, предлагай идеи, делись планами.
            - Отвечай на языке игрока. Сообщения в чат — короткие (1–2 предложения), без markdown.
            - Перед длинной задачей коротко скажи, что собираешься делать; по завершении — что получилось.
            - Думай как опытный игрок: дерево → доски → верстак → палки → кирки → камень → печь → уголь/железо. Проверяй инвентарь перед крафтом.
            - В сборке есть моды. Их блоки и предметы имеют id вида modid:name. Если не знаешь id — используй survey, look_around с тегами (#forge:ores/..., #minecraft:logs) или спроси игрока. Рецепты модов на верстаке работают через craft; машины из модов ты использовать не умеешь — честно скажи об этом.
            - Если действие не удалось — подумай почему и попробуй иначе, а не повторяй то же самое.
            - Следи за здоровьем; ночью будь осторожнее.
            - Новые сообщения игрока могут прийти посреди работы — учитывай их сразу. Если просят остановиться — остановись.
            - Важное (координаты базы, договорённости, что любит игрок) сохраняй через remember.
            - Слушай только своего хозяина. С его друзьями будь дружелюбен.
            - Когда задача выполнена и сказать больше нечего — закончи ход без вызова инструментов. Если игроку нужно, чтобы ты был рядом, включи follow_player перед завершением.""";

    private final MinecraftServer server;
    private final CompanionEntity entity;
    private final ExecutorService thread = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Companion-Brain");
        t.setDaemon(true);
        return t;
    });
    private final ConcurrentLinkedQueue<String> pending = new ConcurrentLinkedQueue<>();
    private final Deque<String> recentChat = new ArrayDeque<>();
    private volatile boolean busy = false;
    private volatile boolean closed = false;
    private AnthropicClient client;
    private String clientKey;
    private String clientUrl;

    public Brain(MinecraftServer server, CompanionEntity entity) {
        this.server = server;
        this.entity = entity;
    }

    public boolean hasPending() { return !pending.isEmpty(); }

    public void notify(String text) {
        if (closed) return;
        synchronized (recentChat) {
            recentChat.addLast(text);
            while (recentChat.size() > 20) recentChat.removeFirst();
        }
        pending.add(text);
        if (!busy) {
            busy = true;
            thread.submit(this::runEpisodes);
        }
    }

    public void close() {
        closed = true;
        thread.shutdownNow();
    }

    /** Профиль нейросети этого напарника. */
    private Settings.Profile profile() {
        return Settings.get().profile(entity.getProfileId());
    }

    /** Строка чата от имени напарника: <Имя> текст, ник в цвете профиля. */
    public static Component chatLine(CompanionEntity e, String text) {
        Settings.Profile p = Settings.get().profile(e.getProfileId());
        String color = p.color == null || p.color.isEmpty() ? "6" : p.color.substring(0, 1);
        return Component.literal("<§" + color + e.displayName() + "§r> " + text);
    }

    private AnthropicClient client() {
        Settings.Profile cfg = profile();
        String key = cfg.apiKey;
        String url = cfg.hasBaseUrl() ? cfg.baseUrl.trim() : "";
        if (client == null || !Objects.equals(key, clientKey) || !Objects.equals(url, clientUrl)) {
            var b = AnthropicOkHttpClient.builder().apiKey(key);
            if (!url.isEmpty()) b.baseUrl(url);
            client = b.build();
            clientKey = key;
            clientUrl = url;
        }
        return client;
    }

    private void runEpisodes() {
        try {
            while (!closed && !pending.isEmpty()) {
                if (!profile().hasKey()) {
                    pending.clear();
                    tellOwner("§eУ " + entity.displayName() + " нет API-ключа. Введи: /claude key " + entity.getProfileId() + " ... (команду видишь только ты)");
                    break;
                }
                try {
                    episode();
                } catch (UnauthorizedException | PermissionDeniedException ex) {
                    pending.clear();
                    tellOwner("§cКлюч " + entity.displayName() + " не работает. Введи новый: /claude key " + entity.getProfileId() + " ...");
                } catch (RateLimitException ex) {
                    pending.clear();
                    say("Слишком много запросов, дай мне минутку.");
                } catch (AnthropicIoException | java.io.IOException ex) {
                    pending.clear();
                    say("Не могу связаться с нейросетью — проверь интернет и адрес (/claude url).");
                } catch (OpenAiChat.ApiError ex) {
                    pending.clear();
                    ClaudeCompanion.LOG.error("Ошибка API", ex);
                    if (ex.status == 401 || ex.status == 403) tellOwner("§cКлюч " + entity.displayName() + " не работает (" + ex.status + "). Введи новый: /claude key " + entity.getProfileId() + " ...");
                    else if (ex.status == 429) say("Слишком много запросов, дай мне минутку.");
                    else if (ex.status == 404) tellOwner("§cСервис не нашёл модель или адрес (404). Проверь: /claude list");
                    else say("Нейросеть ответила ошибкой " + ex.status + ".");
                } catch (InterruptedException ex) {
                    return;
                } catch (Exception ex) {
                    ClaudeCompanion.LOG.error("Ошибка мозга напарника", ex);
                    pending.clear();
                    say("Мой мозг споткнулся: " + ex.getClass().getSimpleName());
                }
            }
        } finally {
            busy = false;
            if (!closed && !pending.isEmpty()) {
                busy = true;
                thread.submit(this::runEpisodes);
            }
        }
    }

    private List<String> drain() {
        List<String> out = new ArrayList<>();
        String s;
        while ((s = pending.poll()) != null) out.add(s);
        return out;
    }

    private <T> T onServer(Callable<T> c) throws Exception {
        CompletableFuture<T> f = new CompletableFuture<>();
        server.execute(() -> {
            try { f.complete(c.call()); } catch (Exception ex) { f.completeExceptionally(ex); }
        });
        return f.get(10, TimeUnit.SECONDS);
    }

    private void episode() throws Exception {
        List<String> events = drain();
        if (events.isEmpty()) return;
        String history;
        synchronized (recentChat) {
            List<String> all = new ArrayList<>(recentChat);
            history = String.join("\n", all.subList(0, Math.max(0, all.size() - events.size())));
        }
        String state = onServer(() -> Actions.status(entity));
        String memory = onServer(() -> {
            if (entity.memory.isEmpty()) return "(пока пусто)";
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < entity.memory.size(); i++) sb.append(i).append(". ").append(entity.memory.get(i)).append('\n');
            return sb.toString();
        });
        String ownerName = onServer(() -> {
            ServerPlayer o = entity.getOwnerPlayer();
            return o == null ? "?" : o.getGameProfile().getName();
        });

        Settings.Profile me = profile();
        String team = onServer(() -> {
            List<String> others = new ArrayList<>();
            if (entity.getOwnerId() != null)
                for (CompanionEntity o : BrainManager.ownedBy(entity.getOwnerId()))
                    if (o != entity) others.add(o.displayName());
            return others.isEmpty() ? "" : "Другие напарники рядом: " + String.join(", ", others)
                    + ". Если сообщение обращено к ним по имени, а не к тебе, — не вмешивайся (просто закончи ход). "
                    + "Если обращаются ко всем — договоритесь и не делайте одно и то же.\n";
        });
        String first = "Твоё имя: " + entity.displayName() + " (в чате тебя зовут: " + String.join(", ", me.names) + "). "
                + "Ты работаешь на нейросети " + me.name + ". Твой хозяин: " + ownerName + ".\n" + team + "\n"
                + "Твоя память:\n" + memory + "\n\n"
                + "Недавний чат:\n" + (history.isBlank() ? "(нет)" : history) + "\n\n"
                + "Текущее состояние:\n" + state + "\n\n"
                + "Новое:\n" + String.join("\n", events);

        Settings.Profile cfg = me;
        String effort = Settings.get().effort;
        if (cfg.isOpenAi()) {
            episodeOpenAi(first, cfg);
            return;
        }

        List<MessageParam> messages = new ArrayList<>();
        messages.add(MessageParam.ofUser(first));

        for (int step = 0; step < 40 && !closed; step++) {
            MessageCreateParams.Builder b = MessageCreateParams.builder()
                    .model(cfg.model)
                    .maxTokens(16000L)
                    .system(SYSTEM)
                    .messages(messages)
                    .thinking(ThinkingConfigAdaptive.builder().build())
                    .outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.of(effort)).build())
                    .cacheControl(CacheControlEphemeral.builder().build());
            for (Tool t : Tools.DEFINITIONS) b.addTool(t);

            Message response = client().messages().create(b.build());
            messages.add(response.toParam());

            StopReason stop = response.stopReason().orElse(StopReason.END_TURN);
            if (stop.equals(StopReason.REFUSAL)) {
                say("Извини, с этим я помочь не могу.");
                return;
            }
            if (stop.equals(StopReason.PAUSE_TURN)) continue;

            List<ToolUseBlock> uses = new ArrayList<>();
            for (ContentBlock block : response.content()) block.toolUse().ifPresent(uses::add);

            if (uses.isEmpty() || stop.equals(StopReason.MAX_TOKENS)) {
                List<String> news = drain();
                if (!news.isEmpty() && !stop.equals(StopReason.MAX_TOKENS)) {
                    messages.add(MessageParam.ofUser("Новые сообщения:\n" + String.join("\n", news)));
                    continue;
                }
                return;
            }

            List<ContentBlockParam> results = new ArrayList<>();
            for (ToolUseBlock use : uses) {
                String result;
                boolean error = false;
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> input = use._input().convert(Map.class);
                    if (input == null) input = Map.of();
                    ClaudeCompanion.LOG.info("[Claude] {} {}", use.name(), input);
                    result = Tools.run(server, entity, this, use.name(), input).get(300, TimeUnit.SECONDS);
                } catch (TimeoutException ex) {
                    result = "Действие слишком долгое — прервано.";
                    server.execute(entity::stopAll);
                    error = true;
                } catch (InterruptedException ex) {
                    throw ex;
                } catch (Exception ex) {
                    result = "Ошибка: " + ex.getMessage();
                    error = true;
                }
                results.add(ContentBlockParam.ofToolResult(ToolResultBlockParam.builder()
                        .toolUseId(use.id()).content(result).isError(error).build()));
            }
            List<String> news = drain();
            if (!news.isEmpty())
                results.add(ContentBlockParam.ofText(TextBlockParam.builder()
                        .text("Новые сообщения/события:\n" + String.join("\n", news)).build()));
            messages.add(MessageParam.ofUserOfContentBlockParams(results));
        }
    }

    /** Тот же цикл «подумал → действие → результат», но для нейросетей в формате OpenAI. */
    @SuppressWarnings("unchecked")
    private void episodeOpenAi(String first, Settings.Profile cfg) throws Exception {
        com.google.gson.JsonArray messages = new com.google.gson.JsonArray();
        messages.add(OpenAiChat.msg("system", SYSTEM));
        messages.add(OpenAiChat.msg("user", first));
        com.google.gson.JsonArray tools = OpenAiChat.toolsJson();
        com.google.gson.Gson gson = new com.google.gson.Gson();

        for (int step = 0; step < 40 && !closed; step++) {
            com.google.gson.JsonObject choice = OpenAiChat.complete(cfg.baseUrl, cfg.apiKey, cfg.model, messages, tools);
            com.google.gson.JsonObject message = choice.getAsJsonObject("message");
            String finish = choice.has("finish_reason") && !choice.get("finish_reason").isJsonNull()
                    ? choice.get("finish_reason").getAsString() : "stop";
            // ассистентский ответ кладём обратно как есть (без лишних полей, которые не все сервисы принимают)
            com.google.gson.JsonObject assistant = new com.google.gson.JsonObject();
            assistant.addProperty("role", "assistant");
            assistant.add("content", message.has("content") ? message.get("content") : com.google.gson.JsonNull.INSTANCE);
            com.google.gson.JsonArray calls = message.has("tool_calls") && message.get("tool_calls").isJsonArray()
                    ? message.getAsJsonArray("tool_calls") : new com.google.gson.JsonArray();
            if (!calls.isEmpty()) assistant.add("tool_calls", calls);
            messages.add(assistant);

            if (calls.isEmpty()) {
                // модель ответила текстом без инструмента — если она не сказала это в чат, скажем за неё
                if (message.has("content") && message.get("content").isJsonPrimitive()) {
                    String text = message.get("content").getAsString().trim();
                    if (!text.isEmpty() && step == 0) say(text.length() > 250 ? text.substring(0, 250) : text);
                }
                List<String> news = drain();
                if (!news.isEmpty() && !"length".equals(finish)) {
                    messages.add(OpenAiChat.msg("user", "Новые сообщения:\n" + String.join("\n", news)));
                    continue;
                }
                return;
            }

            for (com.google.gson.JsonElement el : calls) {
                com.google.gson.JsonObject call = el.getAsJsonObject();
                com.google.gson.JsonObject fn = call.getAsJsonObject("function");
                String name = fn.get("name").getAsString();
                String result;
                try {
                    String args = fn.has("arguments") && !fn.get("arguments").isJsonNull() ? fn.get("arguments").getAsString() : "{}";
                    Map<String, Object> input = args.isBlank() ? new HashMap<>() : gson.fromJson(args, Map.class);
                    if (input == null) input = new HashMap<>();
                    ClaudeCompanion.LOG.info("[AI] {} {}", name, input);
                    result = Tools.run(server, entity, this, name, input).get(300, TimeUnit.SECONDS);
                } catch (TimeoutException ex) {
                    result = "Действие слишком долгое — прервано.";
                    server.execute(entity::stopAll);
                } catch (InterruptedException ex) {
                    throw ex;
                } catch (Exception ex) {
                    result = "Ошибка: " + ex.getMessage();
                }
                com.google.gson.JsonObject toolMsg = new com.google.gson.JsonObject();
                toolMsg.addProperty("role", "tool");
                toolMsg.addProperty("tool_call_id", call.has("id") ? call.get("id").getAsString() : name);
                toolMsg.addProperty("content", result);
                messages.add(toolMsg);
            }
            List<String> news = drain();
            if (!news.isEmpty()) messages.add(OpenAiChat.msg("user", "Новые сообщения/события:\n" + String.join("\n", news)));
        }
    }

    private void say(String text) {
        server.execute(() -> server.getPlayerList().broadcastSystemMessage(chatLine(entity, text), false));
    }

    private void tellOwner(String text) {
        server.execute(() -> {
            ServerPlayer o = entity.getOwnerPlayer();
            if (o != null) o.sendSystemMessage(Component.literal(text));
        });
    }
}
