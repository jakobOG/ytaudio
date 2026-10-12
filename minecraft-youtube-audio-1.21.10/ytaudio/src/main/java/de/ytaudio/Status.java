package de.ytaudio;

/** Letzte Statusmeldung (wird im Menü angezeigt). */
public final class Status {
    public static volatile String msg = "";
    private Status() {}
    public static void set(String s) { msg = s == null ? "" : s; }
}
