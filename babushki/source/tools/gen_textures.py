#!/usr/bin/env python3
"""Рисует все текстуры мода «Бабушки у подъезда» (пиксель-арт).
Запуск: python3 tools/gen_textures.py  (из папки проекта)
Раскладка UV совпадает с BabushkaModel.java.
"""
import os
import random
from PIL import Image

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "babushki", "textures")
T = (0, 0, 0, 0)


def box_faces(u, v, w, h, d):
    return {
        "top": (u + d, v, w, d),
        "bottom": (u + d + w, v, w, d),
        "right": (u, v + d, d, h),
        "front": (u + d, v + d, w, h),
        "left": (u + d + w, v + d, d, h),
        "back": (u + 2 * d + w, v + d, w, h),
    }


def fill(img, rect, fn):
    x0, y0, w, h = rect
    for y in range(h):
        for x in range(w):
            c = fn(x, y, w, h)
            if c is not None:
                img.putpixel((x0 + x, y0 + y), c if len(c) == 4 else c + (255,))


def paint_box(img, u, v, w, h, d, fn_by_face):
    for face, rect in box_faces(u, v, w, h, d).items():
        fn = fn_by_face.get(face, fn_by_face.get("all"))
        if fn is not None:
            fill(img, rect, lambda x, y, ww, hh, f=fn, fc=face: f(fc, x, y, ww, hh))


def solid(c, noise=6, seed=1):
    rnd = random.Random(seed)
    cache = {}

    def f(face, x, y, w, h):
        k = (face, x, y)
        if k not in cache:
            n = rnd.randint(-noise, noise)
            cache[k] = tuple(max(0, min(255, ch + n)) for ch in c)
        return cache[k]
    return f


def shade(c, k):
    return tuple(max(0, min(255, int(ch * k))) for ch in c)


SKIN = (234, 192, 162)
SKIN_D = (214, 168, 138)
HAIR = (182, 182, 188)
CANE = (110, 70, 40)
STOCK = (204, 168, 138)
SHOE = (35, 30, 30)


