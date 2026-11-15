package dev.jnic.util;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class Log {
    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("HH-mm-ss.SSS", Locale.ROOT);
    private static boolean debug;

    private Log() {
    }

    public static void setDebug(boolean value) {
        debug = value;
    }

    public static boolean isDebugEnabled() {
        return debug;
    }

    public static void info(String message) {
        write("INFO", message);
    }

    public static void debug(String message) {
        if (debug) write("DEBUG", message);
    }

    public static void warn(String message) {
        write("WARN", message);
    }

    private static void write(String level, String message) {
        System.out.println(LocalTime.now().format(TIME) + "  " + level + ": " + message);
    }
}
