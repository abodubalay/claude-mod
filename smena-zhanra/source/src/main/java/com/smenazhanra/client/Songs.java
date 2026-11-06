package com.smenazhanra.client;

import net.minecraft.sounds.SoundEvents;

/** Свои мелодии для каждого жанра (сочинены для мода, играются нотными блоками). */
public class Songs {

    /** Новелла: нежная мелодия на колокольчиках и арфе. */
    public static NoteMusic novella() {
        return new NoteMusic(92)
                .track(SoundEvents.NOTE_BLOCK_BELL.value(), 0.5, 0.45f,
                        "E4 . G4 . C5 . B4 A4 | G4 . . . E4 . . . | F4 . A4 . D5 . C5 B4 | C5 . . . . . . . |"
                      + "E4 . G4 . C5 . D5 E5 | F5 . E5 . D5 . C5 . | A4 . B4 . C5 . D5 . | C5 . . . G4 . . .")
                .track(SoundEvents.NOTE_BLOCK_HARP.value(), 0.5, 0.35f,
                        "C4+E4+G4 . . . C4+E4+G4 . . . | A3+C4+E4 . . . A3+C4+E4 . . . | F4+A4+C5 . . . G3+B3+D4 . . . | C4+E4+G4 . . . C4+E4+G4 . . . |"
                      + "C4+E4+G4 . . . C4+E4+G4 . . . | F4+A4+C5 . . . F4+A4+C5 . . . | G3+B3+D4 . . . G3+B3+D4 . . . | C4+E4+G4 . . . G3+B3+D4 . . .")
                .track(SoundEvents.NOTE_BLOCK_BASS.value(), 0.5, 0.5f,
                        "C4 . . . G4 . . . | A3 . . . E4 . . . | F4 . . . G4 . . . | C4 . . . G4 . . . |"
                      + "C4 . . . G4 . . . | F4 . . . C4 . . . | G4 . . . D4 . . . | C4 . . . G3 . . .");
    }

    /** Суд: тяжёлая драматичная тема в ре миноре. */
    public static NoteMusic sud() {
        return new NoteMusic(84)
                .track(SoundEvents.NOTE_BLOCK_BASS.value(), 0.5, 0.7f,
                        "D4 . D4 . D4 . F4 E4 | D4 . . . A3 . . . | Bb3 . Bb3 . Bb3 . D4 C4 | A3 . . . . . . . |"
                      + "D4 . D4 . D4 . F4 E4 | D4 . . . G4 . . . | A4 . G4 . F4 . E4 . | D4 . . . A3 . . .")
                .track(SoundEvents.NOTE_BLOCK_GUITAR.value(), 0.5, 0.4f,
                        ". . . . . . . . | D4 . F4 . A4 . . . | . . . . . . . . | C#4 . E4 . A4 . . . |"
                      + ". . . . . . . . | D4 . F4 . Bb4 . . . | . . . . . . . . | A4 . . . D5 . . .")
                .drums(0.5, 0.6f, "K . . . K . . . | K . . . K . . . | K . . . K . . . | K . K . K . . . |"
                                + "K . . . K . . . | K . . . K . . . | K . . . K . . . | K . K . K . K K");
    }

    /**
     * Ритм-игра: бодрый трек 128 BPM. Мелодия (plink) — по ней же строятся падающие блоки.
     */
    public static NoteMusic ritmBackground() {
        String bass = "A3 . A3 . A4 . A3 . | F3 . F3 . F4 . F3 . | C4 . C4 . C5 . C4 . | G3 . G3 . G4 . G3 . |";
        String drum = "K . S . K K S . | K . S . K . S H | K . S . K K S . | K . S . K S S S |";
        return new NoteMusic(128)
                .track(SoundEvents.NOTE_BLOCK_BASS.value(), 0.5, 0.75f, bass + bass + bass + bass)
                .drums(0.5, 0.55f, drum + drum + drum + drum)
                .drums(0.5, 0.35f, (". H . H . H . H | ").repeat(16));
    }

    /** Мелодия ритм-игры: 16 тактов. */
    public static final String RITM_MELODY =
            "A4 . A4 C5 E5 . D5 C5 | F4 . A4 . C5 . A4 . | E4 . G4 C5 E5 . D5 C5 | D5 . B4 . G4 . B4 . |"
          + "A4 C5 E5 A4 C5 E5 D5 C5 | F4 A4 C5 F4 A4 C5 D5 C5 | E5 . D5 . C5 . B4 . | B4 . . . D5 . . . |"
          + "E5 . E5 D5 C5 . A4 . | C5 . C5 A4 F4 . A4 . | G4 . C5 . E5 . G4 . | B4 . D5 . G4 . B4 . |"
          + "A4 . C5 . E5 D5 C5 A4 | F4 . A4 . C5 D5 C5 A4 | E4 G4 C5 E5 D5 C5 B4 G4 | A4 . . . A4 . . .";

    public static NoteMusic ritmMelody() {
        return new NoteMusic(128).track(SoundEvents.NOTE_BLOCK_PLING.value(), 0.5, 0.5f, RITM_MELODY);
    }

    /** Викторина: напряжённая «думательная» тема. */
    public static NoteMusic viktorina() {
        return new NoteMusic(116)
                .track(SoundEvents.NOTE_BLOCK_BIT.value(), 0.5, 0.3f,
                        "E4 G4 B4 G4 E4 G4 B4 G4 | C4 E4 G4 E4 C4 E4 G4 E4 | D4 F#4 A4 F#4 D4 F#4 A4 F#4 | B3 D#4 F#4 D#4 B3 D#4 F#4 B4 |")
                .track(SoundEvents.NOTE_BLOCK_BASS.value(), 0.5, 0.7f,
                        "E4 . . . E4 . . . | C4 . . . C4 . . . | D4 . . . D4 . . . | B3 . . . B3 . . . |")
                .drums(0.5, 0.35f, "K . H . K . H . | K . H . K . H . | K . H . K . H . | K . H . K H S S |");
    }

    /** Файтинг: агрессивный трек 150 BPM. */
    public static NoteMusic fighting() {
        return new NoteMusic(150)
                .track(SoundEvents.NOTE_BLOCK_BASS.value(), 0.5, 0.8f,
                        "E4 E4 E4 E4 G4 G4 A4 A4 | E4 E4 E4 E4 D4 D4 B3 B3 | C4 C4 C4 C4 D4 D4 E4 E4 | B3 B3 B3 B3 D#4 D#4 F#4 F#4 |")
                .track(SoundEvents.NOTE_BLOCK_GUITAR.value(), 0.5, 0.45f,
                        "E4 . G4 . B4 . A4 G4 | E4 . . . D4 . B3 . | C5 . B4 . A4 . G4 . | F#4 . . . B4 . . . |")
                .drums(0.5, 0.6f, "K . S . K K S . | K . S . K . S . | K . S . K K S . | K S K S S S S S |");
    }
}
