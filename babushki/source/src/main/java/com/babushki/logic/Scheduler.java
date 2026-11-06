package com.babushki.logic;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Простейший планировщик «сделать через N тиков» (только на сервере). */
public final class Scheduler {
    private Scheduler() {}
    private record Task(long at, Runnable r) {}
    private static final List<Task> TASKS = new ArrayList<>();
    private static long now;

    public static void schedule(int delayTicks, Runnable r) {
        TASKS.add(new Task(now + Math.max(1, delayTicks), r));
    }

    public static void tick() {
        now++;
        if (TASKS.isEmpty()) return;
        List<Runnable> due = new ArrayList<>();
        Iterator<Task> it = TASKS.iterator();
        while (it.hasNext()) {
            Task t = it.next();
            if (t.at <= now) { due.add(t.r); it.remove(); }
        }
        for (Runnable r : due) {
            try { r.run(); } catch (Exception ignored) {}
        }
    }

    public static void clear() { TASKS.clear(); }
}
