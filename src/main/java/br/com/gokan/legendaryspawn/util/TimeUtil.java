package br.com.gokan.legendaryspawn.util;

public final class TimeUtil {

    private TimeUtil() {
    }

    public static String formatDuration(long millis) {
        long totalSeconds = Math.max(0L, millis) / 1000L;
        long days = totalSeconds / 86400L;
        long hours = (totalSeconds % 86400L) / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;

        StringBuilder text = new StringBuilder();
        if (days > 0) {
            text.append(days).append("d ");
        }
        if (hours > 0 || days > 0) {
            text.append(hours).append("h ");
        }
        if (minutes > 0 || hours > 0 || days > 0) {
            text.append(minutes).append("m ");
        }
        text.append(seconds).append("s");
        return text.toString().trim();
    }
}
