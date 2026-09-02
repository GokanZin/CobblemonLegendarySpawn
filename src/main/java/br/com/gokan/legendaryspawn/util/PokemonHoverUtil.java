package br.com.gokan.legendaryspawn.util;

import br.com.gokan.legendaryspawn.config.LegendaryConfigManager;
import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.pokemon.EVs;
import com.cobblemon.mod.common.pokemon.IVs;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class PokemonHoverUtil {

    private PokemonHoverUtil() {
    }

    public static MutableComponent nameComponent(
            LegendaryConfigManager configManager,
            Pokemon pokemon,
            String displayName,
            Style inheritedStyle
    ) {
        return nameComponent(configManager, pokemon, displayName, inheritedStyle, "");
    }

    public static MutableComponent nameComponent(
            LegendaryConfigManager configManager,
            Pokemon pokemon,
            String displayName,
            Style inheritedStyle,
            String context
    ) {
        if (!configManager.hoverEnabledFor(context)) {
            return Component.literal(displayName).withStyle(inheritedStyle);
        }

        MutableComponent hover = buildHover(configManager, pokemon, displayName);
        HoverEvent hoverEvent = new HoverEvent(HoverEvent.Action.SHOW_TEXT, hover);
        MutableComponent root = Component.empty();
        root.append(Component.literal(displayName).withStyle(inheritedStyle.withHoverEvent(hoverEvent)));

        if (configManager.hoverHintEnabled()) {
            String hintText = configManager.hoverHintText();
            if (hintText != null && !hintText.isBlank()) {
                MutableComponent hint = TextUtil.component(hintText);
                hint.setStyle(hint.getStyle().withHoverEvent(hoverEvent));
                root.append(hint);
            }
        }
        return root;
    }

    private static MutableComponent buildHover(LegendaryConfigManager configManager, Pokemon pokemon, String displayName) {
        List<Move> moves = moves(pokemon);
        Map<String, String> values = placeholders(configManager, pokemon, displayName, moves);
        List<String> lines = configManager.hoverLines();
        MutableComponent root = Component.empty();
        for (int i = 0; i < lines.size(); i++) {
            String line = replaceLiteralPlaceholders(lines.get(i), values);
            root.append(TextUtil.component(line, (placeholder, style) ->
                    resolvePlaceholder(configManager, pokemon, moves, values, placeholder, style)));
            if (i + 1 < lines.size()) {
                root.append(Component.literal("\n"));
            }
        }
        return root;
    }

    private static MutableComponent resolvePlaceholder(
            LegendaryConfigManager configManager,
            Pokemon pokemon,
            List<Move> moves,
            Map<String, String> values,
            String rawPlaceholder,
            Style style
    ) {
        String placeholder = rawPlaceholder.toLowerCase(Locale.ROOT);
        if (placeholder.equals("nature")) {
            return Component.translatable(pokemon.getEffectiveNature().getDisplayName()).withStyle(style);
        }
        if (placeholder.equals("ability")) {
            return Component.translatable(pokemon.getAbility().getDisplayName()).withStyle(style);
        }
        if (placeholder.startsWith("move_") && !placeholder.endsWith("_pp") && !placeholder.endsWith("_max_pp")) {
            int slot = moveSlot(placeholder);
            if (slot >= 0 && slot < moves.size()) {
                return moves.get(slot).getDisplayName().copy().withStyle(style);
            }
            return Component.literal(configManager.hoverValue("empty-move", "None")).withStyle(style);
        }
        String value = values.get(placeholder);
        return value == null ? null : Component.literal(value).withStyle(style);
    }

    private static int moveSlot(String placeholder) {
        if (placeholder.length() != 6 || !placeholder.startsWith("move_")) {
            return -1;
        }
        char number = placeholder.charAt(5);
        return number >= '1' && number <= '4' ? number - '1' : -1;
    }

    private static List<Move> moves(Pokemon pokemon) {
        List<Move> moves = new ArrayList<>();
        for (Move move : pokemon.getMoveSet()) {
            moves.add(move);
        }
        return moves;
    }

    private static Map<String, String> placeholders(
            LegendaryConfigManager configManager,
            Pokemon pokemon,
            String displayName,
            List<Move> moves
    ) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("pokemon", displayName);
        values.put("level", Integer.toString(pokemon.getLevel()));
        values.put("shiny", pokemon.getShiny()
                ? configManager.hoverValue("shiny-yes", "Yes")
                : configManager.hoverValue("shiny-no", "No"));
        values.put("gender", formatGender(configManager, pokemon));

        String emptyMove = configManager.hoverValue("empty-move", "None");
        for (int slot = 0; slot < 4; slot++) {
            String prefix = "move_" + (slot + 1);
            if (slot < moves.size()) {
                Move move = moves.get(slot);
                values.put(prefix + "_pp", Integer.toString(move.getCurrentPp()));
                values.put(prefix + "_max_pp", Integer.toString(move.getMaxPp()));
            } else {
                values.put(prefix, emptyMove);
                values.put(prefix + "_pp", "0");
                values.put(prefix + "_max_pp", "0");
            }
        }

        appendIvs(values, pokemon.getIvs());
        appendEvs(values, pokemon.getEvs());
        return values;
    }

    private static void appendIvs(Map<String, String> values, IVs ivs) {
        int hp = ivs.getOrDefault(Stats.HP);
        int attack = ivs.getOrDefault(Stats.ATTACK);
        int defence = ivs.getOrDefault(Stats.DEFENCE);
        int specialAttack = ivs.getOrDefault(Stats.SPECIAL_ATTACK);
        int specialDefence = ivs.getOrDefault(Stats.SPECIAL_DEFENCE);
        int speed = ivs.getOrDefault(Stats.SPEED);
        int total = hp + attack + defence + specialAttack + specialDefence + speed;

        values.put("ivs_total", Integer.toString(total));
        values.put("ivs_max", Integer.toString(IVs.MAX_TOTAL));
        values.put("ivs_percent", Integer.toString(percent(total, IVs.MAX_TOTAL)));
        values.put("iv_hp", Integer.toString(hp));
        values.put("iv_atk", Integer.toString(attack));
        values.put("iv_def", Integer.toString(defence));
        values.put("iv_spa", Integer.toString(specialAttack));
        values.put("iv_spd", Integer.toString(specialDefence));
        values.put("iv_spe", Integer.toString(speed));
    }

    private static void appendEvs(Map<String, String> values, EVs evs) {
        int hp = evs.getOrDefault(Stats.HP);
        int attack = evs.getOrDefault(Stats.ATTACK);
        int defence = evs.getOrDefault(Stats.DEFENCE);
        int specialAttack = evs.getOrDefault(Stats.SPECIAL_ATTACK);
        int specialDefence = evs.getOrDefault(Stats.SPECIAL_DEFENCE);
        int speed = evs.getOrDefault(Stats.SPEED);
        int total = hp + attack + defence + specialAttack + specialDefence + speed;

        values.put("evs_total", Integer.toString(total));
        values.put("evs_max", Integer.toString(EVs.MAX_TOTAL_VALUE));
        values.put("evs_percent", Integer.toString(percent(total, EVs.MAX_TOTAL_VALUE)));
        values.put("ev_hp", Integer.toString(hp));
        values.put("ev_atk", Integer.toString(attack));
        values.put("ev_def", Integer.toString(defence));
        values.put("ev_spa", Integer.toString(specialAttack));
        values.put("ev_spd", Integer.toString(specialDefence));
        values.put("ev_spe", Integer.toString(speed));
    }

    private static int percent(int value, int maximum) {
        if (maximum <= 0) {
            return 0;
        }
        return (int) Math.round((double) value * 100.0D / maximum);
    }

    private static String replaceLiteralPlaceholders(String text, Map<String, String> placeholders) {
        String result = text == null ? "" : text;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return result;
    }

    private static String formatGender(LegendaryConfigManager configManager, Pokemon pokemon) {
        String raw = pokemon.getGender().name().toLowerCase(Locale.ROOT);
        return switch (raw) {
            case "male" -> configManager.hoverValue("gender-male", "Male");
            case "female" -> configManager.hoverValue("gender-female", "Female");
            case "genderless" -> configManager.hoverValue("gender-genderless", "Genderless");
            default -> configManager.hoverValue("gender-unknown", "Unknown");
        };
    }
}
