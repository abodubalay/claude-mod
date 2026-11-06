#!/usr/bin/env python3
"""Синтезирует собственные звуки мода (без чужих записей) и кодирует их в .ogg через ffmpeg.

Запуск:  python3 tools/make_sounds.py
"""
import os
import subprocess
import tempfile
import wave

import numpy as np

SR = 44100
OUT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources",
                   "assets", "smenazhanra", "sounds")
os.makedirs(OUT, exist_ok=True)
rng = np.random.default_rng(1980)


def t(sec):
    return np.arange(int(SR * sec)) / SR


def env(n, attack=0.005, release=0.1):
    e = np.ones(n)
    a, r = int(SR * attack), int(SR * release)
    if a:
        e[:a] = np.linspace(0, 1, a)
    if r:
        e[-r:] *= np.linspace(1, 0, r)
    return e


def lowpass(x, k):
    """Простой фильтр — скользящее среднее."""
    return np.convolve(x, np.ones(k) / k, mode="same")


def save(name, x):
    x = x / (np.max(np.abs(x)) + 1e-9) * 0.85
    pcm = (x * 32767).astype(np.int16)
    with tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as f:
        tmp = f.name
    with wave.open(tmp, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(pcm.tobytes())
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", tmp, "-c:a", "libvorbis", "-q:a", "4",
                    os.path.join(OUT, name + ".ogg")], check=True)
    os.remove(tmp)
    print("  звук:", name + ".ogg")


def channel_switch():
    """Щелчок + шипение помех + писк настройки — как переключение каналов старого телевизора."""
    n = int(SR * 1.1)
    noise = rng.uniform(-1, 1, n)
    # «рваные» помехи: громкость скачет
    gate = np.repeat(rng.uniform(0.3, 1.0, n // 800 + 1), 800)[:n]
    hiss = noise * gate * env(n, 0.002, 0.25)
    tt = t(1.1)
    whine = np.sin(2 * np.pi * (3000 + 1500 * np.sin(2 * np.pi * 3 * tt)) * tt) * 0.15 * env(n, 0.05, 0.3)
    click = np.zeros(n)
    click[:300] = np.linspace(1, 0, 300) * 1.5
    click[int(SR * 0.55):int(SR * 0.55) + 300] = np.linspace(1, 0, 300) * 1.2
    save("channel_switch", hiss * 0.7 + whine + click)


def applause():
    n = int(SR * 2.5)
    x = np.zeros(n)
    for _ in range(900):
        p = rng.integers(0, n - 2000)
        L = rng.integers(200, 900)
        x[p:p + L] += rng.uniform(-1, 1, L) * np.exp(-np.linspace(0, 6, L)) * rng.uniform(0.3, 1)
    x = x - lowpass(x, 9) * 0.5
    save("applause", x * env(n, 0.3, 0.8))


def gavel():
    n = int(SR * 0.45)
    tt = t(0.45)
    knock = (np.sin(2 * np.pi * 180 * tt) + 0.6 * np.sin(2 * np.pi * 410 * tt) + 0.3 * np.sin(2 * np.pi * 760 * tt))
    knock *= np.exp(-tt * 18)
    knock[:400] += rng.uniform(-1, 1, 400) * np.linspace(1, 0, 400)
    save("gavel", knock)


def buzzer():
    n = int(SR * 0.9)
    tt = t(0.9)
    sq = np.sign(np.sin(2 * np.pi * 110 * tt)) + 0.5 * np.sign(np.sin(2 * np.pi * 116 * tt))
    save("buzzer", lowpass(sq, 6) * env(n, 0.01, 0.15))


def correct():
    tt = t(0.12)
    parts = []
    for f in (523.25, 659.25, 783.99, 1046.5):
        tone = np.sin(2 * np.pi * f * tt) + 0.3 * np.sin(2 * np.pi * 2 * f * tt)
        parts.append(tone * env(len(tt), 0.005, 0.05))
    last = t(0.5)
    parts.append((np.sin(2 * np.pi * 1046.5 * last) + 0.4 * np.sin(2 * np.pi * 1318.5 * last)) * np.exp(-last * 5))
    save("correct", np.concatenate(parts))


if __name__ == "__main__":
    print("Синтезирую звуки...")
    channel_switch(); applause(); gavel(); buzzer(); correct()
    print("Готово!")
