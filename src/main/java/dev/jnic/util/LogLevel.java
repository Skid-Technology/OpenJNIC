package dev.jnic.util;

public enum LogLevel {
    INSTANCE;

    public static final int LEVEL_NONE = 6;
    public static final int LEVEL_ERROR = 5;
    public static final int LEVEL_WARN = 4;
    public static final int LEVEL_INFO = 3;
    public static final int LEVEL_DEBUG = 2;
    public static final int LEVEL_TRACE = 1;
    public static boolean ERROR;
    public static boolean WARN;
    public static boolean INFO;
    public static boolean DEBUG;
    public static boolean TRACE;
}
