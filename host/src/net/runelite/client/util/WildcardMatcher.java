package net.runelite.client.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Same-name replacement: RuneLite's version uses Matcher.appendReplacement(StringBuilder), which
 * Android 9 lacks (Java 9 API); this is the same logic over a StringBuffer.
 */
public class WildcardMatcher {
    private static final Pattern WILDCARD_PATTERN = Pattern.compile("(?i)[^*]+|(\\*+)");

    public static boolean matches(String pattern, String text) {
        Matcher matcher = WILDCARD_PATTERN.matcher(pattern);
        StringBuffer buffer = new StringBuffer();
        buffer.append("(?i)");
        while (matcher.find()) {
            if (matcher.group(1) != null) {
                matcher.appendReplacement(buffer, ".*");
            } else {
                matcher.appendReplacement(buffer, Matcher.quoteReplacement(Pattern.quote(matcher.group(0))));
            }
        }
        matcher.appendTail(buffer);
        return text.matches(buffer.toString());
    }
}
