package com.cosmerearchives.androidimageeditor;

import java.util.regex.Pattern;

final class StableVersion {
    private static final String COMPONENT = "(?:0|[1-9][0-9]{0,2})";
    private static final Pattern TAG = Pattern.compile(
            "v(" + COMPONENT + ")\\.(" + COMPONENT + ")\\.(" + COMPONENT + ")");

    static int codeFromTag(String tag) {
        var match = TAG.matcher(tag);
        if (!match.matches()) return -1;
        return Integer.parseInt(match.group(1)) * 1_000_000
                + Integer.parseInt(match.group(2)) * 1_000
                + Integer.parseInt(match.group(3));
    }
}
