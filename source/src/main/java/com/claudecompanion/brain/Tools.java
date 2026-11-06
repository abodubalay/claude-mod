package com.claudecompanion.brain;

import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.Tool;
import com.claudecompanion.entity.Actions;
import com.claudecompanion.entity.CompanionEntity;
import com.claudecompanion.entity.CompanionTask;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Инструменты, которые Claude может вызывать, и их выполнение в игре. */
public final class Tools {
    private Tools() {}

    // ---------- схемы ----------

    private static Map<String, Object> str(String d) { return Map.of("type", "string", "description", d); }
    private static Map<String, Object> integer(String d) { return Map.of("type", "integer", "description", d); }
    private static Map<String, Object> bool(String d) { return Map.of("type", "boolean", "description", d); }
    private static Map<String, Object> strList(String d) { return Map.of("type", "array", "items", Map.of("type", "string"), "description", d); }
    private static Map<String, Object> oneOf(String d, String... values) { return Map.of("type", "string", "enum", List.of(values), "description", d); }

    /** props: пары имя -> схема; все поля обязательные, лишних нет (strict). */
    private static Tool tool(String name, String description, Object... props) {
        Tool.InputSchema.Properties.Builder pb = Tool.InputSchema.Properties.builder();
        List<String> required = new ArrayList<>();
        for (int i = 0; i < props.length; i += 2) {
            pb.putAdditionalProperty((String) props[i], JsonValue.from(props[i + 1]));
            required.add((String) props[i]);
        }
        Tool.InputSchema schema = Tool.InputSchema.builder()
                .properties(pb.build())
                .required(required)
                .putAdditionalProperty("additionalProperties", JsonValue.from(false))
                .build();
        return Tool.builder().name(name).description(description).inputSchema(schema).strict(true).build();
    }

    public static final List<Tool> DEFINITIONS = List.of(
            tool("say", "Написать сообщение в общий чат. Коротко, по-дружески, без markdown.",
                    "message", str("текст")),
            tool("get_status", "Своё состояние: позиция, здоровье, время, инвентарь, броня, где хозяин, кто рядом."),
            tool("look_around", "Найти ближайшие блоки. Можно id (minecraft:oak_log, create:zinc_ore) или тег "
                            + "(#minecraft:logs, #forge:ores/iron, #minecraft:coal_ores) — теги находят и блоки из модов.",
                    "blocks", strList("id или теги блоков"), "radius", integer("радиус 4..32")),
            tool("survey", "Перечислить все типы блоков вокруг — чтобы узнать id незнакомых блоков из модов.",
                    "radius", integer("радиус 2..10")),
            tool("follow_player", "Ходить за игроком (фоновое действие, длится, пока не вызовешь stop).",
                    "player", str("ник")),
            tool("go_to_player", "Подойти к игроку.", "player", str("ник")),
            tool("go_to", "Дойти до координат.", "x", integer("x"), "y", integer("y"), "z", integer("z")),
            tool("stop", "Остановить все действия (движение, бой, добычу, следование)."),
            tool("mine", "Добыть N блоков поблизости: сам дойдёт, возьмёт лучший инструмент, подберёт дроп. "
                            + "Блок — id или тег (#minecraft:logs, #forge:ores/iron).",
                    "block", str("id или тег"), "count", integer("1..64")),
            tool("craft", "Скрафтить предмет по любому рецепту верстака (включая рецепты модов). "
                            + "Для рецептов 3x3 нужен верстак рядом или в инвентаре.",
                    "item", str("id предмета, напр. minecraft:stone_pickaxe"), "count", integer("сколько")),
            tool("smelt", "Переплавить предметы (нужна печь рядом или в инвентаре и топливо в инвентаре).",
                    "item", str("id сырья, напр. minecraft:raw_iron"), "count", integer("сколько")),
            tool("equip", "Взять или надеть предмет из инвентаря.",
                    "item", str("id"), "slot", oneOf("куда", "hand", "off-hand", "armor")),
            tool("eat", "Съесть лучшую еду из инвентаря (восстанавливает здоровье)."),
            tool("attack", "Атаковать ближайшую сущность заданного типа (minecraft:zombie, или моб из мода) или игрока по нику.",
                    "target", str("id сущности или ник")),
            tool("set_guard_mode", "Режим охраны: сам атакует враждебных мобов поблизости (кроме криперов).",
                    "enabled", bool("вкл/выкл")),
            tool("give_to_player", "Подойти к игроку и отдать ему предметы.",
                    "player", str("ник"), "item", str("id"), "count", integer("сколько")),
            tool("place_block", "Поставить блок из инвентаря на координаты (в пределах 6 блоков).",
                    "item", str("id блока"), "x", integer("x"), "y", integer("y"), "z", integer("z")),
            tool("store_items", "Положить предметы в ближайший сундук/бочку/хранилище из мода (радиус 16). "
                            + "item=\"all\" — всё, кроме инструментов, брони и еды.",
                    "item", str("id или all"), "count", integer("сколько")),
            tool("take_items", "Взять предметы из ближайшего хранилища (радиус 16). item=\"list\" — посмотреть, что внутри.",
                    "item", str("id или list"), "count", integer("сколько")),
            tool("remember", "Сохранить заметку в долговременную память (база, договорённости, что любит игрок).",
                    "note", str("заметка")),
            tool("forget", "Удалить заметку из памяти по номеру.", "index", integer("номер")),
            tool("wait", "Подождать N секунд (1..30). Прервётся, если игрок что-то напишет.",
                    "seconds", integer("1..30"))
    );

