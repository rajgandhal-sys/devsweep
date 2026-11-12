package devsweep;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parsing and formatting of sizes and ages for the command line. */
public final class Units {

    private static final Pattern SIZE = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*([KMGT]?)I?B?", Pattern.CASE_INSENSITIVE);
    private static final Pattern AGE = Pattern.compile("(\\d+)\\s*([DWMY]?)", Pattern.CASE_INSENSITIVE);
    private static final String[] SIZE_UNITS = {"B", "KB", "MB", "GB", "TB"};

    private Units() {
    }

    /** Parses sizes such as {@code 500}, {@code 10MB}, {@code 1.5g} or {@code 2GiB} (binary units). */
    public static long parseSize(String text) {
        Matcher m = SIZE.matcher(text.trim());
        if (!m.matches()) {
            throw new IllegalArgumentException("Invalid size '" + text + "' (examples: 500KB, 10MB, 1.5GB)");
        }
        double value = Double.parseDouble(m.group(1));
        int power = switch (m.group(2).toUpperCase(Locale.ROOT)) {
            case "K" -> 1;
            case "M" -> 2;
            case "G" -> 3;
            case "T" -> 4;
            default -> 0;
        };
        return (long) (value * Math.pow(1024, power));
    }

    /**
     * Parses ages such as {@code 30} or {@code 30d} (days), {@code 6w} (weeks),
     * {@code 3m} (months of 30 days) or {@code 1y} (years of 365 days).
     */
    public static Duration parseAge(String text) {
        Matcher m = AGE.matcher(text.trim());
        if (!m.matches()) {
            throw new IllegalArgumentException("Invalid age '" + text + "' (examples: 30d, 6w, 3m, 1y)");
        }
        long n = Long.parseLong(m.group(1));
        long days = switch (m.group(2).toUpperCase(Locale.ROOT)) {
            case "W" -> n * 7;
            case "M" -> n * 30;
            case "Y" -> n * 365;
            default -> n;
        };
        return Duration.ofDays(days);
    }

    public static String formatSize(long bytes) {
        double value = bytes;
        int unit = 0;
        while (value >= 1024 && unit < SIZE_UNITS.length - 1) {
            value /= 1024;
            unit++;
        }
        return unit == 0
                ? bytes + " B"
                : String.format(Locale.ROOT, "%.1f %s", value, SIZE_UNITS[unit]);
    }

    public static String formatAge(Instant then, Instant now) {
        long days = Duration.between(then, now).toDays();
        if (days < 1) {
            return "today";
        }
        if (days < 60) {
            return days + "d ago";
        }
        if (days < 730) {
            return (days / 30) + "mo ago";
        }
        return (days / 365) + "y ago";
    }
}
