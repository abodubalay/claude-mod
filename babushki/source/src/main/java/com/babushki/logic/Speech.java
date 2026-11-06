package com.babushki.logic;

import com.babushki.entity.Anim;
import com.babushki.entity.BabushkaEntity;
import com.babushki.entity.Role;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Random;
import java.util.function.Function;
import java.util.function.Supplier;

/** Отправка реплик в чат с правильным цветом и подписью. */
public final class Speech {
    private Speech() {}

    public enum Mood { SCOLD, CHAT, PRAISE, GIFT, WINDOW, NEUTRAL }

    public record Resolved(Role speaker, String text) {}

    /** Определяет, кто говорит каждую реплику. first — кто будет «A». */
    public static List<Resolved> resolve(Lines.Dialogue d, Role first) {
        List<Resolved> out = new ArrayList<>();
        for (String raw : d.lines()) {
            Role who;
            String text = raw;
            if (raw.length() > 2 && raw.charAt(1) == ':') {
                char c = raw.charAt(0);
                text = raw.substring(2);
                who = switch (c) {
                    case 'V' -> Role.VALYA;
                    case 'Z' -> Role.ZINA;
                    case 'B' -> first.other();
                    default -> first;
                };
            } else {
                who = first;
            }
            text = text.replace("{n}", who.other().vocative).replace("{o}", who.other().vocative);
            out.add(new Resolved(who, text));
        }
        return out;
    }

    /** Первая бабушка диалога: если первая реплика явно Валина/Зинина — она, иначе preferred или случайная. */
    public static Role firstSpeaker(Lines.Dialogue d, @Nullable Role preferred, Random r) {
        String l0 = d.lines().get(0);
        if (l0.startsWith("V:")) return Role.VALYA;
        if (l0.startsWith("Z:")) return Role.ZINA;
        if (preferred != null) return preferred;
        return r.nextBoolean() ? Role.VALYA : Role.ZINA;
    }

    public static Component format(Role who, String text, boolean window) {
        MutableComponent name = Component.literal("[" + who.displayName + "] ")
                .withStyle(s -> s.withColor(who.textColor()).withBold(true));
        MutableComponent body = Component.literal(text).withStyle(s -> s.withColor(who.textColor()).withItalic(window));
        return name.append(body);
    }

    /**
     * Проговорить диалог: реплики идут одна за другой с паузой.
     * @param audience кому слать (вычисляется в момент реплики)
     * @param entity   функция «роль → сущность» для анимаций (может вернуть null)
     */
    public static void say(List<Resolved> lines, Supplier<Collection<ServerPlayer>> audience,
                           Function<Role, BabushkaEntity> entity, Mood mood, int delayTicks) {
        Random rnd = new Random();
        for (int i = 0; i < lines.size(); i++) {
            Resolved line = lines.get(i);
            Runnable send = () -> {
                Component msg = format(line.speaker(), line.text(), mood == Mood.WINDOW);
                for (ServerPlayer p : audience.get()) p.sendSystemMessage(msg);
                BabushkaEntity e = entity.apply(line.speaker());
                if (e != null && e.isAlive()) {
                    switch (mood) {
                        case SCOLD -> e.playAnim(rnd.nextBoolean() ? Anim.FINGER_WAG : Anim.HEAD_SHAKE, 40);
                        case CHAT -> {
                            e.playAnim(Anim.WHISPER, 50);
                            BabushkaEntity other = entity.apply(line.speaker().other());
                            if (other != null && other.isAlive() && !other.isBusy()) other.playAnim(Anim.WHISPER, 50);
                        }
                        case PRAISE -> e.playAnim(Anim.HEAD_SHAKE, 20);
                        default -> {}
                    }
                }
            };
            if (i == 0) send.run();
            else Scheduler.schedule(i * delayTicks, send);
        }
    }
}