    // ---------- выполнение ----------

    private static String s(Map<String, Object> in, String k) { return String.valueOf(in.get(k)); }
    private static int i(Map<String, Object> in, String k) { return ((Number) in.get(k)).intValue(); }
    @SuppressWarnings("unchecked")
    private static List<String> list(Map<String, Object> in, String k) { return (List<String>) in.get(k); }

    /** Запускает инструмент на серверном потоке и возвращает результат (может прийти позже — для долгих задач). */
    public static CompletableFuture<String> run(MinecraftServer server, CompanionEntity e, Brain brain, String name, Map<String, Object> in) {
        CompletableFuture<String> out = new CompletableFuture<>();
        server.execute(() -> {
            try {
                if (!e.isAlive()) { out.complete("Ты мёртв или исчез."); return; }
                Object r = exec(server, e, brain, name, in);
                if (r instanceof CompanionTask t) {
                    e.startTask(t);
                    t.result.whenComplete((v, ex) -> out.complete(ex != null ? "Ошибка: " + ex.getMessage() : v));
                } else {
                    out.complete(String.valueOf(r));
                }
            } catch (IllegalArgumentException ex) {
                out.complete("Ошибка: " + ex.getMessage());
            } catch (Exception ex) {
                out.complete("Ошибка: " + ex);
            }
        });
        return out;
    }

    private static ServerPlayer player(MinecraftServer server, String name) {
        ServerPlayer p = server.getPlayerList().getPlayerByName(name);
        if (p == null) throw new IllegalArgumentException("Игрок " + name + " не в сети.");
        return p;
    }

