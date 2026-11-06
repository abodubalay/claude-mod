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
    /** Похоже на API-ключ (Anthropic, OpenAI, DeepSeek, xAI, Google, Groq...) */
    private static final Pattern KEY = Pattern.compile("(sk-ant-[A-Za-z0-9_-]{10,}|sk-[A-Za-z0-9_-]{20,}|xai-[A-Za-z0-9_-]{20,}|AIza[0-9A-Za-z_-]{30,}|gsk_[A-Za-z0-9]{20,})");

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
                    "§cНе пиши ключ в чат! Сообщение не отправлено. Используй команду: /claude key <нейросеть> <ключ>"));
            return;
        }

        List<CompanionEntity> mine = BrainManager.ownedBy(player.getUUID());
        if (mine.isEmpty()) return; // слушаем только хозяина

        Settings cfg = Settings.get();
        String lower = text.toLowerCase(Locale.ROOT);
        // кого назвали по имени: «дипсик, копай», «грок и клод, за мной»
        List<CompanionEntity> named = new java.util.ArrayList<>();
        for (CompanionEntity c : mine) {
            Settings.Profile p = cfg.profile(c.getProfileId());
            boolean hit = lower.contains(p.name.toLowerCase(Locale.ROOT))
                    || p.names.stream().anyMatch(n -> !n.isBlank() && lower.contains(n.toLowerCase(Locale.ROOT)));
            if (hit) named.add(c);
        }
        boolean stop = STOP.matcher(text.replaceAll("(?iu)^[\\p{L}\\s,]*?[,:]\\s*", "").trim()).matches()
                || STOP.matcher(text.trim()).matches();
        List<CompanionEntity> targets;
        if (!named.isEmpty()) targets = named;
        else if ("all".equals(cfg.respondTo) || stop) targets = mine;
        else return;

        if (stop) targets.forEach(CompanionEntity::stopAll);
        String line = "<" + player.getGameProfile().getName() + "> " + text;
        for (CompanionEntity c : targets) BrainManager.notify(c, line);
    }
}
