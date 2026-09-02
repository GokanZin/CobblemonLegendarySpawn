package br.com.gokan.legendaryspawn.util;

import br.com.gokan.core.gkapi.modules.chat.ChatColor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

import java.util.ArrayList;
import java.util.List;

public final class TextUtil {

    private TextUtil() {
    }

    public static MutableComponent component(String input) {
        return component(input, null);
    }

    public static MutableComponent component(String input, PlaceholderResolver resolver) {
        return parseText(normalizeLineBreaks(input), Style.EMPTY, resolver);
    }

    public static String humanizeResourcePath(String raw) {
        if (raw == null || raw.isBlank()) {
            return "Unknown";
        }
        String path = raw;
        int separator = raw.indexOf(':');
        if (separator >= 0 && separator < raw.length() - 1) {
            path = raw.substring(separator + 1);
        }

        StringBuilder builder = new StringBuilder(path.length());
        boolean capitalize = true;
        for (int i = 0; i < path.length(); i++) {
            char c = path.charAt(i);
            if (c == '_' || c == '-' || c == '.') {
                builder.append(' ');
                capitalize = true;
                continue;
            }
            builder.append(capitalize ? Character.toUpperCase(c) : c);
            capitalize = false;
        }
        return builder.toString().trim();
    }

    private static MutableComponent parseText(String text, Style initialStyle, PlaceholderResolver resolver) {
        if (text == null || text.isEmpty()) {
            return Component.empty();
        }

        MutableComponent root = Component.empty();
        StringBuilder current = new StringBuilder();
        Style style = initialStyle;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '&' && i + 1 < text.length() && ChatColor.isValidCode(text.charAt(i + 1))) {
                appendLiteral(root, current, style);
                style = applyLegacyColor(style, text.charAt(++i));
                continue;
            }

            HexTag hexTag = readHexTag(text, i);
            if (hexTag != null) {
                appendLiteral(root, current, style);
                style = style.withColor(TextColor.fromRgb(hexTag.rgb()));
                i = hexTag.endIndex();
                continue;
            }

            GradientTag gradientTag = readGradientTag(text, i);
            if (gradientTag != null) {
                appendLiteral(root, current, style);
                root.append(buildGradientComponent(gradientTag.text(), gradientTag.startRgb(), gradientTag.endRgb(), style));
                i = gradientTag.endIndex();
                continue;
            }

            if (c == '{') {
                int end = text.indexOf('}', i + 1);
                if (end > i + 1) {
                    String placeholder = text.substring(i + 1, end);
                    if (resolver != null) {
                        MutableComponent component = resolver.resolve(placeholder, style);
                        if (component != null) {
                            appendLiteral(root, current, style);
                            root.append(component);
                            i = end;
                            continue;
                        }
                    }
                }
            }