    private static Object exec(MinecraftServer server, CompanionEntity e, Brain brain, String name, Map<String, Object> in) {
        switch (name) {
            case "say" -> {
                String msg = s(in, "message");
                if (msg.length() > 250) msg = msg.substring(0, 250);
                server.getPlayerList().broadcastSystemMessage(Component.literal("<§6Claude§r> " + msg), false);
                return "Отправлено.";
            }
            case "get_status" -> { return Actions.status(e); }
            case "look_around" -> { return Actions.lookAround(e, list(in, "blocks"), i(in, "radius")); }
            case "survey" -> { return Actions.survey(e, i(in, "radius")); }
            case "follow_player" -> {
                ServerPlayer p = player(server, s(in, "player"));
                e.stopAll();
                e.setFollowTarget(p.getUUID());
                return "Иду за " + p.getGameProfile().getName() + ".";
            }
            case "go_to_player" -> {
                ServerPlayer p = player(server, s(in, "player"));
                if (p.level() != e.level()) return "Игрок в другом измерении.";
                return new Actions.GoTo(p.position(), 2.5, 90, null);
            }
            case "go_to" -> { return new Actions.GoTo(new Vec3(i(in, "x") + 0.5, i(in, "y"), i(in, "z") + 0.5), 1.5, 180, null); }
            case "stop" -> { e.stopAll(); return "Остановился."; }
            case "mine" -> {
                String b = s(in, "block");
                return new Actions.Mine(Actions.blockMatcher(b), b, Math.max(1, Math.min(64, i(in, "count"))));
            }
            case "craft" -> { return Actions.craft(e, s(in, "item"), Math.max(1, Math.min(64, i(in, "count")))); }
            case "smelt" -> {
                Item it = Actions.item(s(in, "item"));
                if (Actions.smeltRecipe(e, it) == null) return Actions.id(it) + " нельзя переплавить в печке.";
                if (!Actions.hasFurnace(e)) return "Нужна печь (minecraft:furnace) рядом или в инвентаре.";
                int n = Math.min(i(in, "count"), Actions.count(e.inventory, it));
                if (n <= 0) return "Нет " + Actions.id(it) + " в инвентаре.";
                return new Actions.Smelt(it, n);
            }
            case "equip" -> { return Actions.equip(e, s(in, "item"), s(in, "slot")); }
            case "eat" -> { return Actions.eatBest(e); }
            case "attack" -> {
                String t = s(in, "target");
                ServerPlayer p = server.getPlayerList().getPlayerByName(t);
                LivingEntity target = null;
                if (p != null && p.level() == e.level()) target = p;
                else {
                    String want = t.contains(":") ? t : "minecraft:" + t;
                    double best = 32;
                    for (Entity x : e.level().getEntities(e, e.getBoundingBox().inflate(32))) {
                        if (x instanceof LivingEntity le && x.isAlive() && !e.isOwner(x)
                                && Actions.entityName(x).equals(want) && e.distanceTo(x) < best) {
                            best = e.distanceTo(x);
                            target = le;
                        }
                    }
                }
                if (target == null) return "Не вижу " + t + " рядом.";
                if (e.isOwner(target)) return "На хозяина не нападаю.";
                return new Actions.Attack(target);
            }
            case "set_guard_mode" -> {
                e.guard = Boolean.TRUE.equals(in.get("enabled"));
                return e.guard ? "Охрана включена." : "Охрана выключена.";
            }
            case "give_to_player" -> {
                ServerPlayer p = player(server, s(in, "player"));
                Item it = Actions.item(s(in, "item"));
                int n = Math.min(i(in, "count"), Actions.count(e.inventory, it));
                if (n <= 0) return "Нет " + Actions.id(it) + " в инвентаре.";
                if (p.level() != e.level()) return "Игрок в другом измерении.";
                return new Actions.Give(p, it, n);
            }
            case "place_block" -> { return Actions.place(e, s(in, "item"), new BlockPos(i(in, "x"), i(in, "y"), i(in, "z"))); }
            case "store_items", "take_items" -> {
                BlockPos pos = Actions.findStorage(e, 16);
                if (pos == null) return "Нет сундука или хранилища в радиусе 16 блоков.";
                CompanionTask then = new Actions.AtStorage(pos, name.equals("store_items"), s(in, "item"), Math.max(1, i(in, "count")));
                return new Actions.GoTo(Vec3.atCenterOf(pos), 2.8, 60, then);
            }
            case "remember" -> {
                e.memory.add(s(in, "note"));
                while (e.memory.size() > 60) e.memory.remove(0);
                return "Запомнил.";
            }
            case "forget" -> {
                int idx = i(in, "index");
                if (idx < 0 || idx >= e.memory.size()) return "Нет такой заметки.";
                e.memory.remove(idx);
                return "Удалил.";
            }
            case "wait" -> { return new Actions.Wait(Math.max(1, Math.min(30, i(in, "seconds"))), brain::hasPending); }
            default -> { return "Нет инструмента " + name; }
        }
    }
}
