package com.smenazhanra.client.screen;

import com.smenazhanra.Genre;

/** Данные от сервера для мини-игры. */
public record GenreContext(Genre genre, int seconds, boolean allowSkip, int[] stats, long seed, String playerName) {
    public int stat(int key) {
        return stats != null && key < stats.length ? stats[key] : 0;
    }
}
