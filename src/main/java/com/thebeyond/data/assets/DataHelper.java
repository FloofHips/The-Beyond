package com.thebeyond.data.assets;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class DataHelper {
    @SafeVarargs
    @SuppressWarnings("varargs")
    public static <T> Collection<T> takeAll(Collection<? extends T> src, T... items) {
        List<T> ret = Arrays.asList(items);
        for (T item : items) {
            if (!src.contains(item)) {
                return Collections.emptyList();
            }
        }
        if (!src.removeAll(ret)) {
            return Collections.emptyList();
        }
        return ret;
    }

    public static <T> Collection<T> takeAll(Collection<T> src, Predicate<T> pred) {
        List<T> ret = new ArrayList<>();

        Iterator<T> iter = src.iterator();
        while (iter.hasNext()) {
            T item = iter.next();
            if (pred.test(item)) {
                iter.remove();
                ret.add(item);
            }
        }

        if (ret.isEmpty()) {
            return Collections.emptyList();
        }
        return ret;
    }

    public static <T> Collection<T> getAll(Collection<? extends T> src, T... items) {
        return List.copyOf(getAll(src, t -> Arrays.stream(items).anyMatch(tAgain -> tAgain.getClass().isInstance(t))));
    }

    public static <T> Collection<T> getAll(Collection<T> src, Predicate<T> pred) {
        return src.stream().filter(pred).collect(Collectors.toList());
    }

    public static String toTitleCase(String givenString, String regex) {
        String[] stringArray = givenString.split(regex);
        StringBuilder stringBuilder = new StringBuilder();
        for (String string : stringArray) {
            stringBuilder.append(Character.toUpperCase(string.charAt(0))).append(string.substring(1)).append(regex);
        }
        return stringBuilder.toString().trim().replaceAll(regex, " ").substring(0, stringBuilder.length() - 1);
    }
}
