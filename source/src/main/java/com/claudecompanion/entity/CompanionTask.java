package com.claudecompanion.entity;

import java.util.concurrent.CompletableFuture;

/** Долгое действие, которое выполняется по тикам на сервере. Результат — текст для Claude. */
public abstract class CompanionTask {
    public final CompletableFuture<String> result = new CompletableFuture<>();
    protected int ticks = 0;
    protected final int maxTicks;

    protected CompanionTask(int maxSeconds) {
        this.maxTicks = maxSeconds * 20;
    }

    /** @return true, когда задача закончена */
    public final boolean tick(CompanionEntity e) {
        if (result.isDone()) return true;
        if (++ticks > maxTicks) {
            onTimeout(e);
            return true;
        }
        return step(e);
    }

    protected abstract boolean step(CompanionEntity e);

    protected void onTimeout(CompanionEntity e) {
        fail("не успел за " + (maxTicks / 20) + " с");
        e.getNavigation().stop();
    }

    public void complete(String msg) { result.complete(msg); }
    public void fail(String msg) { result.complete("Не получилось: " + msg); }
    public void cancel(String why) { result.complete("Прервано: " + why); }
}
