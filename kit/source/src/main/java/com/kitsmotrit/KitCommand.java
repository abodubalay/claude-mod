package com.kitsmotrit;

import com.kitsmotrit.entity.MaidWhale;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** /kit spawn | off | on | simpatia [set N] | reset — для проверки, только с читами. */
public final class KitCommand {
    private KitCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("kit").requires(s -> s.hasPermission(2))
                .executes(KitCommand::help)
                .then(Commands.literal("spawn").executes(KitCommand::spawn))
                .then(Commands.literal("off").executes(c -> toggle(c, true)))
                .then(Commands.literal("on").executes(c -> toggle(c, false)))
                .then(Commands.literal("simpatia").executes(KitCommand::show)
                        .then(Commands.literal("set").then(Commands.argument("value", IntegerArgumentType.integer(0, 1000))
                                .executes(KitCommand::set))))
                .then(Commands.literal("reset").executes(KitCommand::reset)));
    }

    private static void reply(CommandSourceStack s, String t) {
        s.sendSuccess(() -> Component.literal(t), false);
    }

    private static int help(CommandContext<CommandSourceStack> c) {
        reply(c.getSource(), """
                §bКит смотрит§r
                /kit spawn — вызвать кита
                /kit off, /kit on — выключить/включить появления
                /kit simpatia — симпатия;  /kit simpatia set <число>
                /kit reset — сбросить симпатию""");
        return 1;
    }

    private static int spawn(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        MaidWhale old = MaidWhale.ofPlayer(p);
        if (old != null) old.discard(); // один кит на игрока
        MaidWhale.spawnFor(p);
        if (!GameEvents.canSpawnFor(p)) reply(c.getSource(), "§7(сам по себе кит прилетает только под открытым небом)");
        return 1;
    }

    private static int toggle(CommandContext<CommandSourceStack> c, boolean off) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        Simpatia.setOff(p, off);
        reply(c.getSource(), off ? "Появления кита выключены." : "Появления кита включены.");
        return 1;
    }

    private static int show(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        int s = Simpatia.get(p);
        reply(c.getSource(), "Симпатия кита: §d" + s + "§r («" + Simpatia.levelName(s) + "»). Шанс появления: "
                + String.format("%.0f", Simpatia.chance(p)) + "% раз в " + KitConfig.CHECK_INTERVAL.get() + " с"
                + (Simpatia.isOff(p) ? ". §cПоявления выключены" : ""));
        return 1;
    }

    private static int set(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        Simpatia.set(p, IntegerArgumentType.getInteger(c, "value"));
        return show(c);
    }

    private static int reset(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        Simpatia.set(p, 0);
        reply(c.getSource(), "Симпатия сброшена.");
        return 1;
    }
}
