package net.runelite.client.util;

import com.google.common.base.CharMatcher;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import java.util.Collection;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.text.WordUtils;
import org.apache.commons.text.similarity.JaroWinklerDistance;

public class Text {
    private static final JaroWinklerDistance DISTANCE = new JaroWinklerDistance();
    private static final Pattern TAG_REGEXP = Pattern.compile("<([^>]*)>");
    private static final Splitter COMMA_SPLITTER = Splitter.on(",").omitEmptyStrings().trimResults();
    private static final Joiner COMMA_JOINER = Joiner.on(",").skipNulls();
    public static final CharMatcher JAGEX_PRINTABLE_CHAR_MATCHER = new JagexPrintableCharMatcher();

    public static List<String> fromCSV(String input) {
        return COMMA_SPLITTER.splitToList(input);
    }

    public static String toCSV(Collection<String> input) {
        return COMMA_JOINER.join(input);
    }

    public static String removeTags(String str) {
        return TAG_REGEXP.matcher(str).replaceAll("");
    }

    // Android 9 lacks Matcher.replaceAll(Function) and appendReplacement(StringBuilder) (Java 9);
    // these two methods are rewritten over StringBuffer, the rest is RuneLite's Text (jadx).
    public static String unescapeJagex(String str) {
        Matcher matcher = TAG_REGEXP.matcher(str);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String r;
            switch (matcher.group(1)) {
                case "br":
                case "n":
                    r = "\n";
                    break;
                case "lt":
                    r = "<";
                    break;
                case "gt":
                    r = ">";
                    break;
                case "at":
                    r = "@";
                    break;
                case "nbh":
                    r = "-";
                    break;
                default:
                    r = "";
            }
            matcher.appendReplacement(sb, Matcher.quoteReplacement(r));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    public static String removeFormattingTags(String str) {
        StringBuffer sb = new StringBuffer();
        Matcher matcher = TAG_REGEXP.matcher(str);
        while (matcher.find()) {
            matcher.appendReplacement(sb, "");
            String match = matcher.group(0);
            switch (match) {
                case "<lt>":
                case "<gt>":
                case "<at>":
                case "<nbh>":
                    sb.append(match);
                    break;
            }
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    public static String standardize(String str) {
        return removeTags(str).replace((char) 160, ' ').trim().toLowerCase();
    }

    public static String toJagexName(String str) {
        return CharMatcher.ascii().retainFrom(str.replaceAll("[ _-]", " ")).trim();
    }

    public static String sanitizeMultilineText(String str) {
        return removeTags(str.replace("-<br>", "-").replace("<br>", " ").replace("-<n>", "-").replace("<n>", " ").replaceAll("[ ]+", " "));
    }

    public static String escapeJagex(String str) {
        StringBuilder out = new StringBuilder(str.length());
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            if (c == '<') {
                out.append("<lt>");
            } else if (c == '>') {
                out.append("<gt>");
            } else if (c == '@') {
                out.append("<at>");
            } else if (c == '\n') {
                out.append("<br>");
            } else if (c != '\r') {
                out.append(c);
            }
        }
        return out.toString();
    }

    public static String sanitize(String name) {
        String cleaned = name.contains("<img") ? name.substring(name.lastIndexOf(62) + 1) : name;
        return cleaned.replace((char) 160, ' ');
    }

    public static String titleCase(Enum o) {
        String toString = o.toString();
        if (o.name().equals(toString)) {
            return WordUtils.capitalize(toString.toLowerCase(), new char[]{'_'}).replace("_", " ");
        }
        return toString;
    }

    public static boolean matchesSearchTerms(Iterable<String> searchTerms, Collection<String> keywords) {
        for (String term : searchTerms) {
            if (keywords.stream().noneMatch(t -> {
                return t.contains(term) || DISTANCE.apply(t, term).doubleValue() > 0.9d;
            })) {
                return false;
            }
        }
        return true;
    }
}
