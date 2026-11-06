package com.babushki.entity;

import net.minecraft.network.chat.TextColor;

public enum Role {
    VALYA("Баба Валя", "Валь", 0xE53935),
    ZINA("Баба Зина", "Зин", 0xFF8C00);

    public final String displayName;
    /** Как к ней обращается подруга. */
    public final String vocative;
    public final int color;

    Role(String displayName, String vocative, int color) {
        this.displayName = displayName;
        this.vocative = vocative;
        this.color = color;
    }

    public TextColor textColor() { return TextColor.fromRgb(color); }

    public Role other() { return this == VALYA ? ZINA : VALYA; }
}
