package br.com.gokan.legendaryspawn.config;

import java.util.List;
import java.util.Map;
import java.util.Set;

public record LegendarySettings(
        boolean enabled,
        int minimumOnlinePlayers,
        boolean pauseTimerWhenBelowMinimumPlayers,
        double spawnChance,
        boolean debug,
        Interval interval,
        Spawn spawn,
        Worlds worlds,
        PokemonPool pokemonPool,
        Blacklist blacklist,
        Biomes biomes,
        Protection protection,
        Announcement announcement,
        Weights weights,
        Cooldowns cooldowns,
        Limits limits,
        Bossbar bossbar,
        Events events,
        Map<String, PokemonOverride> pokemonOverrides
) {
    public record Interval(int minMinutes, int maxMinutes) {}

    public record Spawn(
            int minDistance,
            int maxDistance,
            int attempts,
            int minLevel,
            int maxLevel,
            boolean allowWater,
            boolean countTowardsSpawnCap,
            int shinyOneInChance
    ) {}

    public record Worlds(boolean allowAllWorlds, Set<String> allowedWorlds, Set<String> blockedWorlds) {}
    public record PokemonPool(List<String> requiredLabels, Set<String> specificPokemon) {}
    public record Blacklist(List<String> labels, Set<String> pokemon) {}

    public record Biomes(
            boolean enabled,
            boolean useCobblemonSpawnData,
            MissingBiomeDataBehavior whenNoSpawnData
    ) {}

    public enum MissingBiomeDataBehavior { ALLOW_ANY, REJECT }

    public record Protection(
            boolean enabled,
            int durationMinutes,
            boolean preventNaturalDespawn,
            boolean restoreIfRemoved
    ) {}

    public record Announcement(boolean enabled, String message, boolean showCoordinates) {}

    public record Weights(boolean enabled, double defaultWeight, Map<String, Double> labelWeights) {}

    public record Cooldowns(
            boolean speciesEnabled,
            int defaultSpeciesMinutes,
            Map<String, Integer> speciesOverrides,
            boolean globalAfterEventEnabled,
            int capturedMinutes,
            int defeatedMinutes
    ) {}

    public record Limits(boolean activeLimitEnabled, int maxActive, boolean applyToForce) {}

    public record Bossbar(
            boolean enabled,
            String title,
            String color,
            String overlay,
            boolean showToAllPlayers
    ) {}

    public record Events(
            boolean captured,
            boolean defeated,
            boolean killed,
            boolean protectionExpired,
            boolean despawned
    ) {}

    public record PokemonOverride(
            OverrideMode worldMode,
            Set<String> allowedWorlds,
            Set<String> blockedWorlds,
            OverrideMode biomeMode,
            Set<String> allowedBiomes,
            Set<String> blockedBiomes
    ) {
        public static PokemonOverride empty() {
            return new PokemonOverride(OverrideMode.INHERIT, Set.of(), Set.of(), OverrideMode.INHERIT, Set.of(), Set.of());
        }
    }

    public enum OverrideMode { INHERIT, REPLACE }
}
