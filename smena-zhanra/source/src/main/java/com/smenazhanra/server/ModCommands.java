package com.smenazhanra.server;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.smenazhanra.Genre;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Arrays;

/**
 * /zhanr start &lt;жанр&gt; [игрок], /zhanr random [игрок], /zhanr on, /zhanr off.
 * Требуют права оператора (читы).
 */
public class ModCommands {

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("zhanr")
                .requires(s -> s.hasPermission(2))
                .then(Commands.literal("start")
                        .then(Commands.argument("genre", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(Arrays.stream(Genre.values()).map(g -> g.id), b))
                                .executes(c -> start(c, c.getSource().getPlayerOrException(), StringArgumentType.getString(c, "genre")))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(c -> start(c, EntityArgument.getPlayer(c, "player"), StringArgumentType.getString(c, "genre"))))))
                .then(Commands.literal("random")
                        .executes(c -> random(c, c.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(c -> random(c, EntityArgument.getPlayer(c, "player")))))
                .then(Commands.literal("on").executes(c -> setAuto(c, true)))
                .then(Commands.literal("off").executes(c -> setAuto(c, false))));
    }

    private static int start(CommandContext<CommandSourceStack> c, ServerPlayer p, String id) {
        Genre g = Genre.byId(id);
        if (g == null) {
            c.getSource().sendFailure(Component.literal("Нет такого жанра. Есть: novella, sud, ritm, viktorina, fighting"));
            return 0;
        }
        return launch(c, p, g);
    }

    private static int random(CommandContext<CommandSourceStack> c, ServerPlayer p) throws CommandSyntaxException {
        Genre g = GenreManager.randomGenre(p.getRandom(), true);
        if (g == null) g = GenreManager.randomGenre(p.getRandom(), false);
        return launch(c, p, g);
    }

    private static int launch(CommandContext<CommandSourceStack> c, ServerPlayer p, Genre g) {
        if (!GenreManager.begin(p, g)) {
            c.getSource().sendFailure(Component.literal(p.getGameProfile().getName() + " уже в другом жанре!"));
            return 0;
        }
        c.getSource().sendSuccess(() -> Component.literal("Смена жанра для " + p.getGameProfile().getName() + ": " + g.title), true);
        return 1;
    }

    private static int setAuto(CommandContext<CommandSourceStack> c, boolean on) {
        WorldData wd = WorldData.get(c.getSource().getServer());
        wd.autoEnabled = on;
        wd.setDirty();
        c.getSource().sendSuccess(() -> Component.literal(on
                ? "Автоматическая смена жанра ВКЛЮЧЕНА."
                : "Автоматическая смена жанра ВЫКЛЮЧЕНА. Включить обратно: /zhanr on"), true);
        return 1;
    }
}
