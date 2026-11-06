#!/usr/bin/env python3
"""Рисует всю пиксельную графику мода «Смена жанра».

Запуск:  python3 tools/draw_art.py
Картинки сохраняются в src/main/resources/assets/smenazhanra/textures/gui/
Всё нарисовано кодом с нуля (прямоугольники, круги, пиксельные карты) — никаких чужих картинок.
"""
import os
import random
from PIL import Image, ImageDraw

OUT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources",
                   "assets", "smenazhanra", "textures", "gui")
os.makedirs(OUT, exist_ok=True)
R = random.Random(1980)


def new(w, h, bg=(0, 0, 0, 0)):
    im = Image.new("RGBA", (w, h), bg)
    return im, ImageDraw.Draw(im)


def save(im, name):
    im.save(os.path.join(OUT, name))
    print("  нарисовано:", name, im.size)


def rect(d, x, y, w, h, c):
    d.rectangle([x, y, x + w - 1, y + h - 1], fill=c)


def ell(d, x, y, w, h, c):
    d.ellipse([x, y, x + w - 1, y + h - 1], fill=c)


def vgrad(d, x, y, w, h, c1, c2, bands=12):
    """Вертикальный градиент полосами (пиксельный стиль)."""
    for i in range(h):
        t = int((i / max(1, h - 1)) * bands) / bands
        c = tuple(int(c1[k] + (c2[k] - c1[k]) * t) for k in range(3)) + (255,)
        d.line([x, y + i, x + w - 1, y + i], fill=c)


def pixmap(im, x, y, rows, pal, scale=1):
    """Рисует пиксельную карту из строк. '.' = прозрачно."""
    px = im.load()
    for j, row in enumerate(rows):
        for i, ch in enumerate(row):
            if ch == '.' or ch == ' ':
                continue
            c = pal[ch]
            for sy in range(scale):
                for sx in range(scale):
                    xx, yy = x + i * scale + sx, y + j * scale + sy
                    if 0 <= xx < im.width and 0 <= yy < im.height:
                        px[xx, yy] = c


def shade(c, k):
    return tuple(max(0, min(255, int(v * k))) for v in c[:3]) + (c[3] if len(c) > 3 else 255,)


def noise_fill(im, x, y, w, h, base, amount=0.08, seed=0):
    rr = random.Random(seed)
    px = im.load()
    for j in range(y, y + h):
        for i in range(x, x + w):
            if 0 <= i < im.width and 0 <= j < im.height:
                k = 1 + rr.uniform(-amount, amount)
                px[i, j] = shade(base, k)


# ---------------------------------------------------------------- НОВЕЛЛА
def novella_bg():
    im, d = new(320, 180)
    vgrad(d, 0, 0, 320, 110, (120, 190, 255), (255, 214, 235), 10)
    # облака
    for cx, cy in [(40, 20), (150, 12), (250, 28), (300, 8)]:
        for k in range(5):
            ell(d, cx + k * 9 - 18, cy + (k % 2) * 3, 18, 10, (255, 255, 255, 230))
    # здание школы
    rect(d, 70, 40, 230, 80, (240, 226, 196, 255))
    rect(d, 70, 40, 230, 4, (200, 120, 90, 255))
    rect(d, 66, 34, 238, 7, (170, 80, 70, 255))
    # башня с часами
    rect(d, 165, 14, 40, 30, (240, 226, 196, 255))
    d.polygon([(160, 16), (185, 2), (210, 16)], fill=(170, 80, 70, 255))
    ell(d, 175, 20, 20, 20, (255, 255, 255, 255))
    d.ellipse([175, 20, 194, 39], outline=(90, 70, 60, 255))
    d.line([185, 30, 185, 23], fill=(40, 40, 40, 255))
    d.line([185, 30, 190, 30], fill=(40, 40, 40, 255))
    # окна
    for row in range(3):
        for col in range(9):
            if 160 <= 72 + col * 25 <= 205 and row == 0:
                continue
            x, y = 78 + col * 25, 50 + row * 22
            rect(d, x, y, 16, 14, (110, 160, 210, 255))
            rect(d, x, y, 16, 2, (90, 70, 60, 255))
            rect(d, x + 7, y, 2, 14, (240, 240, 240, 255))
            rect(d, x + 2, y + 3, 3, 3, (200, 230, 255, 255))
    # дверь
    rect(d, 175, 96, 22, 24, (120, 80, 50, 255))
    rect(d, 185, 96, 2, 24, (80, 50, 30, 255))
    # земля двора
    vgrad(d, 0, 110, 320, 70, (210, 190, 160), (180, 160, 130), 6)
    for i in range(0, 320, 16):
        d.line([i, 110, i - 30, 180], fill=(170, 150, 120, 255))
    for j in (124, 142, 164):
        d.line([0, j, 320, j], fill=(170, 150, 120, 255))
    # забор
    for i in range(0, 320, 8):
        rect(d, i, 104, 2, 10, (110, 130, 110, 255))
    rect(d, 0, 106, 320, 2, (110, 130, 110, 255))
    # сакура слева
    rect(d, 22, 60, 10, 60, (110, 70, 50, 255))
    d.line([27, 70, 8, 52], fill=(110, 70, 50, 255), width=3)
    d.line([27, 66, 48, 48], fill=(110, 70, 50, 255), width=3)
    for _ in range(90):
        x, y = R.randint(-10, 70), R.randint(10, 70)
        if (x - 27) ** 2 / 1600 + (y - 42) ** 2 / 700 < 1:
            ell(d, x, y, 9, 7, R.choice([(255, 183, 213, 255), (255, 200, 225, 255), (245, 160, 195, 255)]))
    # лепестки
    for _ in range(40):
        x, y = R.randint(0, 319), R.randint(0, 179)
        rect(d, x, y, 2, 1, (255, 190, 220, 255))
    save(im, "novella_bg.png")


