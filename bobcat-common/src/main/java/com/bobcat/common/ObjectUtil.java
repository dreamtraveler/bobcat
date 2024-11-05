package com.bobcat.common;

public final class ObjectUtil {

    private ObjectUtil() {
    }

    public static <T> T checkNotNull(T arg, String text) {
        if (arg == null) {
            throw new NullPointerException(text);
        }
        return arg;
    }

    public static <T> T[] checkNonEmpty(T[] array, String name) {
        if (checkNotNull(array, name).length == 0) {
            throw new IllegalArgumentException("Param '" + name + "' must not be empty");
        }
        return array;
    }

    public static String checkNonEmpty(final String value, final String name) {
        if (checkNotNull(value, name).isEmpty()) {
            throw new IllegalArgumentException("Param '" + name + "' must not be empty");
        }
        return value;
    }
}
