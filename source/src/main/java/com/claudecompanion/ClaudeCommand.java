package com.claudecompanion;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.NotFoundException;
import com.anthropic.errors.PermissionDeniedException;
import com.anthropic.errors.UnauthorizedException;
import com.claudecompanion.brain.BrainManager;
import com.claudecompanion.brain.OpenAiChat;
import com.claudecompanion.entity.CompanionEntity;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * /claude ... — команды мода. Ответы видит только тот, кто ввёл команду.
 * <нейросеть> — id профиля: claude, deepseek, grok, chatgpt, gemini или свой (/claude new).
 */
public final class ClaudeCommand {
    private ClaudeCommand() {}

    /** Только для тестов: -Dclaudecompanion.debug=true включает /claude debugtool {"tool":..,"input":{..}} */
    private static final boolean DEBUG = Boolean.getBoolean("claudecompanion.debug");

    private static RequiredArgumentBuilder<CommandSourceStack, String> ai() {
        return Commands.argument("ai", StringArgumentType.word())
                .suggests((c, b) -> SharedSuggestionProvider.suggest(Settings.get().profiles.keySet(), b));
    }

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("claude")
                .executes(ClaudeCommand::help)
                .then(Commands.literal("summon").requires(ClaudeCommand::isAdmin)
                        .executes(c -> summon(c, "claude"))
                        .then(ai().executes(c -> summon(c, StringArgumentType.getString(c, "ai")))))
                .then(Commands.literal("dismiss")
                        .executes(c -> dismiss(c, null))
                        .then(ai().executes(c -> dismiss(c, StringArgumentType.getString(c, "ai")))))
                .then(Commands.literal("stop").executes(ClaudeCommand::stop))
                .then(Commands.literal("list").requires(ClaudeCommand::isAdmin).executes(ClaudeCommand::list))
                .then(Commands.literal("key").requires(ClaudeCommand::isAdmin)
                        .executes(ClaudeCommand::list)
                        .then(ai().executes(ClaudeCommand::keyLegacy)
                                .then(Commands.argument("value", StringArgumentType.greedyString()).executes(ClaudeCommand::key))))
                .then(Commands.literal("url").requires(ClaudeCommand::isAdmin)
                        .then(ai().then(Commands.argument("value", StringArgumentType.greedyString()).executes(ClaudeCommand::url))))
                .then(Commands.literal("model").requires(ClaudeCommand::isAdmin)
                        .then(ai().then(Commands.argument("value", StringArgumentType.greedyString()).executes(ClaudeCommand::model))))
                .then(Commands.literal("names").requires(ClaudeCommand::isAdmin)
                        .then(ai().then(Commands.argument("value", StringArgumentType.greedyString()).executes(ClaudeCommand::names))))
                .then(Commands.literal("new").requires(ClaudeCommand::isAdmin)
                        .then(Commands.argument("id", StringArgumentType.word())
                                .then(Commands.argument("name", StringArgumentType.greedyString()).executes(ClaudeCommand::newProfile))))
                .then(Commands.literal("skin").requires(ClaudeCommand::isAdmin).executes(ClaudeCommand::skin))
                .then(Commands.literal("mode").requires(ClaudeCommand::isAdmin)
                        .then(Commands.literal("all").executes(c -> mode(c, "all")))
                        .then(Commands.literal("mention").executes(c -> mode(c, "mention"))))
                .then(Commands.literal("effort").requires(ClaudeCommand::isAdmin)
                        .then(Commands.literal("low").executes(c -> effort(c, "low")))
                        .then(Commands.literal("medium").executes(c -> effort(c, "medium")))
                        .then(Commands.literal("high").executes(c -> effort(c, "high"))))
                .then(Commands.literal("debugtool").requires(s -> DEBUG && s.hasPermission(2))
                        .then(Commands.argument("json", StringArgumentType.greedyString()).executes(ClaudeCommand::debugTool))));
    }

    /** Хозяин мира (тот, кто открыл его для сети) или оператор сервера. */
    private static boolean isAdmin(CommandSourceStack src) {
        if (src.hasPermission(2)) return true;
        ServerPlayer p = src.getPlayer();
        return p != null && src.getServer().isSingleplayerOwner(p.getGameProfile());
    }

    private static void reply(CommandSourceStack src, String text) {
        src.sendSuccess(() -> Component.literal(text), false);
    }

    /** Найти профиль по аргументу; при ошибке пишет подсказку и возвращает null. */
    private static Settings.Profile profileArg(CommandContext<CommandSourceStack> c) {
        String id = StringArgumentType.getString(c, "ai");
        Settings.Profile p = Settings.get().find(id);
        if (p == null) reply(c.getSource(), "§cНет нейросети «" + id + "». Есть: " + String.join(", ", Settings.get().profiles.keySet())
                + ". Свою можно добавить: /claude new <id> <Имя>");
        return p;
    }

    private static int help(CommandContext<CommandSourceStack> c) {
        reply(c.getSource(), """
                §6Напарники-нейросети§r (claude, deepseek, grok, chatgpt, gemini)
                /claude key <нейросеть> <ключ> — задать ключ (виден только тебе)
                /claude summon <нейросеть> — призвать (или позвать к себе)
                /claude list — все нейросети: ключ, сервер, модель
                /claude stop — остановить всех;  /claude dismiss [нейросеть] — отпустить
                /claude url <нейросеть> https://... — адрес сервера (reset — стандартный)
                /claude model <нейросеть> <модель>;  /claude names <нейросеть> имя1, имя2
                /claude new <id> <Имя> — своя нейросеть (формат OpenAI)
                /claude mode all|mention — если никого не назвали: отвечают все / никто
                /claude skin — свои скины из config/claudecompanion/skins/<нейросеть>.png
                Обращайся по имени: «дипсик, копай», «грок, за мной». ПКМ — инвентарь.""");
        return 1;
    }

    // ---------- напарники ----------

    private static int summon(CommandContext<CommandSourceStack> c, String id) {
        ServerPlayer p = c.getSource().getPlayer();
        if (p == null) { reply(c.getSource(), "Команду нужно вводить в игре."); return 0; }
        Settings cfg = Settings.get();
        Settings.Profile prof = cfg.find(id);
        if (prof == null) {
            reply(c.getSource(), "§cНет нейросети «" + id + "». Есть: " + String.join(", ", cfg.profiles.keySet()));
            return 0;
        }
        String pid = cfg.idOf(prof);
        for (CompanionEntity e : BrainManager.ownedBy(p.getUUID())) {
            if (!pid.equals(e.getProfileId())) continue;
            if (e.level() == p.level()) e.teleportTo(p.getX(), p.getY(), p.getZ());
            else e.changeDimension(p.serverLevel(), new net.minecraftforge.common.util.ITeleporter() {});
            reply(c.getSource(), prof.name + " уже здесь — позвал к тебе.");
            return 1;
        }
        CompanionEntity e = ClaudeCompanion.COMPANION.get().create(p.serverLevel());
        if (e == null) return 0;
        e.moveTo(p.getX() + 1, p.getY(), p.getZ() + 1, p.getYRot(), 0);
        e.setOwner(p.getUUID());
        e.setProfile(pid);
        p.serverLevel().addFreshEntity(e);
        if (prof.hasKey()) {
            BrainManager.notify(e, "[событие] Тебя только что призвал " + p.getGameProfile().getName() + ". Поздоровайся коротко и иди за ним.");
        } else {
            reply(c.getSource(), "§e" + prof.name + " призван, но ему нужен ключ: /claude key " + pid + " <ключ>");
        }
        return 1;
    }

    private static int dismiss(CommandContext<CommandSourceStack> c, String id) {
        ServerPlayer p = c.getSource().getPlayer();
        if (p == null) return 0;
        Settings.Profile prof = id == null ? null : Settings.get().find(id);
        String pid = prof == null ? null : Settings.get().idOf(prof);
        int n = 0;
        for (CompanionEntity e : BrainManager.ownedBy(p.getUUID())) {
            if (pid != null && !pid.equals(e.getProfileId())) continue;
            for (int i = 0; i < e.inventory.getContainerSize(); i++) {
                var s = e.inventory.removeItemNoUpdate(i);
                if (!s.isEmpty()) e.spawnAtLocation(s);
            }
            e.discard();
            n++;
        }
        reply(c.getSource(), n == 0 ? "Таких напарников у тебя нет." : "Отпустил: " + n + ". Их вещи остались на земле.");
        return n;
    }

    private static int stop(CommandContext<CommandSourceStack> c) {
        ServerPlayer p = c.getSource().getPlayer();
        if (p == null) return 0;
        BrainManager.ownedBy(p.getUUID()).forEach(CompanionEntity::stopAll);
        reply(c.getSource(), "Остановил всех.");
        return 1;
    }

    // ---------- настройки нейросетей ----------

    private static int list(CommandContext<CommandSourceStack> c) {
        StringBuilder sb = new StringBuilder("§6Нейросети:§r");
        Settings cfg = Settings.get();
        cfg.profiles.forEach((id, p) -> sb.append("\n§").append(p.color == null || p.color.isEmpty() ? "f" : p.color.substring(0, 1))
                .append(p.name).append("§r [").append(id).append("] ключ: ").append(p.hasKey() ? "§a" + p.maskedKey() + "§r" : "§cнет§r")
                .append(", модель: ").append(p.model).append(", сервер: ").append(p.server())
                .append(", имена: ").append(String.join("/", p.names)));
        sb.append("\nЕсли никого не назвали: ").append("all".equals(cfg.respondTo) ? "отвечают все" : "никто не отвечает");
        reply(c.getSource(), sb.toString());
        return 1;
    }

    /** /claude key sk-ant-... — старый вариант без названия нейросети: это ключ Claude. */
    private static int keyLegacy(CommandContext<CommandSourceStack> c) {
        String v = StringArgumentType.getString(c, "ai");
        if (v.startsWith("sk-ant-")) return checkAndSaveKey(c.getSource(), Settings.get().profiles.get("claude"), v);
        Settings.Profile p = profileArg(c);
        if (p == null) return 0;
        reply(c.getSource(), p.name + ": ключ " + (p.hasKey() ? p.maskedKey() : "§cне задан§r") + ". Задать: /claude key " + Settings.get().idOf(p) + " <ключ>");
        return 1;
    }

    private static int key(CommandContext<CommandSourceStack> c) {
        Settings.Profile p = profileArg(c);
        if (p == null) return 0;
        String v = StringArgumentType.getString(c, "value").trim();
        if (v.equalsIgnoreCase("remove") || v.equalsIgnoreCase("delete")) {
            p.apiKey = "";
            Settings.save();
            reply(c.getSource(), "Ключ " + p.name + " удалён.");
            return 1;
        }
        if (v.contains(" ") || v.length() < 4) {
            reply(c.getSource(), "§cЭто не похоже на ключ. Вставь его целиком, без пробелов.");
            return 0;
        }
        return checkAndSaveKey(c.getSource(), p, v);
    }

    /** Проверяем ключ коротким запросом и сохраняем, если он работает. */
    private static int checkAndSaveKey(CommandSourceStack src, Settings.Profile p, String key) {
        if (p.isOpenAi() && !p.hasBaseUrl()) {
            reply(src, "§cУ " + p.name + " не задан адрес сервера: /claude url " + Settings.get().idOf(p) + " https://...");
            return 0;
        }
        reply(src, "Проверяю ключ " + p.name + "...");
        MinecraftServer server = src.getServer();
        String model = p.model;
        String baseUrl = p.hasBaseUrl() ? p.baseUrl.trim() : "";
        boolean openAi = p.isOpenAi();
        Thread t = new Thread(() -> {
            String error = null;
            try {
                if (openAi) {
                    com.google.gson.JsonArray msgs = new com.google.gson.JsonArray();
                    msgs.add(OpenAiChat.msg("user", "ping"));
                    OpenAiChat.complete(baseUrl, key, model, msgs, null);
                } else {
                    var builder = AnthropicOkHttpClient.builder().apiKey(key);
                    if (!baseUrl.isEmpty()) builder.baseUrl(baseUrl);
                    AnthropicClient client = builder.build();
                    if (baseUrl.isEmpty()) client.models().retrieve(model); // бесплатная проверка
                    else client.messages().create(com.anthropic.models.messages.MessageCreateParams.builder()
                            .model(model).maxTokens(16L).addUserMessage("ping").build());
                }
            } catch (UnauthorizedException e) {
                error = "ключ неверный или отозван";
            } catch (PermissionDeniedException e) {
                error = "у ключа нет доступа";
            } catch (NotFoundException e) {
                error = "модель " + model + " недоступна — смени: /claude model " + Settings.get().idOf(p) + " <модель>";
            } catch (OpenAiChat.ApiError e) {
                error = switch (e.status) {
                    case 401, 403 -> "ключ неверный или нет доступа";
                    case 402 -> "на счёте нет денег";
                    case 404 -> "сервер не нашёл модель " + model + " или адрес неверный";
                    case 429 -> "лимит запросов или кончились деньги";
                    default -> e.getMessage();
                };
            } catch (AnthropicIoException | java.io.IOException e) {
                error = "нет связи с " + (baseUrl.isEmpty() ? "api.anthropic.com" : baseUrl);
            } catch (Exception e) {
                error = e.getMessage();
            }
            String err = error;
            server.execute(() -> {
                if (err != null) {
                    reply(src, "§cКлюч " + p.name + " не подошёл: " + err);
                } else {
                    p.apiKey = key;
                    Settings.save();
                    reply(src, "§aКлюч " + p.name + " принят. Призови: /claude summon " + Settings.get().idOf(p));
                }
            });
        }, "Companion-KeyCheck");
        t.setDaemon(true);
        t.start();
        return 1;
    }

    private static int url(CommandContext<CommandSourceStack> c) {
        Settings.Profile p = profileArg(c);
        if (p == null) return 0;
        String v = StringArgumentType.getString(c, "value").trim();
        String id = Settings.get().idOf(p);
        if (v.equalsIgnoreCase("reset") || v.equalsIgnoreCase("default")) {
            Settings.Profile preset = Settings.presets().get(id);
            p.baseUrl = preset != null ? preset.baseUrl : "";
            if (preset != null) p.provider = preset.provider;
            Settings.save();
            reply(c.getSource(), p.name + ": сервер " + p.server());
            return 1;
        }
        if (!v.startsWith("https://") && !v.startsWith("http://")) {
            reply(c.getSource(), "§cАдрес должен начинаться с https:// (или http:// для своей нейросети на компьютере)");
            return 0;
        }
        while (v.endsWith("/")) v = v.substring(0, v.length() - 1);
        p.baseUrl = v;
        // адрес со словом anthropic — сервер в формате Claude, иначе — формат OpenAI
        if (v.toLowerCase(Locale.ROOT).contains("anthropic")) p.provider = "anthropic";
        else p.provider = "openai";
        Settings.save();
        reply(c.getSource(), "§e" + p.name + ": сервер " + v + " (формат " + (p.isOpenAi() ? "OpenAI" : "Anthropic")
                + "). Чат с ним идёт через этот сервер. Теперь введи ключ: /claude key " + id + " <ключ>");
        return 1;
    }

    private static int model(CommandContext<CommandSourceStack> c) {
        Settings.Profile p = profileArg(c);
        if (p == null) return 0;
        p.model = StringArgumentType.getString(c, "value").trim();
        Settings.save();
        reply(c.getSource(), p.name + ": модель " + p.model);
        return 1;
    }

    private static int names(CommandContext<CommandSourceStack> c) {
        Settings.Profile p = profileArg(c);
        if (p == null) return 0;
        List<String> list = new ArrayList<>();
        for (String n : StringArgumentType.getString(c, "value").split(","))
            if (!n.isBlank()) list.add(n.trim().toLowerCase(Locale.ROOT));
        p.names = list;
        Settings.save();
        reply(c.getSource(), p.name + " откликается на: " + String.join(", ", list));
        return 1;
    }

    private static int newProfile(CommandContext<CommandSourceStack> c) {
        String id = StringArgumentType.getString(c, "id").toLowerCase(Locale.ROOT);
        String name = StringArgumentType.getString(c, "name").trim();
        if (!id.matches("[a-z0-9_]{2,20}")) {
            reply(c.getSource(), "§cid — латиницей, например mistral или my_ai");
            return 0;
        }
        Settings cfg = Settings.get();
        Settings.Profile p = cfg.profiles.getOrDefault(id, new Settings.Profile());
        p.name = name;
        p.provider = "openai";
        if (p.model == null) p.model = "";
        if (p.names == null || p.names.isEmpty()) p.names = new ArrayList<>(List.of(id, name.toLowerCase(Locale.ROOT)));
        if (p.color == null) p.color = "b";
        cfg.profiles.put(id, p);
        Settings.save();
        reply(c.getSource(), "§aДобавил " + name + " [" + id + "]. Дальше:\n/claude url " + id + " https://адрес/v1\n/claude model " + id
                + " <модель>\n/claude key " + id + " <ключ>\n/claude summon " + id);
        return 1;
    }

    private static int skin(CommandContext<CommandSourceStack> c) {
        int n = 0;
        for (ServerPlayer p : c.getSource().getServer().getPlayerList().getPlayers()) n = SkinSync.sendTo(p);
        reply(c.getSource(), n > 0 ? "§aСкины обновлены у всех игроков (" + n + " шт.)."
                : "§eПоложи PNG 64x64 в " + SkinSync.skinsDir().toAbsolutePath() + " с именем нейросети (claude.png, deepseek.png, grok.png...) и введи /claude skin ещё раз.");
        return n > 0 ? 1 : 0;
    }

    private static int mode(CommandContext<CommandSourceStack> c, String mode) {
        Settings.get().respondTo = mode;
        Settings.save();
        reply(c.getSource(), mode.equals("all") ? "Если в сообщении никого не назвали — отвечают все напарники."
                : "Напарники отвечают, только если назвать их по имени.");
        return 1;
    }

    private static int effort(CommandContext<CommandSourceStack> c, String effort) {
        Settings.get().effort = effort;
        Settings.save();
        reply(c.getSource(), "Глубина размышлений (для Claude): " + effort);
        return 1;
    }

    @SuppressWarnings("unchecked")
    private static int debugTool(CommandContext<CommandSourceStack> c) {
        var json = new com.google.gson.Gson().fromJson(StringArgumentType.getString(c, "json"), java.util.Map.class);
        String tool = (String) json.get("tool");
        var input = (java.util.Map<String, Object>) json.getOrDefault("input", java.util.Map.of());
        for (var level : c.getSource().getServer().getAllLevels())
            for (var ent : level.getAllEntities())
                if (ent instanceof CompanionEntity e) {
                    var brain = BrainManager.brain(e);
                    com.claudecompanion.brain.Tools.run(c.getSource().getServer(), e, brain, tool, input)
                            .thenAccept(r -> ClaudeCompanion.LOG.info("DEBUGTOOL {} -> {}", tool, r));
                    return 1;
                }
        ClaudeCompanion.LOG.info("DEBUGTOOL: нет напарника");
        return 0;
    }
}