            current.append(c);
        }

        appendLiteral(root, current, style);
        return root;
    }

    private static void appendLiteral(MutableComponent root, StringBuilder current, Style style) {
        if (current.isEmpty()) {
            return;
        }
        root.append(Component.literal(current.toString()).withStyle(style));
        current.setLength(0);
    }

    private static Style applyLegacyColor(Style current, char code) {
        var formatting = ChatColor.fromCode(code);
        if (formatting == null) {
            return current;
        }
        if (formatting.getName().equalsIgnoreCase("reset")) {
            return Style.EMPTY;
        }
        return current.applyTo(ChatColor.toStyle(formatting));
    }

    private static MutableComponent buildGradientComponent(String text, int startRgb, int endRgb, Style inheritedStyle) {
        MutableComponent root = Component.empty();
        List<GradientCharacter> characters = new ArrayList<>();
        Style style = inheritedStyle;

        for (int index = 0; index < text.length();) {
            if (text.charAt(index) == '&' && index + 1 < text.length() && ChatColor.isValidCode(text.charAt(index + 1))) {
                style = applyLegacyColor(style, text.charAt(index + 1));
                index += 2;
                continue;
            }
            int codePoint = text.codePointAt(index);
            characters.add(new GradientCharacter(codePoint, style));
            index += Character.charCount(codePoint);
        }

        int visibleLength = Math.max(1, characters.size() - 1);
        for (int index = 0; index < characters.size(); index++) {
            GradientCharacter character = characters.get(index);
            double percent = characters.size() == 1 ? 0.0D : (double) index / visibleLength;
            int rgb = interpolateRgb(startRgb, endRgb, percent);
            root.append(Component.literal(new String(Character.toChars(character.codePoint())))
                    .withStyle(character.style().withColor(TextColor.fromRgb(rgb))));
        }

        return root;
    }

    private static int interpolateRgb(int startRgb, int endRgb, double percent) {
        int startRed = (startRgb >> 16) & 0xFF;
        int startGreen = (startRgb >> 8) & 0xFF;
        int startBlue = startRgb & 0xFF;

        int endRed = (endRgb >> 16) & 0xFF;
        int endGreen = (endRgb >> 8) & 0xFF;
        int endBlue = endRgb & 0xFF;

        int red = (int) Math.round(startRed + (endRed - startRed) * percent);
        int green = (int) Math.round(startGreen + (endGreen - startGreen) * percent);
        int blue = (int) Math.round(startBlue + (endBlue - startBlue) * percent);
        return (red << 16) | (green << 8) | blue;
    }

    private static HexTag readHexTag(String text, int index) {
        int end = text.indexOf('>', index);
        if (end < 0) {
            return null;
        }
        String tag = text.substring(index, end + 1);
        if ((!tag.startsWith("<&#") && !tag.startsWith("<#")) || tag.length() < 9) {
            return null;
        }
        Integer rgb = parseRgb(tag.substring(1, tag.length() - 1));
        if (rgb == null) {
            return null;
        }
        return new HexTag(rgb, end);
    }

    private static GradientTag readGradientTag(String text, int index) {
        if (!text.startsWith("<gradient:", index)) {
            return null;
        }
        int headerEnd = text.indexOf('>', index);
        if (headerEnd < 0) {
            return null;
        }
        String header = text.substring(index + "<gradient:".length(), headerEnd);
        String[] colors = header.split(":");
        if (colors.length < 2) {
            return null;
        }
        Integer startRgb = parseRgb(colors[0]);
        Integer endRgb = parseRgb(colors[1]);
        if (startRgb == null || endRgb == null) {
            return null;
        }
        int closeStart = text.indexOf("</gradient>", headerEnd + 1);
        if (closeStart < 0) {
            return null;
        }
        String content = text.substring(headerEnd + 1, closeStart);
        return new GradientTag(content, startRgb, endRgb, closeStart + "</gradient>".length() - 1);
    }

    private static Integer parseRgb(String raw) {
        if (raw == null) {
            return null;
        }
        String hex = raw.trim();
        if (hex.startsWith("&")) {
            hex = hex.substring(1);
        }
        if (hex.startsWith("#")) {
            hex = hex.substring(1);
        }
        if (hex.length() != 6) {
            return null;
        }
        try {
            return Integer.parseInt(hex, 16);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    public static String normalizeLineBreaks(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        return text.replace("\\r\\n", "\n").replace("\\n", "\n");
    }


    public static String trimOuterBlankLines(String text) {
        String normalized = normalizeLineBreaks(text);
        if (normalized.isEmpty()) {
            return "";
        }
        String[] lines = normalized.split("\n", -1);
        int start = 0;
        int end = lines.length;
        while (start < end && lines[start].isBlank()) start++;
        while (end > start && lines[end - 1].isBlank()) end--;
        if (start == end) return "";
        return String.join("\n", java.util.Arrays.copyOfRange(lines, start, end));
    }

    @FunctionalInterface
    public interface PlaceholderResolver {
        MutableComponent resolve(String placeholder, Style inheritedStyle);
    }

    private record HexTag(int rgb, int endIndex) {
    }

    private record GradientTag(String text, int startRgb, int endRgb, int endIndex) {
    }

    private record GradientCharacter(int codePoint, Style style) {
    }
}