def entity_texture(name, scarf_fn, cardigan, skirt_fn, bag, face_style):
    img = Image.new("RGBA", (64, 64), T)

    # --- голова (0,0) 8x8x8 ---
    skin = solid(SKIN, 4, 2)

    def head(face, x, y, w, h):
        if face == "front":
            if y <= 1:
                return HAIR
            return SKIN if (x, y) not in ((0, 7), (7, 7)) else SKIN_D
        if face == "top":
            return HAIR
        if face in ("back",) and y < 5:
            return HAIR
        return skin(face, x, y, w, h)
    paint_box(img, 0, 0, 8, 8, 8, {"all": head})

    # лицо
    fx, fy = 8, 8
    px = lambda x, y, c: img.putpixel((fx + x, fy + y), c + (255,))
    if face_style == "strict":
        # Валя: очки, сдвинутые брови, поджатые губы
        for x in (1, 2): px(x, 2, (90, 70, 60))
        for x in (5, 6): px(x, 2, (90, 70, 60))
        px(3, 3, (90, 70, 60)); px(4, 3, (90, 70, 60))  # брови к переносице
        frame = (120, 120, 130)
        for x in range(1, 7): px(x, 3, frame) if x not in (3, 4) else None
        px(1, 4, frame); px(6, 4, frame); px(3, 4, frame); px(4, 4, frame)
        px(2, 4, (40, 30, 30)); px(5, 4, (40, 30, 30))
        px(1, 5, frame); px(2, 5, frame); px(5, 5, frame); px(6, 5, frame)
        px(3, 5, SKIN_D); px(4, 5, SKIN_D)  # нос
        for x in range(2, 6): px(x, 6, (150, 70, 70))
        px(1, 6, SKIN_D); px(6, 6, SKIN_D)  # морщинки
    else:
        # Зина: круглые любопытные глаза, приподнятые брови, румянец, ротик «о»
        px(1, 2, (110, 90, 80)); px(2, 2, (110, 90, 80))
        px(5, 2, (110, 90, 80)); px(6, 2, (110, 90, 80))
        for (x, y) in ((1, 3), (2, 3), (5, 3), (6, 3)): px(x, y, (255, 255, 255))
        px(2, 4, (40, 30, 30)); px(5, 4, (40, 30, 30))
        px(1, 4, (255, 255, 255)); px(6, 4, (255, 255, 255))
        px(3, 4, SKIN_D); px(4, 4, SKIN_D)
        px(0, 5, (235, 140, 140)); px(1, 5, (235, 140, 140)); px(6, 5, (235, 140, 140)); px(7, 5, (235, 140, 140))
        px(3, 6, (160, 70, 70)); px(4, 6, (160, 70, 70))
        px(3, 5, SKIN_D); px(4, 5, SKIN_D)

    # --- платок (32,0) 8x8x8, поверх головы ---
    def scarf(face, x, y, w, h):
        if face == "bottom":
            return None
        if face == "front":
            if y <= 1 or x == 0 or x == 7:
                return scarf_fn(x, y, True)
            return None
        if face in ("right", "left") and y >= 6 and (x <= 1):
            return None  # открыта щека спереди
        return scarf_fn(x + {"top": 0, "right": 1, "left": 3, "back": 2}.get(face, 0), y, False)
    paint_box(img, 32, 0, 8, 8, 8, {"all": scarf})
    # узелок (48,40) 2x2x1
    paint_box(img, 48, 40, 2, 2, 1, {"all": lambda f, x, y, w, h: scarf_fn(x, y, True)})

    # --- кофта (0,16) 9x11x6 ---
    card = solid(cardigan, 7, 3)

    def body(face, x, y, w, h):
        c = card(face, x, y, w, h)
        if face == "front":
            if x == 4:
                return shade(cardigan, 0.75) if y % 3 != 1 else (230, 220, 180)  # пуговицы
            if y <= 1 and 2 <= x <= 6:
                return (235, 230, 220) if y == 0 else shade(cardigan, 0.9)  # воротничок
            if y == 10:
                return shade(cardigan, 0.8)
            if x in (1, 7) and 6 <= y <= 8:
                return shade(cardigan, 0.7)  # карманы
        if face == "top" and 2 <= x <= 6 and 1 <= y <= 4:
            return SKIN_D
        return c
    paint_box(img, 0, 16, 9, 11, 6, {"all": body})

    # --- юбка (0,34) 10x10x7 ---
    def skirt(face, x, y, w, h):
        if face == "top":
            return shade(skirt_fn(x, y), 0.8)
        if face == "bottom":
            return shade(skirt_fn(x, y), 0.6)
        c = skirt_fn(x, y)
        if y == h - 1:
            return shade(c, 0.75)
        return c
    paint_box(img, 0, 34, 10, 10, 7, {"all": skirt})

    # --- руки (32,16) и (44,16) 3x10x3 ---
    for u in (32, 44):
        def arm(face, x, y, w, h, u=u):
            if face == "bottom" or y >= h - 2:
                return SKIN if face != "top" else card(face, x, y, w, h)
            if y == h - 3:
                return shade(cardigan, 0.75)  # манжета
            return card(face, x, y, w, h)
        paint_box(img, u, 16, 3, 10, 3, {"all": arm})

    # --- клюка (56,16) 1x13x1 и ручка (48,44) 1x1x3 ---
    wood = solid(CANE, 10, 5)
    paint_box(img, 56, 16, 1, 13, 1, {"all": lambda f, x, y, w, h: wood(f, x, y, w, h) if y < h - 1 else (40, 40, 40)})
    paint_box(img, 48, 44, 1, 1, 3, {"all": wood})

    # --- сумка (36,36) 2x4x4 ---
    bagc = solid(bag, 6, 7)

    def bagf(face, x, y, w, h):
        if face in ("right", "left") and y == 0 and x in (1, 2):
            return (210, 180, 60)  # застёжка
        if face == "top":
            return shade(bag, 0.7)
        return bagc(face, x, y, w, h)
    paint_box(img, 36, 36, 2, 4, 4, {"all": bagf})

    # --- ноги: бедро (u,52) 3x5x3, голень (u+12,52) 3x6x3 ---
    st = solid(STOCK, 5, 9)
    for u in (0, 24):
        paint_box(img, u, 52, 3, 5, 3, {"all": st})

        def shin(face, x, y, w, h):
            if face == "bottom" or y >= h - 2:
                return SHOE if not (face == "front" and y == h - 2 and x == 1) else (70, 60, 60)
            return st(face, x, y, w, h)
        paint_box(img, u + 12, 52, 3, 6, 3, {"all": shin})

    out = os.path.join(ROOT, "entity", name + ".png")
    img.save(out)
    print("saved", out)


# ---------- Валя: красный платок в белый горошек, серо-коричневая кофта, синяя юбка ----------
def valya_scarf(x, y, front):
    red = (196, 28, 36)
    if (x * 3 + y * 5) % 7 == 0 and not front:
        return (240, 235, 235)
    if front and y == 1:
        return shade(red, 0.85)
    return red


def valya_skirt(x, y):
    return (44, 50, 92) if (x + y) % 5 else (38, 43, 80)


# ---------- Зина: цветастый платок, сиреневая кофта, клетчатая коричневая юбка ----------
def zina_scarf(x, y, front):
    base = (245, 196, 52)
    k = (x * 7 + y * 3) % 11
    if k == 0:
        return (220, 40, 60)     # красный цветок
    if k == 5:
        return (60, 110, 210)    # синий цветок
    if k in (1, 10):
        return (70, 160, 60)     # листик
    if k == 3:
        return (250, 250, 250)
    return base