def pig_girl(expr):
    W, H = 120, 160
    im, d = new(W, H)
    pink = (255, 170, 190, 255)
    pink_d = (232, 132, 160, 255)
    pink_l = (255, 205, 215, 255)
    navy = (40, 50, 110, 255)
    navy_l = (70, 85, 160, 255)
    white = (250, 250, 255, 255)
    red = (230, 40, 70, 255)
    out = (120, 50, 70, 255)
    # ноги
    rect(d, 44, 136, 10, 20, pink)
    rect(d, 66, 136, 10, 20, pink)
    rect(d, 42, 152, 14, 6, (60, 40, 40, 255))
    rect(d, 64, 152, 14, 6, (60, 40, 40, 255))
    rect(d, 44, 140, 10, 8, white)  # гольфы
    rect(d, 66, 140, 10, 8, white)
    # юбка
    d.polygon([(36, 110), (84, 110), (94, 140), (26, 140)], fill=navy)
    for x in range(30, 92, 8):
        d.line([x + 2, 112, x, 139], fill=navy_l)
    # блузка
    rect(d, 38, 82, 44, 30, white)
    d.polygon([(38, 82), (82, 82), (74, 98), (46, 98)], fill=navy)  # матросский воротник
    d.line([40, 84, 80, 84], fill=white)
    d.polygon([(52, 92), (68, 92), (60, 104)], fill=red)  # бант
    ell(d, 54, 90, 12, 8, red)
    # руки
    rect(d, 28, 84, 10, 30, white)
    rect(d, 82, 84, 10, 30, white)
    ell(d, 27, 110, 12, 10, pink)
    ell(d, 81, 110, 12, 10, pink)
    # голова
    ell(d, 18, 10, 84, 76, out)
    ell(d, 19, 11, 82, 74, pink)
    ell(d, 24, 14, 60, 30, pink_l)
    # уши
    d.polygon([(22, 26), (14, 0), (40, 14)], fill=pink_d)
    d.polygon([(98, 26), (106, 0), (80, 14)], fill=pink_d)
    d.polygon([(24, 20), (19, 6), (34, 15)], fill=(255, 140, 170, 255))
    d.polygon([(96, 20), (101, 6), (86, 15)], fill=(255, 140, 170, 255))
    # чёлка-хохолок
    d.polygon([(46, 12), (60, 2), (56, 18)], fill=pink_d)
    d.polygon([(58, 12), (72, 4), (66, 18)], fill=pink_d)
    # пятачок
    ell(d, 44, 52, 32, 20, pink_d)
    ell(d, 46, 53, 28, 17, (255, 150, 175, 255))
    ell(d, 51, 58, 6, 7, out)
    ell(d, 63, 58, 6, 7, out)
    # глаза
    def big_eye(x):
        ell(d, x, 28, 20, 24, (40, 20, 50, 255))
        ell(d, x + 2, 32, 16, 18, (120, 60, 160, 255))
        ell(d, x + 4, 38, 12, 12, (200, 120, 230, 255))
        ell(d, x + 3, 30, 8, 8, white)
        rect(d, x + 12, 44, 3, 3, white)
        rect(d, x + 7, 47, 2, 2, white)
        d.line([x - 1, 28, x + 6, 25], fill=(40, 20, 50, 255), width=2)  # ресницы
    if expr in ("normal", "cry"):
        big_eye(28)
        big_eye(72)
    elif expr in ("happy", "blush"):
        for x in (30, 74):
            d.arc([x, 32, x + 18, 48], 200, 340, fill=(40, 20, 50, 255), width=3)
    # рот
    if expr == "cry":
        d.arc([52, 72, 68, 82], 200, 340, fill=out, width=2)
    else:
        d.arc([52, 68, 68, 78], 20, 160, fill=out, width=2)
    if expr in ("blush", "happy"):
        for x in (22, 80):
            ell(d, x, 50, 16, 8, (255, 100, 130, 200))
            for k in range(3):
                d.line([x + 3 + k * 4, 52, x + 1 + k * 4, 56], fill=(220, 60, 100, 255))
    if expr == "blush":
        # сердечки
        for hx, hy in [(4, 6), (104, 30)]:
            pixmap(im, hx, hy, [".rr.rr.", "rrrrrrr", "rrrrrrr", ".rrrrr.", "..rrr..", "...r..."], {"r": red})
    if expr == "cry":
        for x in (30, 86):
            rect(d, x, 50, 4, 30, (120, 190, 255, 220))
            rect(d, x + 1, 50, 2, 30, (190, 230, 255, 230))
            ell(d, x - 1, 78, 6, 6, (120, 190, 255, 220))
    return im


