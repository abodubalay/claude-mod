package com.claudecompanion;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.NotFoundException;
import com.anthropic.errors.PermissionDeniedException;
import com.anthropic.errors.UnauthorizedException;
import com.claudecompanion.brain.BrainManager;
import com.claudecompanion.entity.CompanionEntity;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.regex.Pattern;

/** /claude ... — команды мода. Ответы видит только тот, кто ввёл команду. */
public final class ClaudeCommand {
    private ClaudeCommand() {}

    /** Только для тестов: -Dclaudecompanion.debug=true включает /claude debugtool {"tool":..,"input":{..}} */
    private static final boolean DEBUG = Boolean.getBoolean("claudecompanion.debug");

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

    private static final Pattern KEY = Pattern.compile("^sk-ant-[A-Za-z0-9_-]{10,}$");

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("claude")
                .executes(ClaudeCommand::help)
                .then(Commands.literal("summon").requires(ClaudeCommand::isAdmin).executes(ClaudeCommand::summon))
                .then(Commands.literal("dismiss").executes(ClaudeCommand::dismiss))
                .then(Commands.literal("stop").executes(ClaudeCommand::stop))
                .then(Commands.literal("key").requires(ClaudeCommand::isAdmin)
                        .executes(ClaudeCommand::keyStatus)
                        .then(Commands.argument("value", StringArgumentType.greedyString()).executes(ClaudeCommand::key)))
                .then(Commands.literal("url").requires(ClaudeCommand::isAdmin)
                        .executes(ClaudeCommand::urlStatus)
                        .then(Commands.argument("address", StringArgumentType.greedyString()).executes(ClaudeCommand::url)))
                .then(Commands.literal("skin").requires(ClaudeCommand::isAdmin).executes(ClaudeCommand::skin))
                .then(Commands.literal("mode").requires(ClaudeCommand::isAdmin)
                        .then(Commands.literal("all").executes(c -> mode(c, "all")))
                        .then(Commands.literal("mention").executes(c -> mode(c, "mention"))))
                .then(Commands.literal("model").requires(ClaudeCommand::isAdmin)
                        .then(Commands.argument("id", StringArgumentType.word()).executes(ClaudeCommand::model)))
                .then(Commands.literal("debugtool").requires(s -> DEBUG && s.hasPermission(2))
                        .then(Commands.argument("json", StringArgumentType.greedyString()).executes(ClaudeCommand::debugTool)))
                .then(Commands.literal("effort").requires(ClaudeCommand::isAdmin)
                        .then(Commands.literal("low").executes(c -> effort(c, "low")))
                        .then(Commands.literal("medium").executes(c -> effort(c, "medium")))
                        .then(Commands.literal("high").executes(c -> effort(c, "high")))));
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

    private static int help(CommandContext<CommandSourceStack> c) {
        reply(c.getSource(), """
                §6Claude — напарник§r
                /claude summon — призвать напарника (или позвать к себе)
                /claude key sk-ant-... — задать API-ключ (виден только тебе)
                /claude key — есть ли ключ;  /claude key remove — удалить
                /claude stop — остановить;  /claude dismiss — отпустить
                /claude mode all|mention — отвечать на всё или только на «клод»
                /claude skin — применить свой скин из config/claudecompanion/skin.png
                /claude url https://... — сторонний сервер API (reset — официальный)
                /claude model claude-haiku-5-5 — модель;  /claude effort low|medium|high
                ПКМ по напарнику — его инвентарь. Пиши в чат как другу!""");
        return 1;
    }

    private static int summon(CommandContext<CommandSourceStack> c) {
        ServerPlayer p = c.getSource().getPlayer();
        if (p == null) { reply(c.getSource(), "Команду нужно вводить в игре."); return 0; }
        List<CompanionEntity> mine = BrainManager.ownedBy(p.getUUID());
        if (!mine.isEmpty()) {
            CompanionEntity e = mine.get(0);
            if (e.level() == p.level()) e.teleportTo(p.getX(), p.getY(), p.getZ());
            else e.changeDimension(p.serverLevel(), new net.minecraftforge.common.util.ITeleporter() {});
            reply(c.getSource(), "Claude уже здесь — позвал к тебе.");
            return 1;
        }
        CompanionEntity e = ClaudeCompanion.COMPANION.get().create(p.serverLevel());
        if (e == null) return 0;
        e.moveTo(p.getX() + 1, p.getY(), p.getZ() + 1, p.getYRot(), 0);
        e.setOwner(p.getUUID());
        p.serverLevel().addFreshEntity(e);
        if (Settings.get().hasKey()) {
            BrainManager.notify(e, "[событие] Тебя только что призвал " + p.getGameProfile().getName() + ". Поздоровайся коротко и иди за ним.");
        } else {
            reply(c.getSource(), "§eClaude призван, но ему нужен API-ключ: /claude key sk-ant-...");
        }
        return 1;
    }

    private static int dismiss(CommandContext<CommandSourceStack> c) {
        ServerPlayer p = c.getSource().getPlayer();
        if (p == null) return 0;
        List<CompanionEntity> mine = BrainManager.ownedBy(p.getUUID());
        if (mine.isEmpty()) { reply(c.getSource(), "У тебя нет напарника."); return 0; }
        for (CompanionEntity e : mine) {
            for (int i = 0; i < e.inventory.getContainerSize(); i++) {
                var s = e.inventory.removeItemNoUpdate(i);
                if (!s.isEmpty()) e.spawnAtLocation(s);
            }
            e.discard();
        }
        reply(c.getSource(), "Claude ушёл. Его вещи остались на земле.");
        return 1;
    }

    private static int stop(CommandContext<CommandSourceStack> c) {
        ServerPlayer p = c.getSource().getPlayer();
        if (p == null) return 0;
        BrainManager.ownedBy(p.getUUID()).forEach(CompanionEntity::stopAll);
        reply(c.getSource(), "Остановил.");
        return 1;
    }

    private static int keyStatus(CommandContext<CommandSourceStack> c) {
        Settings s = Settings.get();
        reply(c.getSource(), s.hasKey() ? "Ключ установлен: " + s.maskedKey() + ". Модель: " + s.model
                : "§eКлюча нет. Введи: /claude key sk-ant-...");
        return 1;
    }

    private static int key(CommandContext<CommandSourceStack> c) {
        String v = StringArgumentType.getString(c, "value").trim();
        CommandSourceStack src = c.getSource();
        if (v.equalsIgnoreCase("status")) return keyStatus(c);
        if (v.equalsIgnoreCase("remove") || v.equalsIgnoreCase("delete")) {
            Settings.get().apiKey = "";
            Settings.save();
            reply(src, "Ключ удалён.");
            return 1;
        }
        if (!KEY.matcher(v).matches()) {
            reply(src, "§cЭто не похоже на ключ Anthropic (он начинается с sk-ant-).");
            return 0;
        }
        reply(src, "Проверяю ключ...");
        MinecraftServer server = src.getServer();
        String model = Settings.get().model;
        String baseUrl = Settings.get().hasBaseUrl() ? Settings.get().baseUrl.trim() : "";
        Thread t = new Thread(() -> {
            String error = null;
            try {
                var builder = AnthropicOkHttpClient.builder().apiKey(v);
                if (!baseUrl.isEmpty()) builder.baseUrl(baseUrl);
                AnthropicClient client = builder.build();
                if (baseUrl.isEmpty()) {
                    client.models().retrieve(model); // бесплатная проверка
                } else {
                    // сторонний сервер: проверяем коротким запросом (несколько токенов)
                    client.messages().create(com.anthropic.models.messages.MessageCreateParams.builder()
                            .model(model).maxTokens(16L).addUserMessage("ping").build());
                }
            } catch (UnauthorizedException e) {
                error = "ключ неверный или отозван";
            } catch (PermissionDeniedException e) {
                error = "у ключа нет доступа";
            } catch (NotFoundException e) {
                error = "модель " + model + " недоступна для этого ключа — смени её: /claude model <id>";
            } catch (AnthropicIoException e) {
                error = "нет связи с " + (baseUrl.isEmpty() ? "api.anthropic.com" : baseUrl);
            } catch (Exception e) {
                error = e.getMessage();
            }
            String err = error;
            server.execute(() -> {
                if (err != null) {
                    reply(src, "§cКлюч не подошёл: " + err);
                } else {
                    Settings.get().apiKey = v;
                    Settings.save();
                    reply(src, "§aКлюч принят и сохранён. Призови напарника: /claude summon");
                }
            });
        }, "Claude-KeyCheck");
        t.setDaemon(true);
        t.start();
        return 1;
    }

    private static int urlStatus(CommandContext<CommandSourceStack> c) {
        Settings s = Settings.get();
        reply(c.getSource(), s.hasBaseUrl() ? "Сервер API: " + s.baseUrl + " (вернуть официальный: /claude url reset)"
                : "Сервер API: официальный api.anthropic.com");
        return 1;
    }

    private static int url(CommandContext<CommandSourceStack> c) {
        String v = StringArgumentType.getString(c, "address").trim();
        if (v.equalsIgnoreCase("reset") || v.equalsIgnoreCase("default")) {
            Settings.get().baseUrl = "";
            Settings.save();
            reply(c.getSource(), "Сервер API: официальный api.anthropic.com");
            return 1;
        }
        if (!v.startsWith("https://") && !v.startsWith("http://")) {
            reply(c.getSource(), "§cАдрес должен начинаться с https://");
            return 0;
        }
        while (v.endsWith("/")) v = v.substring(0, v.length() - 1);
        Settings.get().baseUrl = v;
        Settings.save();
        reply(c.getSource(), "§eСервер API: " + v + ". Запросы и чат с ботом теперь идут через этот сервер, а не напрямую в Anthropic. Теперь введи ключ: /claude key ...");
        return 1;
    }

    private static int skin(CommandContext<CommandSourceStack> c) {
        if (!java.nio.file.Files.exists(SkinSync.skinFile())) {
            reply(c.getSource(), "§eПоложи PNG-скин 64x64 сюда: " + SkinSync.skinFile().toAbsolutePath() + " и введи /claude skin ещё раз.");
            return 0;
        }
        for (ServerPlayer p : c.getSource().getServer().getPlayerList().getPlayers()) SkinSync.sendTo(p);
        reply(c.getSource(), "§aСкин обновлён у всех игроков.");
        return 1;
    }

    private static int mode(CommandContext<CommandSourceStack> c, String mode) {
        Settings.get().respondTo = mode;
        Settings.save();
        reply(c.getSource(), mode.equals("all") ? "Claude отвечает на все твои сообщения."
                : "Claude отвечает, только если в сообщении есть «клод» или «claude».");
        return 1;
    }

    private static int model(CommandContext<CommandSourceStack> c) {
        Settings.get().model = StringArgumentType.getString(c, "id");
        Settings.save();
        reply(c.getSource(), "Модель: " + Settings.get().model);
        return 1;
    }

    private static int effort(CommandContext<CommandSourceStack> c, String effort) {
        Settings.get().effort = effort;
        Settings.save();
        reply(c.getSource(), "Глубина размышлений: " + effort);
        return 1;
    }
}
