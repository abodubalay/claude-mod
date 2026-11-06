package com.smenazhanra;

/** Все жанры мода. id — это название для команды /zhanr start. */
public enum Genre {
    NOVELLA("novella", "Визуальная новелла «Свинья моей мечты»"),
    SUD("sud", "Суд «Крипер против тебя»"),
    RITM("ritm", "Ритм-игра «Копай в такт»"),
    VIKTORINA("viktorina", "Викторина «Кто хочет стать изумрудным миллионером»"),
    FIGHTING("fighting", "Файтинг «Куриный Комбат»");

    public final String id;
    public final String title;

    Genre(String id, String title) {
        this.id = id;
        this.title = title;
    }

    public static Genre byId(String id) {
        for (Genre g : values()) if (g.id.equalsIgnoreCase(id)) return g;
        return null;
    }
}
