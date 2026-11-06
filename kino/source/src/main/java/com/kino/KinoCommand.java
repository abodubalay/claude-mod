package com.kino;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** /kino scene | laugh | credits | off | on — для проверки, только с читами. */
public final class KinoCommand {
    private KinoCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("kino").requires(s -> s.hasPermission(2))
                .then(Commands.literal("scene").executes(c -> { var p = player(c); Cutscenes.start(p, Cutscenes.randomAction()); return 1; }))
                .then(Commands.literal("laugh").executes(c -> { Sitcom.react(player(c), Sitcom.LAUGH, true); return 1; }))
                .then(Commands.literal("credits").executes(c -> { Credits.show(player(c)); return 1; }))
                .then(Commands.literal("off").executes(c -> toggle(c, true)))
                .then(Commands.literal("on").executes(c -> toggle(c, false))));
    }

    private static ServerPlayer player(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        return c.getSource().getPlayerOrException();
    }

    private static int toggle(CommandContext<CommandSourceStack> c, boolean off) throws CommandSyntaxException {
        KinoData.setOff(player(c), off);
        c.getSource().sendSuccess(() -> Component.literal(off ? "Кино выключено." : "Кино включено."), false);
        return 1;
    }
}
