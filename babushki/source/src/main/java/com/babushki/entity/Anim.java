package com.babushki.entity;

/** Анимации бабушек (синхронизируются с клиентом). */
public enum Anim {
    NONE, HEAD_SHAKE, FINGER_WAG, WHISPER, HAND_OUT, WAVE;

    public static Anim byId(int id) {
        Anim[] v = values();
        return id >= 0 && id < v.length ? v[id] : NONE;
    }
}
