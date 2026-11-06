package com.babushki.command;

import com.babushki.logic.BabushkiManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Locale;

/** /babushki ... — только с читами (уровень прав 2). */
public final class BabushkiCommand {
    private BabushkiCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("babushki")
                .requires(s -> s.hasPermission(2))
                .then(Commands.literal("spawn").executes(BabushkiCommand::spawn))
                .then(Commands.literal("rep")
                        .executes(BabushkiCommand::showRep)
                        .then(Commands.literal("set")
                                .then(Commands.argument("value", IntegerArgumentType.integer(-10, 10))
                                        .executes(BabushkiCommand::setRep))))
                .then(Commands.literal("gift")
                        .executes(c -> gift(c, null))
                        .then(Commands.argument("type", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(new String[]{"pie", "socks", "scarf", "jam", "avoska"}, b))
                                .executes(c -> gift(c, StringArgumentType.getString(c, "type")))))
                .then(Commands.literal("gossip").executes(BabushkiCommand::gossip))
                .then(Commands.literal("on").executes(c -> toggle(c, true)))
                .then(Commands.literal("off").executes(c -> toggle(c, false))));
    }

    private static void ok(CommandContext<CommandSourceStack> c, String msg) {
        c.getSource().sendSuccess(() -> Component.literal(msg), false);
    }

    private static int spawn(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        ok(c, BabushkiManager.get().commandSpawn(p));
        return 1;
    }

    private static int showRep(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        int rep = BabushkiManager.get().getRep(p);
        ok(c, "Твоя репутация у бабушек: " + rep + " — «" + BabushkiManager.tierName(rep) + "» (от -10 до +10)");
        return rep;
    }

    private static int setRep(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        int v = IntegerArgumentType.getInteger(c, "value");
        BabushkiManager.get().setRep(p, v);
        ok(c, "Репутация у бабушек теперь: " + v + " — «" + BabushkiManager.tierName(v) + "»");
        return 1;
    }

    private static int gift(CommandContext<CommandSourceStack> c, String type) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        BabushkiManager.GiftType g = null;
        if (type != null) {
            try {
                g = BabushkiManager.GiftType.valueOf(type.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                c.getSource().sendFailure(Component.literal("Не знаю такого подарка. Есть: pie, socks, scarf, jam, avoska"));
                return 0;
            }
        }
        String res = BabushkiManager.get().commandGift(p, g);
        if (res == null) {
            c.getSource().sendFailure(Component.literal("Бабушек рядом нет (ближе 32 блоков). Сначала /babushki spawn, и чтобы был день."));
            return 0;
        }
        if (res.isEmpty()) {
            c.getSource().sendFailure(Component.literal("Бабушка сейчас занята — попробуй через пару секунд."));
            return 0;
        }
        ok(c, "Бабушка несёт подарок: " + res.toLowerCase(Locale.ROOT));
        return 1;
    }

    private static int gossip(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        if (!BabushkiManager.get().commandGossip(p)) {
            c.getSource().sendFailure(Component.literal("Бабушек рядом нет (ближе 32 блоков)."));
            return 0;
        }
        return 1;
    }

    private static int toggle(CommandContext<CommandSourceStack> c, boolean on) {
        BabushkiManager.get().setEnabled(on);
        ok(c, on ? "Бабушки включены. Скоро выйдут на лавочку!" : "Бабушки выключены. Во дворе тишина...");
        return 1;
    }
}