def zina_skirt(x, y):
    a, b = (115, 78, 52), (140, 100, 66)
    if x % 4 == 0 or y % 4 == 0:
        return (90, 58, 38)
    return a if (x // 2 + y // 2) % 2 else b


def items():
    P = {
        ".": T,
        "o": (60, 35, 20), "b": (205, 135, 55), "B": (230, 170, 80), "y": (250, 210, 120),
        "r": (190, 30, 40), "R": (230, 60, 70), "w": (240, 240, 240), "W": (210, 210, 215),
        "g": (170, 210, 230), "G": (120, 160, 190), "k": (40, 30, 30), "c": (235, 220, 190),
        "s": (150, 120, 80), "S": (110, 85, 55), "a": (200, 40, 40), "l": (80, 160, 60), "f": (240, 150, 40),
        "h": (190, 140, 70),
    }
    art = {
        "pirozhok": [
            "................",
            "................",
            "................",
            "................",
            ".....oooooo.....",
            "...oobByyBboo...",
            "..obBByyyyBBbo..",
            ".obBBByyyyBBBbo.",
            ".obBbBByyBBbBbo.",
            ".obbBbBBBBbBbbo.",
            "..obbbbbbbbbbo..",
            "...oobbbbbboo...",
            ".....oooooo.....",
            "................",
            "................",
            "................",
        ],
        "jam": [
            "................",
            ".....rRrRrR.....",
            "....rRwRwRwr....",
            "....kkkkkkkk....",
            ".....gWWWWg.....",
            "....gaaaaaaG....",
            "...gaRaaaaRaG...",
            "...gaaaaaaaaG...",
            "...gacccccaaG...",
            "...gacaRacaaG...",
            "...gacccccaaG...",
            "...gaaaaaRaaG...",
            "...gaaRaaaaaG...",
            "....gaaaaaaG....",
            ".....GGGGGG.....",
            "................",
        ],
        "socks": [
            "................",
            "..wwwww.........",
            "..rrrrr.........",
            "..wwwww.........",
            "..rRrRr.........",
            "..rrrrr.........",
            "..rwrwr.........",
            "..rrrrr.........",
            "..rrrrrr........",
            "..rrrrrrr.......",
            "..rrrrrrrrr.....",
            "..wrrrrrrrrr....",
            "..wwrrrrrrrww...",
            "...wwwwwwwww....",
            "................",
            "................",
        ],
        "scarf": [
            "................",
            "....rrrrrrrr....",
            "...rwwrrwwrrr...",
            "..rr........rr..",
            "..rw........wr..",
            "..rr........rr..",
            "...rrrrrrrrrr...",
            "........rwr.....",
            "........rrr.....",
            "........wwr.....",
            "........rrr.....",
            "........rww.....",
            "........rrr.....",
            "........w.w.....",
            "................",
            "................",
        ],
        "avoska": [
            "................",
            "......SSSS......",
            ".....S....S.....",
            ".....S....S.....",
            "....hhhhhhhh....",
            "...h.a.hl.h.h...",
            "...haaahlll.h...",
            "...hhahhhlhhh...",
            "...hbBbhffh.h...",
            "...hBBBhffhfh...",
            "...hhhhhhhhhh...",
            "...h.h.h.h.hh...",
            "....hhhhhhhh....",
            ".....h.h.h.h....",
            "......hhhhh.....",
            "................",
        ],
    }
    for name, rows in art.items():
        img = Image.new("RGBA", (16, 16), T)
        for y, row in enumerate(rows):
            for x, ch in enumerate(row):
                c = P[ch]
                img.putpixel((x, y), c if len(c) == 4 else c + (255,))
        out = os.path.join(ROOT, "item", name + ".png")
        img.save(out)
        print("saved", out)


def armor():
    img = Image.new("RGBA", (64, 32), T)
    red, white = (190, 30, 40, 255), (240, 240, 240, 255)
    # Шарф: нижние 3 ряда головы (вокруг шеи), полоски
    for x in range(0, 32):
        for y in range(13, 16):
            img.putpixel((x, y), white if (x // 2) % 3 == 0 else red)
    for x in range(8, 24):  # низ головы
        for y in range(0, 8):
            if 16 <= x < 24:
                img.putpixel((x, y), red if (x + y) % 3 else white)
    # Носки: нижние 6 рядов ног
    for x in range(0, 16):
        for y in range(26, 32):
            img.putpixel((x, y), white if y == 26 or (y == 29 and x % 2 == 0) else red)
    for x in range(8, 12):
        for y in range(16, 20):
            img.putpixel((x, y), red)
    out = os.path.join(ROOT, "models", "armor", "knitted_layer_1.png")
    img.save(out)
    # пустой второй слой (поножей нет, но файл нужен на всякий случай)
    Image.new("RGBA", (64, 32), T).save(os.path.join(ROOT, "models", "armor", "knitted_layer_2.png"))
    print("saved", out)


if __name__ == "__main__":
    entity_texture("baba_valya", valya_scarf, (96, 80, 74), valya_skirt, (32, 30, 30), "strict")
    entity_texture("baba_zina", zina_scarf, (128, 78, 140), zina_skirt, (150, 62, 42), "curious")
    items()
    armor()
