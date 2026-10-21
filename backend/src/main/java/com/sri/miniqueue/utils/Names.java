package com.sri.miniqueue.utils;

import com.sri.miniqueue.exception.CustomException;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * The one place a topic or queue name from a request is cleaned and checked.
 * Every service method calls this before using a name, so the rules cannot drift
 * between create, publish and consume.
 */
public class Names {

    private static final Pattern VALID = Pattern.compile("[a-z0-9][a-z0-9._-]{0,99}");

    private Names(){

    }

    public static String normalize(String raw, String kind) {
        if (raw == null || raw.isBlank()){
            throw new CustomException(kind+" name is required");
        }
        String name = raw.trim().toLowerCase(Locale.ROOT);
        if (!VALID.matcher(name).matches()){
            throw new CustomException(kind+" name is invalid: use 1 to 100 characters from a-z, 0-9, '.', '_' and '-', starting with a letter or digit");
        }
        return name;
    }
}