def novella_pigs():
    for e in ("normal", "blush", "happy", "cry"):
        save(pig_girl(e), f"novella_pig_{e}.png")


def hearts():
    im, d = new(18, 9)
    red, dark, hi = (235, 40, 80, 255), (80, 30, 40, 255), (255, 170, 190, 255)
    rows = [".xx.xx..", "xrrxrrx.", "xrhrrrx.", "xrrrrrx.", ".xrrrx..", "..xrx...", "...x...."]
    pixmap(im, 0, 1, rows, {"x": dark, "r": red, "h": hi})
    pixmap(im, 9, 1, rows, {"x": dark, "r": (90, 60, 70, 255), "h": (110, 80, 90, 255)})
    save(im, "hearts.png")


# ---------------------------------------------------------------- СУД
def court_bg():
    im, d = new(320, 180)
    # стены — деревянные панели
    noise_fill(im, 0, 0, 320, 120, (120, 80, 50, 255), 0.06, 1)
    for x in range(0, 320, 32):
        rect(d, x, 0, 2, 120, (90, 58, 35, 255))
        rect(d, x + 6, 20, 20, 36, (135, 92, 58, 255))
        rect(d, x + 6, 66, 20, 40, (135, 92, 58, 255))
    # окно с витражом
    rect(d, 240, 10, 50, 46, (70, 45, 30, 255))
    vgrad(d, 243, 13, 44, 40, (140, 200, 255), (220, 240, 255), 5)
    rect(d, 264, 13, 2, 40, (70, 45, 30, 255))
    rect(d, 243, 32, 44, 2, (70, 45, 30, 255))
    # герб: весы
    ell(d, 140, 4, 40, 40, (200, 160, 60, 255))
    ell(d, 143, 7, 34, 34, (230, 200, 90, 255))
    rect(d, 159, 12, 2, 22, (110, 80, 30, 255))
    rect(d, 148, 14, 24, 2, (110, 80, 30, 255))
    d.polygon([(146, 16), (150, 16), (152, 24), (144, 24)], fill=(110, 80, 30, 255))
    d.polygon([(170, 16), (174, 16), (176, 24), (168, 24)], fill=(110, 80, 30, 255))
    rect(d, 154, 34, 12, 3, (110, 80, 30, 255))
    # пол — плитка
    for j in range(120, 180, 10):
        for i in range(0, 320, 20):
            c = (180, 170, 150, 255) if ((i // 20 + j // 10) % 2) else (150, 140, 120, 255)
            rect(d, i, j, 20, 10, c)
    # трибуна судьи
    rect(d, 110, 64, 100, 60, (95, 60, 35, 255))
    rect(d, 106, 60, 108, 8, (70, 42, 25, 255))
    for x in range(118, 208, 22):
        rect(d, x, 74, 14, 40, (110, 72, 44, 255))
    # стол истца слева
    rect(d, 6, 110, 76, 8, (70, 42, 25, 255))
    rect(d, 10, 118, 6, 30, (70, 42, 25, 255))
    rect(d, 72, 118, 6, 30, (70, 42, 25, 255))
    # свидетельская ложа справа
    rect(d, 232, 104, 84, 44, (95, 60, 35, 255))
    rect(d, 228, 100, 92, 6, (70, 42, 25, 255))
    for x in range(236, 316, 10):
        rect(d, x, 110, 4, 36, (110, 72, 44, 255))
    save(im, "court_bg.png")


def villager_head(d, im, x, y, robe, sleep=False):
    skin = (190, 140, 105, 255)
    skin_d = (150, 105, 75, 255)
    rect(d, x, y, 24, 28, skin)
    rect(d, x, y + 24, 24, 4, skin_d)
    rect(d, x + 3, y + 8, 18, 3, (70, 45, 30, 255))  # единая бровь
    if sleep:
        rect(d, x + 4, y + 13, 6, 1, (40, 30, 30, 255))
        rect(d, x + 14, y + 13, 6, 1, (40, 30, 30, 255))
    else:
        rect(d, x + 4, y + 12, 6, 4, (255, 255, 255, 255))
        rect(d, x + 14, y + 12, 6, 4, (255, 255, 255, 255))
        rect(d, x + 7, y + 12, 3, 4, (40, 140, 60, 255))
        rect(d, x + 14, y + 12, 3, 4, (40, 140, 60, 255))
    rect(d, x + 9, y + 12, 6, 16, skin_d)  # нос
    rect(d, x + 10, y + 26, 4, 4, skin_d)


def judge(sleep):
    im, d = new(56, 72)
    robe = (30, 30, 35, 255)
    rect(d, 10, 36, 36, 36, robe)
    rect(d, 24, 36, 8, 30, (240, 240, 240, 255))  # жабо
    rect(d, 4, 40, 10, 28, robe)
    rect(d, 42, 40, 10, 28, robe)
    villager_head(d, im, 16, 8, robe, sleep)
    # парик
    for k in range(6):
        ell(d, 8 + (k % 2) * 2, 6 + k * 6, 10, 9, (245, 245, 240, 255))
        ell(d, 38 - (k % 2) * 2, 6 + k * 6, 10, 9, (245, 245, 240, 255))
    for k in range(4):
        ell(d, 14 + k * 7, 1, 10, 9, (245, 245, 240, 255))
    for k in range(6):
        d.arc([9 + (k % 2) * 2, 7 + k * 6, 17 + (k % 2) * 2, 14 + k * 6], 0, 180, fill=(200, 200, 195, 255))
    if not sleep:
        # молоток в правой руке
        rect(d, 46, 26, 4, 22, (120, 80, 40, 255))
        rect(d, 40, 20, 16, 8, (90, 55, 30, 255))
        rect(d, 40, 20, 16, 2, (130, 90, 50, 255))
    return im


def creeper_tie():
    im, d = new(36, 60)
    g = [(80, 170, 70), (60, 140, 55), (110, 200, 95), (40, 110, 40)]
    px = im.load()
    rr = random.Random(7)
    def body(x, y, w, h):
        for j in range(h):
            for i in range(w):
                c = rr.choice(g)
                px[x + i, y + j] = c + (255,)
    body(6, 0, 24, 24)     # голова
    body(10, 24, 16, 26)   # тело
    body(6, 50, 10, 10)    # ноги
    body(20, 50, 10, 10)
    face = (20, 20, 20, 255)
    rect(d, 9, 6, 6, 6, face)
    rect(d, 21, 6, 6, 6, face)
    rect(d, 15, 12, 6, 6, face)
    rect(d, 12, 15, 4, 7, face)
    rect(d, 20, 15, 4, 7, face)
    # галстук и воротник
    d.polygon([(12, 24), (18, 28), (24, 24), (24, 26), (12, 26)], fill=(255, 255, 255, 255))
    d.polygon([(16, 26), (20, 26), (19, 29), (17, 29)], fill=(200, 30, 40, 255))
    d.polygon([(17, 29), (19, 29), (22, 42), (18, 46), (14, 42)], fill=(200, 30, 40, 255))
    d.line([16, 33, 21, 36], fill=(150, 20, 30, 255))
    d.line([15, 38, 21, 41], fill=(150, 20, 30, 255))
    save(im, "creeper_tie.png")


CHICKEN_ROWS = [
    "......rr......",
    ".....wwww.....",
    "....wwwwww....",
    "....wwkwww....",
    "..yywwwwww....",
    "..yywwwwww....",
    "....rwwwww....",
    "....rwwwwwwww.",
    ".....wwwwwwwwg",
    ".....wwwwwwwgg",
    ".....wwwwwwwgg",
    "......wwwwwww.",
    "........y.y...",
    "........y.y...",
    ".......yy.yy..",
]


def chicken():
    im, _ = new(14, 15)
    pixmap(im, 0, 0, CHICKEN_ROWS, {"r": (220, 30, 30, 255), "w": (250, 250, 250, 255),
                                     "k": (20, 20, 20, 255), "y": (240, 170, 30, 255), "g": (200, 200, 200, 255)})
    save(im, "chicken.png")


# ---------------------------------------------------------------- РИТМ
def blocks():
    im, d = new(96, 16)
    def tile(ix, base, ore=None, seed=0):
        x0 = ix * 16
        noise_fill(im, x0, 0, 16, 16, base, 0.12, seed)
        if ore:
            rr = random.Random(seed + 99)
            for _ in range(5):
                ox, oy = rr.randint(1, 12), rr.randint(1, 12)
                rect(d, x0 + ox, oy, 3, 2, ore)
                rect(d, x0 + ox + 1, oy - 1 if oy > 0 else oy, 1, 1, shade(ore, 1.3))
        d.rectangle([x0, 0, x0 + 15, 15], outline=shade(base, 0.6))
    tile(0, (134, 96, 67, 255), None, 1)                 # земля
    rect(d, 0, 0, 16, 4, (90, 160, 60, 255))
    noise_fill(im, 0, 0, 16, 4, (90, 160, 60, 255), 0.15, 5)
    tile(1, (125, 125, 125, 255), None, 2)               # камень
    tile(2, (125, 125, 125, 255), (30, 30, 30, 255), 3)  # уголь
    tile(3, (125, 125, 125, 255), (215, 170, 140, 255), 4)  # железо
    tile(4, (125, 125, 125, 255), (250, 210, 60, 255), 5)   # золото
    tile(5, (125, 125, 125, 255), (90, 235, 225, 255), 6)   # алмаз
    save(im, "blocks.png")


def ritm_bg():
    im, d = new(320, 180)
    noise_fill(im, 0, 0, 320, 180, (40, 38, 48, 255), 0.15, 11)
    rr = random.Random(5)
    for _ in range(70):
        x, y = rr.randint(0, 316), rr.randint(0, 176)
        c = rr.choice([(90, 235, 225, 255), (250, 210, 60, 255), (215, 170, 140, 255), (255, 80, 200, 255)])
        rect(d, x, y, 2, 2, c)
    # сталактиты
    for x in range(0, 320, 14):
        h = rr.randint(6, 22)
        d.polygon([(x, 0), (x + 10, 0), (x + 5, h)], fill=(28, 26, 34, 255))
    # колонки-динамики
    for x in (14, 262):
        rect(d, x, 60, 44, 100, (25, 25, 30, 255))
        ell(d, x + 6, 70, 32, 32, (60, 60, 70, 255))
        ell(d, x + 14, 78, 16, 16, (15, 15, 20, 255))
        ell(d, x + 10, 110, 24, 24, (60, 60, 70, 255))
        ell(d, x + 16, 116, 12, 12, (15, 15, 20, 255))
    save(im, "ritm_bg.png")


# ---------------------------------------------------------------- ВИКТОРИНА
def quiz_bg():
    im, d = new(320, 180)
    vgrad(d, 0, 0, 320, 180, (20, 10, 60), (60, 20, 90), 10)
    # лучи прожекторов
    beams = Image.new("RGBA", (320, 180), (0, 0, 0, 0))
    bd = ImageDraw.Draw(beams)
    for sx, ex in [(30, 110), (290, 210), (160, 160)]:
        bd.polygon([(sx - 6, 0), (sx + 6, 0), (ex + 40, 150), (ex - 40, 150)], fill=(255, 250, 200, 40))
    im.alpha_composite(beams)
    # лампы
    for x in range(10, 320, 20):
        ell(d, x, 2, 6, 6, (255, 240, 120, 255))
    # сцена
    ell(d, 40, 112, 240, 40, (90, 40, 140, 255))
    ell(d, 50, 116, 220, 32, (140, 70, 200, 255))
    ell(d, 60, 120, 200, 24, (190, 110, 240, 255))
    # логотип-ромб
    d.polygon([(160, 20), (200, 45), (160, 70), (120, 45)], fill=(30, 20, 90, 255), outline=(255, 210, 60, 255))
    d.polygon([(160, 28), (190, 45), (160, 62), (130, 45)], outline=(90, 235, 140, 255))
    # пол зрителей
    rect(d, 0, 150, 320, 30, (15, 8, 30, 255))
    save(im, "quiz_bg.png")


def audience():
    """Ряд зрителей-мобов (головы), рисуется поверх низа экрана и подпрыгивает при аплодисментах."""
    im, d = new(320, 34)
    kinds = ["creeper", "zombie", "pig", "skeleton", "villager", "chicken"]
    rr = random.Random(3)
    for i, x in enumerate(range(2, 320, 18)):
        k = rr.choice(kinds)
        y = 6 + (i % 2) * 6
        if k == "creeper":
            noise_fill(im, x, y, 16, 16, (80, 170, 70, 255), 0.15, i)
            rect(d, x + 3, y + 4, 3, 3, (20, 20, 20, 255)); rect(d, x + 10, y + 4, 3, 3, (20, 20, 20, 255))
            rect(d, x + 6, y + 7, 4, 6, (20, 20, 20, 255))
        elif k == "zombie":
            rect(d, x, y, 16, 16, (80, 140, 90, 255)); rect(d, x, y, 16, 4, (50, 90, 60, 255))
            rect(d, x + 3, y + 7, 3, 3, (20, 30, 20, 255)); rect(d, x + 10, y + 7, 3, 3, (20, 30, 20, 255))
        elif k == "pig":
            rect(d, x, y, 16, 16, (240, 160, 170, 255)); rect(d, x + 4, y + 9, 8, 5, (220, 120, 140, 255))
            rect(d, x + 2, y + 5, 3, 3, (30, 30, 30, 255)); rect(d, x + 11, y + 5, 3, 3, (30, 30, 30, 255))
        elif k == "skeleton":
            rect(d, x, y, 16, 16, (200, 200, 200, 255))
            rect(d, x + 3, y + 5, 4, 4, (40, 40, 40, 255)); rect(d, x + 9, y + 5, 4, 4, (40, 40, 40, 255))
            rect(d, x + 4, y + 12, 8, 2, (60, 60, 60, 255))
        elif k == "villager":
            rect(d, x, y, 16, 16, (190, 140, 105, 255)); rect(d, x + 6, y + 6, 4, 9, (150, 105, 75, 255))
            rect(d, x + 2, y + 4, 12, 2, (70, 45, 30, 255))
        else:
            rect(d, x + 2, y + 2, 12, 14, (250, 250, 250, 255)); rect(d, x + 6, y, 4, 3, (220, 30, 30, 255))
            rect(d, x + 4, y + 6, 2, 2, (20, 20, 20, 255)); rect(d, x + 10, y + 6, 2, 2, (20, 20, 20, 255))
            rect(d, x + 6, y + 9, 4, 3, (240, 170, 30, 255))
        rect(d, x, y + 16, 16, 34 - y - 16, (25, 15, 45, 255))
    save(im, "audience.png")


def host():
    im, d = new(48, 80)
    suit = (25, 25, 40, 255)
    rect(d, 10, 34, 28, 34, suit)
    d.polygon([(18, 34), (30, 34), (24, 50)], fill=(250, 250, 250, 255))  # рубашка
    d.polygon([(20, 36), (28, 36), (24, 40)], fill=(220, 30, 60, 255))    # бабочка
    rect(d, 2, 36, 8, 28, suit)
    rect(d, 38, 36, 8, 22, suit)
    rect(d, 10, 68, 12, 12, (15, 15, 20, 255))
    rect(d, 26, 68, 12, 12, (15, 15, 20, 255))
    villager_head(d, im, 12, 6, suit)
    rect(d, 12, 2, 24, 6, (60, 40, 25, 255))   # причёска
    rect(d, 10, 4, 4, 8, (60, 40, 25, 255))
    # микрофон
    rect(d, 40, 40, 6, 6, (60, 60, 60, 255))
    ell(d, 39, 34, 8, 8, (150, 150, 160, 255))
    rect(d, 42, 46, 2, 14, (30, 30, 30, 255))
    save(im, "host.png")


# ---------------------------------------------------------------- ФАЙТИНГ
def fight_bg():
    im, d = new(320, 180)
    vgrad(d, 0, 0, 320, 120, (250, 120, 70), (255, 220, 140), 9)
    ell(d, 220, 40, 50, 50, (255, 250, 200, 255))
    # горы
    d.polygon([(0, 120), (60, 50), (120, 120)], fill=(120, 80, 110, 255))
    d.polygon([(80, 120), (170, 40), (260, 120)], fill=(100, 60, 100, 255))
    d.polygon([(200, 120), (280, 60), (340, 120)], fill=(120, 80, 110, 255))
    d.polygon([(150, 58), (170, 40), (190, 58)], fill=(255, 255, 255, 255))
    # зрители-силуэты
    rr = random.Random(9)
    for x in range(0, 320, 7):
        h = rr.randint(10, 18)
        rect(d, x, 120 - h, 6, h, (60, 30, 50, 255))
        rect(d, x + 1, 118 - h - 4, 4, 5, (60, 30, 50, 255))
    # земля — блоки травы
    for i in range(0, 320, 16):
        noise_fill(im, i, 140, 16, 40, (134, 96, 67, 255), 0.1, i)
        noise_fill(im, i, 136, 16, 6, (90, 160, 60, 255), 0.15, i + 1)
        d.line([i, 136, i, 180], fill=(100, 70, 50, 255))
    rect(d, 0, 120, 320, 16, (160, 140, 110, 255))
    noise_fill(im, 0, 120, 320, 16, (160, 140, 110, 255), 0.06, 77)
    save(im, "fight_bg.png")


def fighter_player():
    """4 кадра 32x48: стойка, удар, блок, ранен."""
    im, d = new(128, 48)
    skin, shirt, pants, hair = (220, 170, 130, 255), (40, 170, 190, 255), (60, 60, 160, 255), (90, 60, 30, 255)
    for f in range(4):
        x0 = f * 32
        lean = 2 if f == 3 else 0
        # ноги
        rect(d, x0 + 10 - lean, 32, 5, 16, pants)
        rect(d, x0 + 17 - lean, 32, 5, 16, pants)
        rect(d, x0 + 9 - lean, 44, 6, 4, (70, 70, 70, 255))
        rect(d, x0 + 17 - lean, 44, 6, 4, (70, 70, 70, 255))
        # тело
        rect(d, x0 + 9 - lean, 16, 14, 16, shirt)
        # голова
        rect(d, x0 + 9 - lean * 2, 2, 14, 14, skin)
        rect(d, x0 + 9 - lean * 2, 2, 14, 4, hair)
        rect(d, x0 + 9 - lean * 2, 2, 3, 8, hair)
        if f == 3:
            rect(d, x0 + 15 - lean * 2, 8, 2, 1, (40, 40, 40, 255)); rect(d, x0 + 19 - lean * 2, 8, 2, 1, (40, 40, 40, 255))
        else:
            rect(d, x0 + 15, 8, 2, 2, (60, 60, 200, 255)); rect(d, x0 + 19, 8, 2, 2, (60, 60, 200, 255))
        rect(d, x0 + 16 - lean * 2, 12, 4, 1, (150, 90, 70, 255))
        # руки
        if f == 1:      # удар вправо
            rect(d, x0 + 18, 18, 14, 5, shirt)
            rect(d, x0 + 28, 17, 4, 7, skin)
            rect(d, x0 + 6, 18, 4, 12, shirt)
        elif f == 2:    # блок — руки перед лицом
            rect(d, x0 + 20, 6, 5, 14, shirt)
            rect(d, x0 + 20, 4, 5, 4, skin)
            rect(d, x0 + 24, 8, 5, 12, shirt)
            rect(d, x0 + 24, 6, 5, 4, skin)
        else:
            rect(d, x0 + 5 - lean, 17, 4, 13, shirt)
            rect(d, x0 + 23 - lean, 17, 4, 13, shirt)
            rect(d, x0 + 5 - lean, 29, 4, 3, skin)
            rect(d, x0 + 23 - lean, 29, 4, 3, skin)
    save(im, "fighter_player.png")


def fighter_creeper():
    """4 кадра: стойка, атака (наклон), шипит (белый), закопчённый."""
    im, d = new(128, 48)
    px = im.load()
    for f in range(4):
        rr = random.Random(f + 40)
        x0 = f * 32
        if f == 2:
            pal = [(240, 255, 240), (220, 245, 220), (255, 255, 255)]
        elif f == 3:
            pal = [(60, 80, 55), (40, 55, 40), (80, 100, 70)]
        else:
            pal = [(80, 170, 70), (60, 140, 55), (110, 200, 95), (40, 110, 40)]
        def body(x, y, w, h):
            for j in range(h):
                for i in range(w):
                    if 0 <= x + i < 128:
                        px[x + i, y + j] = rr.choice(pal) + (255,)
        off = 4 if f == 1 else 0
        body(x0 + 8 - off, 0, 16, 16)
        body(x0 + 10 - off // 2, 16, 12, 22)
        body(x0 + 7, 38, 8, 10)
        body(x0 + 17, 38, 8, 10)
        face = (20, 20, 20, 255)
        hx = x0 + 8 - off
        rect(d, hx + 2, 4, 4, 4, face); rect(d, hx + 10, 4, 4, 4, face)
        rect(d, hx + 6, 8, 4, 4, face); rect(d, hx + 4, 10, 3, 5, face); rect(d, hx + 9, 10, 3, 5, face)
        # галстук!
        d.polygon([(x0 + 15 - off // 2, 16), (x0 + 17 - off // 2, 16), (x0 + 18 - off // 2, 28), (x0 + 16 - off // 2, 31), (x0 + 14 - off // 2, 28)], fill=(200, 30, 40, 255))
    save(im, "fighter_creeper.png")


def fighter_zombie():
    """4 кадра: стойка, атака (руки вперёд), однорукий, однорукий-атака."""
    im, d = new(128, 48)
    skin, shirt, pants = (90, 150, 95, 255), (40, 150, 160, 255), (70, 60, 150, 255)
    for f in range(4):
        x0 = f * 32
        rect(d, x0 + 10, 32, 5, 16, pants); rect(d, x0 + 17, 32, 5, 16, pants)
        rect(d, x0 + 9, 16, 14, 16, shirt)
        rect(d, x0 + 9, 2, 14, 14, skin)
        rect(d, x0 + 9, 2, 14, 3, (60, 110, 65, 255))
        rect(d, x0 + 14, 8, 3, 2, (20, 30, 20, 255)); rect(d, x0 + 19, 8, 3, 2, (20, 30, 20, 255))
        rect(d, x0 + 15, 12, 5, 2, (50, 80, 50, 255))
        reach = 14 if f in (1, 3) else 10
        rect(d, x0 + 18, 17, reach, 4, shirt)            # передняя рука
        rect(d, x0 + 18 + reach - 3, 17, 3, 4, skin)
        if f < 2:
            rect(d, x0 + 18, 23, reach, 4, shirt)        # вторая рука
            rect(d, x0 + 18 + reach - 3, 23, 3, 4, skin)
        else:
            rect(d, x0 + 20, 23, 3, 3, (150, 30, 30, 255))  # плечо без руки
    save(im, "fighter_zombie.png")


def fighter_chicken():
    """4 кадра: стойка, клюёт, машет крыльями, танцует."""
    im, _ = new(128, 48)
    pal = {"r": (220, 30, 30, 255), "w": (250, 250, 250, 255), "k": (20, 20, 20, 255),
           "y": (240, 170, 30, 255), "g": (200, 200, 200, 255)}
    for f in range(4):
        rows = [r[::-1] for r in CHICKEN_ROWS]  # смотрит вправо
        if f == 1:
            rows = ["." * 14] + rows[:-1]
        if f == 2:
            rows = rows[:7] + ["wwww" + r[4:] for r in rows[7:11]] + rows[11:]
        pixmap(im, f * 32 + 2, 3 if f != 3 else 0, rows, pal, scale=2)
    save(im, "fighter_chicken.png")


def explosion():
    im, d = new(48, 48)
    rr = random.Random(2)
    for c, r in [((255, 240, 150, 255), 22), ((255, 170, 50, 255), 17), ((240, 80, 30, 255), 12), ((255, 255, 230, 255), 6)]:
        for _ in range(16):
            ox, oy = rr.randint(-4, 4), rr.randint(-4, 4)
            ell(d, 24 - r + ox, 24 - r + oy, r * 2 - 6, r * 2 - 6, c)
    for _ in range(30):
        x, y = rr.randint(0, 46), rr.randint(0, 46)
        rect(d, x, y, 2, 2, (80, 80, 80, 200))
    save(im, "explosion.png")


def zombie_arm():
    im, d = new(16, 6)
    rect(d, 0, 1, 12, 4, (40, 150, 160, 255))
    rect(d, 11, 0, 5, 6, (90, 150, 95, 255))
    rect(d, 0, 1, 2, 4, (150, 30, 30, 255))
    save(im, "zombie_arm.png")


# ---------------------------------------------------------------- ОБЩЕЕ
def tv():
    """Телевизор над головой игрока — 3 кадра помех, 32x26 каждый."""
    im, d = new(96, 26)
    for f in range(3):
        x0 = f * 32
        rect(d, x0, 0, 32, 22, (70, 50, 40, 255))
        rect(d, x0 + 1, 1, 30, 20, (110, 80, 60, 255))
        rr = random.Random(f * 13)
        for j in range(3, 19):
            for i in range(3, 24):
                v = rr.randint(60, 255)
                im.putpixel((x0 + i, j), (v, v, v, 255))
        rect(d, x0 + 25, 4, 4, 4, (200, 190, 170, 255))
        rect(d, x0 + 25, 10, 4, 4, (200, 190, 170, 255))
        rect(d, x0 + 4, 22, 4, 4, (50, 40, 30, 255))
        rect(d, x0 + 24, 22, 4, 4, (50, 40, 30, 255))
    save(im, "tv.png")


def bow():
    im, _ = new(16, 10)
    rows = ["rr..........rr..",
            "rrrr......rrrr..",
            "rrrrrr..rrrrrr..",
            "rrrrrrkkrrrrrr..",
            "rrrrrrkkrrrrrr..",
            "rrrr..rr..rrrr..",
            "rr...rr.rr..rr..",
            ".....r...r......",
            "....r.....r.....",
            "................"]
    rows = ["." + r[:-1] for r in rows]
    pixmap(im, 0, 0, rows, {"r": (235, 40, 90, 255), "k": (170, 20, 60, 255)})
    save(im, "bow.png")


if __name__ == "__main__":
    print("Рисую графику...")
    novella_bg(); novella_pigs(); hearts()
    court_bg(); save(judge(False), "judge.png"); save(judge(True), "judge_sleep.png"); creeper_tie(); chicken()
    blocks(); ritm_bg()
    quiz_bg(); audience(); host()
    fight_bg(); fighter_player(); fighter_creeper(); fighter_zombie(); fighter_chicken(); explosion(); zombie_arm()
    tv(); bow()
    print("Готово!")
