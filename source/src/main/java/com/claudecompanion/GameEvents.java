package com.claudecompanion;

import com.claudecompanion.brain.BrainManager;
import com.claudecompanion.entity.CompanionEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public class GameEvents {
    private static final Pattern STOP = Pattern.compile("^(стоп|stop|хватит|стой|отмена|cancel)[!. ]*$", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern KEY = Pattern.compile("sk-ant-[A-Za-z0-9_-]{10,}");

    @SubscribeEvent
    public void onCommands(RegisterCommandsEvent event) {
        ClaudeCommand.register(event.getDispatcher());
    }

    @SubscribeEvent
    public void onJoin(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof CompanionEntity c) BrainManager.track(c);
    }

    @SubscribeEvent
    public void onLeave(EntityLeaveLevelEvent event) {
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof CompanionEntity c) BrainManager.untrack(c);
    }

    @SubscribeEvent
    public void onLogin(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) SkinSync.sendTo(sp);
    }

    @SubscribeEvent
    public void onStopping(ServerStoppingEvent event) {
        BrainManager.closeAll();
    }

    @SubscribeEvent
    public void onChat(ServerChatEvent event) {
        ServerPlayer player = event.getPlayer();
        String text = event.getRawText();

        // ключ в общем чате видят все — не пропускаем его дальше
        if (KEY.matcher(text).find()) {
            event.setCanceled(true);
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "§cНе пиши ключ в чат! Сообщение не отправлено. Используй команду: /claude key sk-ant-..."));
            return;
        }

        List<CompanionEntity> mine = BrainManager.ownedBy(player.getUUID());
        if (mine.isEmpty()) return; // слушаем только хозяина

        if (STOP.matcher(text.trim()).matches()) {
            mine.forEach(CompanionEntity::stopAll);
        }
        Settings cfg = Settings.get();
        if ("mention".equals(cfg.respondTo) && !STOP.matcher(text.trim()).matches()) {
            String lower = text.toLowerCase(Locale.ROOT);
            if (cfg.names.stream().noneMatch(n -> lower.contains(n.toLowerCase(Locale.ROOT)))) return;
        }
        String line = "<" + player.getGameProfile().getName() + "> " + text;
        for (CompanionEntity c : mine) BrainManager.notify(c, line);
    }
}
