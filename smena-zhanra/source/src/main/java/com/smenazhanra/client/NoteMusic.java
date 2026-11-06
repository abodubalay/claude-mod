package com.smenazhanra.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

import java.util.ArrayList;
import java.util.List;

/**
 * Проигрыватель музыки из звуков нотных блоков Minecraft (без авторских прав — мелодии свои).
 * Ноты пишутся строкой: "C4 . E4 G4 - A3+C4+E4", где каждый токен — один шаг,
 * "." или "-" — пауза, "+" — аккорд. Диапазон нотного блока F#3..F#5 (лишнее сдвигается по октавам).
 */
public class NoteMusic {
    public record Note(double beat, SoundEvent sound, float pitch, float volume) {}

    private final List<Note> notes = new ArrayList<>();
    private final double bpm;
    private double lengthBeats;
    private long startNanos = -1;
    private int idx;
    private int loopIndex;
    public float masterVolume = 0.55f;

    public NoteMusic(double bpm) {
        this.bpm = bpm;
    }

    public double bpm() { return bpm; }

    /** Добавить дорожку. step — длина одного токена в долях (0.5 = восьмая). */
    public NoteMusic track(SoundEvent sound, double step, float volume, String pattern) {
        String[] tokens = pattern.replace("|", " ").trim().split("\\s+");
        double beat = 0;
        for (String tok : tokens) {
            if (!tok.equals(".") && !tok.equals("-")) {
                for (String n : tok.split("\\+")) {
                    notes.add(new Note(beat, sound, pitchOf(n), volume));
                }
            }
            beat += step;
        }
        lengthBeats = Math.max(lengthBeats, beat);
        notes.sort((a, b) -> Double.compare(a.beat, b.beat));
        return this;
    }

    /** Ударные: "K" бочка, "S" малый, "H" хэт, "." пауза. */
    public NoteMusic drums(double step, float volume, String pattern) {
        String[] tokens = pattern.replace("|", " ").trim().split("\\s+");
        double beat = 0;
        for (String tok : tokens) {
            for (char c : tok.toCharArray()) {
                SoundEvent s = switch (c) {
                    case 'K' -> SoundEvents.NOTE_BLOCK_BASEDRUM.value();
                    case 'S' -> SoundEvents.NOTE_BLOCK_SNARE.value();
                    case 'H' -> SoundEvents.NOTE_BLOCK_HAT.value();
                    default -> null;
                };
                if (s != null) notes.add(new Note(beat, s, c == 'H' ? 1.4f : 1f, volume * (c == 'H' ? 0.5f : 1f)));
            }
            beat += step;
        }
        lengthBeats = Math.max(lengthBeats, beat);
        notes.sort((a, b) -> Double.compare(a.beat, b.beat));
        return this;
    }

    public List<Note> notes() { return notes; }

    public double lengthBeats() { return lengthBeats; }

    /** Номер ноты нотного блока 0..24 (F#3 = 0). */
    public static int keyOf(String name) {
        name = name.trim();
        char letter = Character.toUpperCase(name.charAt(0));
        int i = 1;
        int semis = switch (letter) {
            case 'C' -> 0; case 'D' -> 2; case 'E' -> 4; case 'F' -> 5; case 'G' -> 7; case 'A' -> 9; default -> 11;
        };
        if (i < name.length() && name.charAt(i) == '#') { semis++; i++; }
        else if (i < name.length() && name.charAt(i) == 'b') { semis--; i++; }
        int octave = Integer.parseInt(name.substring(i));
        int midi = (octave + 1) * 12 + semis;
        int key = midi - 54;
        while (key < 0) key += 12;
        while (key > 24) key -= 12;
        return key;
    }

    public static float pitchOf(String name) {
        return (float) Math.pow(2.0, (keyOf(name) - 12) / 12.0);
    }

    public void start() {
        startNanos = System.nanoTime();
        idx = 0;
        loopIndex = 0;
    }

    public void stop() {
        startNanos = -1;
    }

    /** Текущая позиция в долях с начала. */
    public double beatNow() {
        if (startNanos < 0) return 0;
        return (System.nanoTime() - startNanos) / 1e9 * bpm / 60.0;
    }

    public double secondsNow() {
        if (startNanos < 0) return 0;
        return (System.nanoTime() - startNanos) / 1e9;
    }

    /** Вызывать часто (каждый кадр) — проигрывает ноты, время которых пришло. */
    public void update() {
        if (startNanos < 0 || notes.isEmpty() || lengthBeats <= 0) return;
        double beat = beatNow();
        int loops = (int) (beat / lengthBeats);
        if (loops > loopIndex) {
            // догоняем хвост предыдущего круга и начинаем новый
            playUntil(Double.MAX_VALUE);
            loopIndex = loops;
            idx = 0;
        }
        playUntil(beat - loops * lengthBeats);
    }

    private void playUntil(double localBeat) {
        Minecraft mc = Minecraft.getInstance();
        while (idx < notes.size() && notes.get(idx).beat <= localBeat) {
            Note n = notes.get(idx++);
            mc.getSoundManager().play(SimpleSoundInstance.forUI(n.sound, n.pitch, n.volume * masterVolume));
        }
    }

    public static void sfx(SoundEvent s, float pitch, float volume) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(s, pitch, volume));
    }
}
