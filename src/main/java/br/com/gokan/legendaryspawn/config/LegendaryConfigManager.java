package br.com.gokan.legendaryspawn.config;

import br.com.gokan.core.gkapi.modules.config.Config;
import br.com.gokan.core.gkapi.modules.config.ConfigSection;
import br.com.gokan.legendaryspawn.LegendarySpawn;
import br.com.gokan.legendaryspawn.spawn.BiomeSpawnIndex;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.pokemon.Species;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LegendaryConfigManager {

    private static final Pattern COMMAND_LITERAL = Pattern.compile("[a-z0-9_:-]+");
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([^{}]+)}");
    private static final Set<String> ANNOUNCEMENT_PLACEHOLDERS = Set.of(
            "pokemon", "level", "x", "y", "z", "player", "dimension", "biome", "biome_id"
    );
    private static final Set<String> HOVER_PLACEHOLDERS = Set.of(
            "pokemon", "level", "shiny", "nature", "ability", "gender",
            "move_1", "move_1_pp", "move_1_max_pp",
            "move_2", "move_2_pp", "move_2_max_pp",
            "move_3", "move_3_pp", "move_3_max_pp",
            "move_4", "move_4_pp", "move_4_max_pp",
            "ivs_total", "ivs_max", "ivs_percent",
            "iv_hp", "iv_atk", "iv_def", "iv_spa", "iv_spd", "iv_spe",
            "evs_total", "evs_max", "evs_percent",
            "ev_hp", "ev_atk", "ev_def", "ev_spa", "ev_spd", "ev_spe"
    );
    private static final String CONFIG_RESOURCE = "assets/legendaryspawn/config.yml";
    private static final String COMMANDS_RESOURCE = "assets/legendaryspawn/commands.yml";
    private static final String MESSAGES_RESOURCE = "assets/legendaryspawn/messages.yml";
    private static final String WORLDS_RESOURCE = "assets/legendaryspawn/worlds.yml";
    private static final String POKEMON_RESOURCE = "assets/legendaryspawn/pokemon.yml";
    private static final String BLACKLIST_RESOURCE = "assets/legendaryspawn/blacklist.yml";
    private static final String BIOMES_RESOURCE = "assets/legendaryspawn/biomes.yml";
    private static final String PROTECTION_RESOURCE = "assets/legendaryspawn/protection.yml";
    private static final String WEIGHTS_RESOURCE = "assets/legendaryspawn/weights.yml";
    private static final String COOLDOWNS_RESOURCE = "assets/legendaryspawn/cooldowns.yml";
    private static final String LIMITS_RESOURCE = "assets/legendaryspawn/limits.yml";
    private static final String OVERRIDES_RESOURCE = "assets/legendaryspawn/pokemon-overrides.yml";
    private static final String BOSSBAR_RESOURCE = "assets/legendaryspawn/bossbar.yml";
    private static final String EVENTS_RESOURCE = "assets/legendaryspawn/events.yml";
    private static final String HOVER_RESOURCE = "assets/legendaryspawn/hover.yml";
    private static final String STATE_RESOURCE = "assets/legendaryspawn/state.yml";

    private final LegendarySpawn mod;
    private Config config;
    private Config commands;
    private Config messages;
    private Config worlds;
    private Config pokemon;
    private Config blacklist;
    private Config biomes;
    private Config protection;
    private Config weights;
    private Config cooldowns;
    private Config limits;
    private Config overrides;
    private Config bossbar;
    private Config events;
    private Config hover;
    private Config state;
    private LegendarySettings settings;
    private CommandSettings commandSettings;
    private List<String> loadErrors = List.of();
    private List<String> validationErrors = List.of();
    private List<String> validationWarnings = List.of();
    private boolean healthy;

    public LegendaryConfigManager(LegendarySpawn mod) {
        this.mod = mod;
    }

    public void load() {
        File folder = mod.getDataFolder();
        config = new Config(new File(folder, "config.yml"), CONFIG_RESOURCE);
        commands = new Config(new File(folder, "commands.yml"), COMMANDS_RESOURCE);
        messages = new Config(new File(folder, "messages.yml"), MESSAGES_RESOURCE);
        worlds = new Config(new File(folder, "worlds.yml"), WORLDS_RESOURCE);
        pokemon = new Config(new File(folder, "pokemon.yml"), POKEMON_RESOURCE);
        blacklist = new Config(new File(folder, "blacklist.yml"), BLACKLIST_RESOURCE);
        biomes = new Config(new File(folder, "biomes.yml"), BIOMES_RESOURCE);
        protection = new Config(new File(folder, "protection.yml"), PROTECTION_RESOURCE);
        weights = new Config(new File(folder, "weights.yml"), WEIGHTS_RESOURCE);
        cooldowns = new Config(new File(folder, "cooldowns.yml"), COOLDOWNS_RESOURCE);
        limits = new Config(new File(folder, "limits.yml"), LIMITS_RESOURCE);
        overrides = new Config(new File(folder, "pokemon-overrides.yml"), OVERRIDES_RESOURCE);
        bossbar = new Config(new File(folder, "bossbar.yml"), BOSSBAR_RESOURCE);
        events = new Config(new File(folder, "events.yml"), EVENTS_RESOURCE);
        hover = new Config(new File(folder, "hover.yml"), HOVER_RESOURCE);
        state = new Config(new File(folder, "state.yml"), STATE_RESOURCE);

        List<String> errors = new ArrayList<>();
        collectConfigErrors(config, errors);
        collectConfigErrors(commands, errors);
        collectConfigErrors(messages, errors);
        collectConfigErrors(worlds, errors);
        collectConfigErrors(pokemon, errors);
        collectConfigErrors(blacklist, errors);
        collectConfigErrors(biomes, errors);
        collectConfigErrors(protection, errors);
        collectConfigErrors(weights, errors);
        collectConfigErrors(cooldowns, errors);
        collectConfigErrors(limits, errors);
        collectConfigErrors(overrides, errors);
        collectConfigErrors(bossbar, errors);
        collectConfigErrors(events, errors);
        collectConfigErrors(hover, errors);
        collectConfigErrors(state, errors);
        validateConfigShape(config, errors);
        validateCommandsShape(commands, errors);
        validateMessagesShape(messages, errors);
        validateWorldsShape(worlds, errors);
        validatePokemonShape(pokemon, errors);
        validateBlacklistShape(blacklist, errors);
        validateBiomesShape(biomes, errors);
        validateProtectionShape(protection, errors);
        validateWeightsShape(weights, errors);
        validateCooldownsShape(cooldowns, errors);
        validateLimitsShape(limits, errors);
        validateOverridesShape(overrides, errors);
        validateBossbarShape(bossbar, errors);
        validateEventsShape(events, errors);
        validateHoverShape(hover, errors);
        validateStateShape(state, errors);

        settings = parseSettings(config, worlds, pokemon, blacklist, biomes, protection, weights, cooldowns, limits, overrides, bossbar, events, errors);
        commandSettings = parseCommands(commands, errors);
        loadErrors = List.copyOf(errors);
        validationErrors = loadErrors;
        validationWarnings = List.of();
        healthy = false;
    }

    public ValidationReport validateRuntime(MinecraftServer server) {
        List<String> errors = new ArrayList<>(loadErrors);
        List<String> warnings = new ArrayList<>();
        try {
            validateRuntimeSettings(server, settings, errors, warnings);
        } catch (RuntimeException exception) {
            errors.add("Runtime validation failed unexpectedly: " + describe(exception) + ". Check the server console for the stack trace.");
            LegendarySpawn.logRuntimeError("[Config] Runtime validation failed unexpectedly: " + describe(exception), exception);
        }
        validationErrors = List.copyOf(errors);
        validationWarnings = List.copyOf(warnings);
        healthy = errors.isEmpty();
        return new ValidationReport(validationErrors, validationWarnings);
    }

    public ReloadResult reload(MinecraftServer server) {
        File folder = mod.getDataFolder();
        Config candidateConfig = new Config(new File(folder, "config.yml"), CONFIG_RESOURCE);
        Config candidateCommands = new Config(new File(folder, "commands.yml"), COMMANDS_RESOURCE);
        Config candidateMessages = new Config(new File(folder, "messages.yml"), MESSAGES_RESOURCE);
        Config candidateWorlds = new Config(new File(folder, "worlds.yml"), WORLDS_RESOURCE);
        Config candidatePokemon = new Config(new File(folder, "pokemon.yml"), POKEMON_RESOURCE);
        Config candidateBlacklist = new Config(new File(folder, "blacklist.yml"), BLACKLIST_RESOURCE);
        Config candidateBiomes = new Config(new File(folder, "biomes.yml"), BIOMES_RESOURCE);
        Config candidateProtection = new Config(new File(folder, "protection.yml"), PROTECTION_RESOURCE);
        Config candidateWeights = new Config(new File(folder, "weights.yml"), WEIGHTS_RESOURCE);
        Config candidateCooldowns = new Config(new File(folder, "cooldowns.yml"), COOLDOWNS_RESOURCE);
        Config candidateLimits = new Config(new File(folder, "limits.yml"), LIMITS_RESOURCE);
        Config candidateOverrides = new Config(new File(folder, "pokemon-overrides.yml"), OVERRIDES_RESOURCE);
        Config candidateBossbar = new Config(new File(folder, "bossbar.yml"), BOSSBAR_RESOURCE);
        Config candidateEvents = new Config(new File(folder, "events.yml"), EVENTS_RESOURCE);
        Config candidateHover = new Config(new File(folder, "hover.yml"), HOVER_RESOURCE);
        Config candidateState = new Config(new File(folder, "state.yml"), STATE_RESOURCE);

        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        collectConfigErrors(candidateConfig, errors);
        collectConfigErrors(candidateCommands, errors);
        collectConfigErrors(candidateMessages, errors);
        collectConfigErrors(candidateWorlds, errors);
        collectConfigErrors(candidatePokemon, errors);
        collectConfigErrors(candidateBlacklist, errors);
        collectConfigErrors(candidateBiomes, errors);
        collectConfigErrors(candidateProtection, errors);
        collectConfigErrors(candidateWeights, errors);
        collectConfigErrors(candidateCooldowns, errors);
        collectConfigErrors(candidateLimits, errors);
        collectConfigErrors(candidateOverrides, errors);
        collectConfigErrors(candidateBossbar, errors);
        collectConfigErrors(candidateEvents, errors);
        collectConfigErrors(candidateHover, errors);
        collectConfigErrors(candidateState, errors);
        validateConfigShape(candidateConfig, errors);
        validateCommandsShape(candidateCommands, errors);
        validateMessagesShape(candidateMessages, errors);
        validateWorldsShape(candidateWorlds, errors);
        validatePokemonShape(candidatePokemon, errors);
        validateBlacklistShape(candidateBlacklist, errors);
        validateBiomesShape(candidateBiomes, errors);
        validateProtectionShape(candidateProtection, errors);
        validateWeightsShape(candidateWeights, errors);
        validateCooldownsShape(candidateCooldowns, errors);
        validateLimitsShape(candidateLimits, errors);
        validateOverridesShape(candidateOverrides, errors);
        validateBossbarShape(candidateBossbar, errors);
        validateEventsShape(candidateEvents, errors);
        validateHoverShape(candidateHover, errors);
        validateStateShape(candidateState, errors);

        LegendarySettings parsedSettings = parseSettings(candidateConfig, candidateWorlds, candidatePokemon, candidateBlacklist, candidateBiomes, candidateProtection, candidateWeights, candidateCooldowns, candidateLimits, candidateOverrides, candidateBossbar, candidateEvents, errors);
        CommandSettings parsedCommands = parseCommands(candidateCommands, errors);

        if (errors.isEmpty()) {
            try {
                validateRuntimeSettings(server, parsedSettings, errors, warnings);
            } catch (RuntimeException exception) {
                errors.add("Runtime validation failed unexpectedly: " + describe(exception) + ". Check the server console for the stack trace.");
                LegendarySpawn.logRuntimeError("[Reload] Runtime validation failed unexpectedly: " + describe(exception), exception);
            }
        }
        if (!errors.isEmpty()) {
            return ReloadResult.failed(errors, warnings);
        }

        warnings.addAll(commandRestartWarnings(commandSettings, parsedCommands));
        config = candidateConfig;
        commands = candidateCommands;
        messages = candidateMessages;
        worlds = candidateWorlds;
        pokemon = candidatePokemon;
        blacklist = candidateBlacklist;
        biomes = candidateBiomes;
        protection = candidateProtection;
        weights = candidateWeights;
        cooldowns = candidateCooldowns;
        limits = candidateLimits;
        overrides = candidateOverrides;
        bossbar = candidateBossbar;
        events = candidateEvents;
        hover = candidateHover;
        state = candidateState;
        settings = parsedSettings;
        commandSettings = parsedCommands;
        loadErrors = List.of();
        validationErrors = List.of();
        validationWarnings = List.copyOf(warnings);
        healthy = true;
        return ReloadResult.ok(warnings);
    }

    public LegendarySettings settings() {
        return settings;
    }

    public CommandSettings commandSettings() {
        return commandSettings;
    }

    public Config state() {
        return state;
    }

    public boolean isHealthy() {
        return healthy;
    }

    public List<String> validationErrors() {
        return validationErrors;
    }

    public List<String> validationWarnings() {
        return validationWarnings;
    }

    public String message(String path, String fallback) {
        return messages.getString(path, fallback);
    }

    public boolean messageBoolean(String path, boolean fallback) {
        return messages.getBoolean(path, fallback);
    }

    public boolean hoverEnabled() {
        return hover.getBoolean("enabled", true);
    }

    public boolean hoverEnabledFor(String context) {
        if (!hoverEnabled()) return false;
        if (context == null || context.isBlank()) return true;
        return hover.getBoolean("messages." + context, true);
    }

    public boolean hoverHintEnabled() {
        return hover.getBoolean("hint.enabled", true);
    }

    public String hoverHintText() {
        String value = hover.getString("hint.text", " &8[<#FFD54A>Hover&8]");
        return value == null ? "" : value;
    }

    public List<String> hoverLines() {
        List<String> lines = hover.getStringList("lines");
        return lines == null ? List.of() : List.copyOf(lines);
    }

    public String hoverValue(String path, String fallback) {
        String value = hover.getString("values." + path, fallback);
        return value == null ? fallback : value;
    }

    public long globalCooldownUntil() {
        return Math.max(0L, state.getLong("cooldowns.global-until-epoch-millis", 0L));
    }

    public Map<String, Long> speciesCooldownState() {
        Map<String, Long> result = new java.util.LinkedHashMap<>();
        for (String entry : state.getStringList("cooldowns.species-until")) {
            int separator = entry.lastIndexOf('|');
            if (separator <= 0 || separator >= entry.length() - 1) continue;
            String id = entry.substring(0, separator).trim().toLowerCase(Locale.ROOT);
            try {
                long until = Long.parseLong(entry.substring(separator + 1).trim());
                if (until > 0L) result.put(id, until);
            } catch (NumberFormatException ignored) {
            }
        }
        return result;
    }

    public synchronized void saveCooldownState(long globalUntil, Map<String, Long> speciesUntil) {
        state.set("cooldowns.global-until-epoch-millis", Math.max(0L, globalUntil));
        List<String> serialized = new ArrayList<>();
        for (Map.Entry<String, Long> entry : speciesUntil.entrySet()) {
            if (entry.getValue() > 0L) serialized.add(entry.getKey() + "|" + entry.getValue());
        }
        state.set("cooldowns.species-until", serialized);
        saveState(
                state.getLong("timer.next-attempt-epoch-millis", 0L),
                state.getBoolean("timer.paused", false),
                state.getLong("timer.paused-remaining-millis", 0L)
        );
    }

    public synchronized void saveState(long nextAttemptEpochMillis, boolean paused, long pausedRemainingMillis) {
        state.set("timer.next-attempt-epoch-millis", Math.max(0L, nextAttemptEpochMillis));
        state.set("timer.paused", paused);
        state.set("timer.paused-remaining-millis", Math.max(0L, pausedRemainingMillis));

        File target = state.getFile();
        File parent = target.getParentFile();
        File temporary = new File(parent, target.getName() + ".tmp");

        try {
            Files.deleteIfExists(temporary.toPath());
            Config temporaryState = new Config(temporary, STATE_RESOURCE);
            temporaryState.set("timer.next-attempt-epoch-millis", Math.max(0L, nextAttemptEpochMillis));
            temporaryState.set("timer.paused", paused);
            temporaryState.set("timer.paused-remaining-millis", Math.max(0L, pausedRemainingMillis));
            temporaryState.set("cooldowns.global-until-epoch-millis", Math.max(0L, state.getLong("cooldowns.global-until-epoch-millis", 0L)));
            temporaryState.set("cooldowns.species-until", state.getStringList("cooldowns.species-until"));
            temporaryState.save();

            try {
                Files.move(
                        temporary.toPath(),
                        target.toPath(),
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING
                );
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException | RuntimeException exception) {
            LegendarySpawn.logRuntimeError(
                    "[State] Atomic timer save failed; falling back to GKCore Config.save(): " + describe(exception),
                    exception
            );
            state.save();
        } finally {
            try {
                Files.deleteIfExists(temporary.toPath());
            } catch (IOException ignored) {
            }
        }
    }

    private LegendarySettings parseSettings(
            Config source,
            Config worldsSource,
            Config pokemonSource,
            Config blacklistSource,
            Config biomesSource,
            Config protectionSource,
            Config weightsSource,
            Config cooldownsSource,
            Config limitsSource,
            Config overridesSource,
            Config bossbarSource,
            Config eventsSource,
            List<String> errors
    ) {
        boolean enabled = source.getBoolean("enabled", true);
        int minimumOnlinePlayers = source.getInt("minimum-online-players", 1);
        boolean pauseTimer = source.getBoolean("pause-timer-when-below-minimum-players", true);
        double spawnChance = source.getDouble("spawn-chance", 0.15D);
        boolean debug = source.getBoolean("debug", false);

        int minMinutes = source.getInt("interval.min-minutes", 60);
        int maxMinutes = source.getInt("interval.max-minutes", 90);

        int minDistance = source.getInt("spawn.min-distance", 8);
        int maxDistance = source.getInt("spawn.max-distance", 24);
        int attempts = source.getInt("spawn.attempts", 48);
        int minLevel = source.getInt("spawn.min-level", 55);
        int maxLevel = source.getInt("spawn.max-level", 75);
        boolean allowWater = source.getBoolean("spawn.allow-water", false);
        boolean countTowardsSpawnCap = source.getBoolean("spawn.count-towards-spawn-cap", false);
        int shinyOneInChance = source.getInt("spawn.shiny-one-in-chance", 8192);

        boolean allowAllWorlds = worldsSource.getBoolean("allow-all-worlds", false);
        Set<String> allowedWorlds = normalizeResourceLocationSet(
                worldsSource.getStringList("allowed-worlds"),
                "worlds.yml",
                "allowed-worlds",
                errors
        );
        Set<String> blockedWorlds = normalizeResourceLocationSet(
                worldsSource.getStringList("blocked-worlds"),
                "worlds.yml",
                "blocked-worlds",
                errors
        );

        List<String> requiredLabels = normalizeList(pokemonSource.getStringList("required-labels"));
        Set<String> specificPokemon = normalizeResourceLocationSet(
                pokemonSource.getStringList("specific-pokemon"),
                "pokemon.yml",
                "specific-pokemon",
                errors
        );
        List<String> blockedLabels = normalizeList(blacklistSource.getStringList("labels"));
        Set<String> blockedPokemon = normalizeResourceLocationSet(
                blacklistSource.getStringList("pokemon"),
                "blacklist.yml",
                "pokemon",
                errors
        );

        boolean biomeEnabled = biomesSource.getBoolean("enabled", false);
        boolean useCobblemonSpawnData = biomesSource.getBoolean("use-cobblemon-spawn-data", true);
        String rawMissingBiomeData = biomesSource.getString("when-no-spawn-data", "allow-any")
                .trim()
                .toLowerCase(Locale.ROOT);
        LegendarySettings.MissingBiomeDataBehavior missingBiomeDataBehavior;
        if (rawMissingBiomeData.equals("allow-any")) {
            missingBiomeDataBehavior = LegendarySettings.MissingBiomeDataBehavior.ALLOW_ANY;
        } else if (rawMissingBiomeData.equals("reject")) {
            missingBiomeDataBehavior = LegendarySettings.MissingBiomeDataBehavior.REJECT;
        } else {
            errors.add("biomes.yml -> when-no-spawn-data must be 'allow-any' or 'reject', but found '" + rawMissingBiomeData + "'.");
            missingBiomeDataBehavior = LegendarySettings.MissingBiomeDataBehavior.ALLOW_ANY;
        }

        boolean protectionEnabled = protectionSource.getBoolean("enabled", true);
        int protectionDurationMinutes = protectionSource.getInt("duration-minutes", 15);
        boolean preventNaturalDespawn = protectionSource.getBoolean("prevent-natural-despawn", true);
        boolean restoreIfRemoved = protectionSource.getBoolean("restore-if-removed", true);

        if (protectionDurationMinutes < 1 || protectionDurationMinutes > 1440) {
            errors.add("protection.yml -> duration-minutes must be between 1 and 1440, but found " + protectionDurationMinutes + ".");
        }

        boolean announcementEnabled = source.getBoolean("announcement.enabled", true);
        String announcementMessage = source.getString(
                "announcement.message",
                "&6A wild legendary &e{pokemon} &6has spawned near &e{player}&6!"
        );
        boolean showCoordinates = source.getBoolean("announcement.show-coordinates", false);

        if (minimumOnlinePlayers < 1) {
            errors.add("config.yml -> minimum-online-players must be at least 1, but found " + minimumOnlinePlayers + ".");
        }
        if (!Double.isFinite(spawnChance) || spawnChance < 0.0D || spawnChance > 1.0D) {
            errors.add("config.yml -> spawn-chance must be between 0.0 and 1.0, but found " + spawnChance + ".");
        }
        if (minMinutes < 1 || maxMinutes < 1 || minMinutes > maxMinutes) {
            errors.add("config.yml -> interval.min-minutes/max-minutes are invalid. min=" + minMinutes + ", max=" + maxMinutes + ".");
        }
        if (minDistance < 1 || maxDistance < minDistance) {
            errors.add("config.yml -> spawn.min-distance/max-distance are invalid. min=" + minDistance + ", max=" + maxDistance + ".");
        }
        if (attempts < 1 || attempts > 512) {
            errors.add("config.yml -> spawn.attempts must be between 1 and 512, but found " + attempts + ".");
        }
        if (minLevel < 1 || maxLevel > 100 || minLevel > maxLevel) {
            errors.add("config.yml -> spawn.min-level/max-level must stay between 1 and 100. min=" + minLevel + ", max=" + maxLevel + ".");
        }
        if (shinyOneInChance < 0) {
            errors.add("config.yml -> spawn.shiny-one-in-chance cannot be negative. Use 0 to disable it.");
        }
        if (!allowAllWorlds && allowedWorlds.isEmpty()) {
            errors.add("worlds.yml -> allowed-worlds cannot be empty while allow-all-worlds is false.");
        }

        if (!allowAllWorlds) {
            Set<String> bothWorlds = new LinkedHashSet<>(allowedWorlds);
            bothWorlds.retainAll(blockedWorlds);
            for (String id : bothWorlds) {
                errors.add("worlds.yml -> world '" + id + "' is present in both allowed-worlds and blocked-worlds. blocked-worlds always wins, so remove it from one list.");
            }
        }

        Set<String> bothLabels = new LinkedHashSet<>(requiredLabels);
        bothLabels.retainAll(blockedLabels);
        for (String label : bothLabels) {
            errors.add("blacklist.yml -> label '" + label + "' is required by pokemon.yml but also blacklisted. Remove it from one file.");
        }

        Set<String> bothSpecies = new LinkedHashSet<>(specificPokemon);
        bothSpecies.retainAll(blockedPokemon);
        for (String id : bothSpecies) {
            errors.add("blacklist.yml -> Pokemon '" + id + "' is present in pokemon.yml specific-pokemon and blacklist.yml pokemon.");
        }

        validateAnnouncementPlaceholders(announcementMessage, errors);

        boolean weightsEnabled = weightsSource.getBoolean("enabled", true);
        double defaultWeight = weightsSource.getDouble("default-weight", 1.0D);
        if (!Double.isFinite(defaultWeight) || defaultWeight <= 0.0D || defaultWeight > 1_000_000.0D) {
            errors.add("weights.yml -> default-weight must be greater than 0 and at most 1000000, but found " + defaultWeight + ".");
            defaultWeight = 1.0D;
        }
        Map<String, Double> labelWeights = new java.util.LinkedHashMap<>();
        ConfigSection labelWeightSection = weightsSource.getSection("labels");
        if (labelWeightSection != null) {
            for (String key : labelWeightSection.getKeys()) {
                String label = key.trim().toLowerCase(Locale.ROOT);
                double weight = labelWeightSection.getDouble(key, -1.0D);
                if (label.isBlank()) {
                    errors.add("weights.yml -> labels contains an empty label name.");
                    continue;
                }
                if (!Double.isFinite(weight) || weight <= 0.0D || weight > 1_000_000.0D) {
                    errors.add("weights.yml -> labels." + key + " must be greater than 0 and at most 1000000.");
                    continue;
                }
                labelWeights.put(label, weight);
            }
        }

        boolean speciesCooldownEnabled = cooldownsSource.getBoolean("species.enabled", true);
        int defaultSpeciesMinutes = cooldownsSource.getInt("species.default-minutes", 180);
        if (defaultSpeciesMinutes < 0 || defaultSpeciesMinutes > 10080) {
            errors.add("cooldowns.yml -> species.default-minutes must be between 0 and 10080.");
        }
        Map<String, Integer> speciesCooldownOverrides = new java.util.LinkedHashMap<>();
        ConfigSection cooldownOverrideSection = cooldownsSource.getSection("species.overrides");
        if (cooldownOverrideSection != null) {
            for (String key : cooldownOverrideSection.getKeys()) {
                Object rawValue = cooldownOverrideSection.asMap().get(key);
                if (!(rawValue instanceof Number number) || number.doubleValue() != Math.rint(number.doubleValue())) {
                    errors.add("cooldowns.yml -> species.overrides." + key + " must be a whole number.");
                    continue;
                }
                String id = normalizeResourceLocation(key, "cooldowns.yml", "species.overrides." + key, errors);
                int minutes = ((Number) rawValue).intValue();
                if (minutes < 0 || minutes > 10080) {
                    errors.add("cooldowns.yml -> species.overrides." + key + " must be between 0 and 10080 minutes.");
                    continue;
                }
                if (id != null) speciesCooldownOverrides.put(id, minutes);
            }
        }
        boolean globalCooldownEnabled = cooldownsSource.getBoolean("global-after-event.enabled", true);
        int capturedCooldownMinutes = cooldownsSource.getInt("global-after-event.captured-minutes", 30);
        int defeatedCooldownMinutes = cooldownsSource.getInt("global-after-event.defeated-minutes", 30);
        if (capturedCooldownMinutes < 0 || capturedCooldownMinutes > 10080) {
            errors.add("cooldowns.yml -> global-after-event.captured-minutes must be between 0 and 10080.");
        }
        if (defeatedCooldownMinutes < 0 || defeatedCooldownMinutes > 10080) {
            errors.add("cooldowns.yml -> global-after-event.defeated-minutes must be between 0 and 10080.");
        }

        boolean activeLimitEnabled = limitsSource.getBoolean("active-limit.enabled", true);
        int maxActive = limitsSource.getInt("active-limit.max-active", 1);
        boolean applyToForce = limitsSource.getBoolean("active-limit.apply-to-force", false);
        if (maxActive < 1 || maxActive > 100) {
            errors.add("limits.yml -> active-limit.max-active must be between 1 and 100.");
        }

        boolean bossbarEnabled = bossbarSource.getBoolean("enabled", true);
        String bossbarTitle = bossbarSource.getString("title", "&6{pokemon} &7- &f{time}");
        String bossbarColor = bossbarSource.getString("color", "yellow").trim().toLowerCase(Locale.ROOT);
        String bossbarOverlay = bossbarSource.getString("overlay", "progress").trim().toLowerCase(Locale.ROOT);
        boolean bossbarAll = bossbarSource.getBoolean("show-to-all-players", true);
        if (!Set.of("pink", "blue", "red", "green", "yellow", "purple", "white").contains(bossbarColor)) {
            errors.add("bossbar.yml -> color is invalid: '" + bossbarColor + "'.");
        }
        if (!Set.of("progress", "notched_6", "notched_10", "notched_12", "notched_20").contains(bossbarOverlay)) {
            errors.add("bossbar.yml -> overlay is invalid: '" + bossbarOverlay + "'.");
        }
        validateBossbarPlaceholders(bossbarTitle, errors);

        LegendarySettings.Events eventSettings = new LegendarySettings.Events(
                eventsSource.getBoolean("captured", true),
                eventsSource.getBoolean("defeated", true),
                eventsSource.getBoolean("killed", true),
                eventsSource.getBoolean("protection-expired", true),
                eventsSource.getBoolean("despawned", true)
        );

        Map<String, LegendarySettings.PokemonOverride> pokemonOverrides = parsePokemonOverrides(overridesSource, errors);

        return new LegendarySettings(
                enabled,
                Math.max(1, minimumOnlinePlayers),
                pauseTimer,
                clamp(spawnChance, 0.0D, 1.0D),
                debug,
                new LegendarySettings.Interval(Math.max(1, minMinutes), Math.max(Math.max(1, minMinutes), maxMinutes)),
                new LegendarySettings.Spawn(
                        Math.max(1, minDistance),
                        Math.max(Math.max(1, minDistance), maxDistance),
                        Math.max(1, Math.min(512, attempts)),
                        Math.max(1, Math.min(100, minLevel)),
                        Math.max(Math.max(1, Math.min(100, minLevel)), Math.min(100, maxLevel)),
                        allowWater,
                        countTowardsSpawnCap,
                        Math.max(0, shinyOneInChance)
                ),
                new LegendarySettings.Worlds(
                        allowAllWorlds,
                        Set.copyOf(allowedWorlds),
                        Set.copyOf(blockedWorlds)
                ),
                new LegendarySettings.PokemonPool(
                        List.copyOf(requiredLabels),
                        Set.copyOf(specificPokemon)
                ),
                new LegendarySettings.Blacklist(
                        List.copyOf(blockedLabels),
                        Set.copyOf(blockedPokemon)
                ),
                new LegendarySettings.Biomes(
                        biomeEnabled,
                        useCobblemonSpawnData,
                        missingBiomeDataBehavior
                ),
                new LegendarySettings.Protection(
                        protectionEnabled,
                        Math.max(1, Math.min(1440, protectionDurationMinutes)),
                        preventNaturalDespawn,
                        restoreIfRemoved
                ),
                new LegendarySettings.Announcement(announcementEnabled, announcementMessage, showCoordinates),
                new LegendarySettings.Weights(weightsEnabled, defaultWeight, Map.copyOf(labelWeights)),
                new LegendarySettings.Cooldowns(
                        speciesCooldownEnabled,
                        Math.max(0, Math.min(10080, defaultSpeciesMinutes)),
                        Map.copyOf(speciesCooldownOverrides),
                        globalCooldownEnabled,
                        Math.max(0, Math.min(10080, capturedCooldownMinutes)),
                        Math.max(0, Math.min(10080, defeatedCooldownMinutes))
                ),
                new LegendarySettings.Limits(activeLimitEnabled, Math.max(1, Math.min(100, maxActive)), applyToForce),
                new LegendarySettings.Bossbar(bossbarEnabled, bossbarTitle, bossbarColor, bossbarOverlay, bossbarAll),
                eventSettings,
                Map.copyOf(pokemonOverrides)
        );
    }


    private static Map<String, LegendarySettings.PokemonOverride> parsePokemonOverrides(Config source, List<String> errors) {
        Map<String, LegendarySettings.PokemonOverride> result = new java.util.LinkedHashMap<>();
        ConfigSection root = source.getSection("pokemon");
        if (root == null) {
            return result;
        }
        for (String rawId : root.getKeys()) {
            String id = normalizeResourceLocation(rawId, "pokemon-overrides.yml", "pokemon." + rawId, errors);
            Object rawPokemonSection = root.asMap().get(rawId);
            if (!(rawPokemonSection instanceof Map<?, ?> rawMap)) {
                errors.add("pokemon-overrides.yml -> pokemon." + rawId + " must be a YAML section.");
                continue;
            }
            @SuppressWarnings("unchecked")
            ConfigSection pokemonSection = new ConfigSection((Map<String, Object>) rawMap, source);
            Set<String> allowedKeys = Set.of("worlds", "biomes");
            for (String key : pokemonSection.getKeys()) {
                if (!allowedKeys.contains(key)) errors.add("pokemon-overrides.yml -> unknown key 'pokemon." + rawId + "." + key + "'.");
            }
            ConfigSection worldSection = pokemonSection.getSection("worlds");
            ConfigSection biomeSection = pokemonSection.getSection("biomes");
            LegendarySettings.OverrideMode worldMode = parseOverrideMode(worldSection == null ? "inherit" : worldSection.getString("mode", "inherit"), "pokemon." + rawId + ".worlds.mode", errors);
            LegendarySettings.OverrideMode biomeMode = parseOverrideMode(biomeSection == null ? "inherit" : biomeSection.getString("mode", "inherit"), "pokemon." + rawId + ".biomes.mode", errors);
            Set<String> allowedWorlds = normalizeResourceLocationSet(worldSection == null ? List.of() : worldSection.getStringList("allowed"), "pokemon-overrides.yml", "pokemon." + rawId + ".worlds.allowed", errors);
            Set<String> blockedWorlds = normalizeResourceLocationSet(worldSection == null ? List.of() : worldSection.getStringList("blocked"), "pokemon-overrides.yml", "pokemon." + rawId + ".worlds.blocked", errors);
            Set<String> allowedBiomes = normalizeResourceLocationSet(biomeSection == null ? List.of() : biomeSection.getStringList("allowed"), "pokemon-overrides.yml", "pokemon." + rawId + ".biomes.allowed", errors);
            Set<String> blockedBiomes = normalizeResourceLocationSet(biomeSection == null ? List.of() : biomeSection.getStringList("blocked"), "pokemon-overrides.yml", "pokemon." + rawId + ".biomes.blocked", errors);
            if (worldSection != null) {
                for (String key : worldSection.getKeys()) if (!Set.of("mode", "allowed", "blocked").contains(key)) errors.add("pokemon-overrides.yml -> unknown key 'pokemon." + rawId + ".worlds." + key + "'.");
                validateDynamicString(worldSection, "mode", "pokemon." + rawId + ".worlds.mode", errors);
                validateDynamicStringList(worldSection, "allowed", "pokemon." + rawId + ".worlds.allowed", errors);
                validateDynamicStringList(worldSection, "blocked", "pokemon." + rawId + ".worlds.blocked", errors);
            }
            if (biomeSection != null) {
                for (String key : biomeSection.getKeys()) if (!Set.of("mode", "allowed", "blocked").contains(key)) errors.add("pokemon-overrides.yml -> unknown key 'pokemon." + rawId + ".biomes." + key + "'.");
                validateDynamicString(biomeSection, "mode", "pokemon." + rawId + ".biomes.mode", errors);
                validateDynamicStringList(biomeSection, "allowed", "pokemon." + rawId + ".biomes.allowed", errors);
                validateDynamicStringList(biomeSection, "blocked", "pokemon." + rawId + ".biomes.blocked", errors);
            }
            Set<String> worldConflicts = new LinkedHashSet<>(allowedWorlds);
            worldConflicts.retainAll(blockedWorlds);
            for (String conflict : worldConflicts) errors.add("pokemon-overrides.yml -> pokemon." + rawId + " world '" + conflict + "' is both allowed and blocked.");
            Set<String> biomeConflicts = new LinkedHashSet<>(allowedBiomes);
            biomeConflicts.retainAll(blockedBiomes);
            for (String conflict : biomeConflicts) errors.add("pokemon-overrides.yml -> pokemon." + rawId + " biome '" + conflict + "' is both allowed and blocked.");
            if (worldMode == LegendarySettings.OverrideMode.REPLACE && allowedWorlds.isEmpty()) {
                errors.add("pokemon-overrides.yml -> pokemon." + rawId + ".worlds.allowed cannot be empty when mode is replace.");
            }
            if (biomeMode == LegendarySettings.OverrideMode.REPLACE && allowedBiomes.isEmpty()) {
                errors.add("pokemon-overrides.yml -> pokemon." + rawId + ".biomes.allowed cannot be empty when mode is replace.");
            }
            if (id != null) result.put(id, new LegendarySettings.PokemonOverride(worldMode, Set.copyOf(allowedWorlds), Set.copyOf(blockedWorlds), biomeMode, Set.copyOf(allowedBiomes), Set.copyOf(blockedBiomes)));
        }
        return result;
    }

    private static void validateDynamicString(ConfigSection section, String key, String path, List<String> errors) {
        if (!section.contains(key)) return;
        Object value = section.asMap().get(key);
        if (!(value instanceof String)) errors.add("pokemon-overrides.yml -> " + path + " must be text.");
    }

    private static void validateDynamicStringList(ConfigSection section, String key, String path, List<String> errors) {
        if (!section.contains(key)) return;
        Object value = section.asMap().get(key);
        if (!(value instanceof List<?> list)) {
            errors.add("pokemon-overrides.yml -> " + path + " must be a YAML list.");
            return;
        }
        for (int i = 0; i < list.size(); i++) {
            if (!(list.get(i) instanceof String string) || string.isBlank()) errors.add("pokemon-overrides.yml -> " + path + "[" + i + "] must be non-empty text.");
        }
    }

    private static LegendarySettings.OverrideMode parseOverrideMode(String raw, String path, List<String> errors) {
        String value = raw == null ? "inherit" : raw.trim().toLowerCase(Locale.ROOT);
        if (value.equals("inherit")) return LegendarySettings.OverrideMode.INHERIT;
        if (value.equals("replace")) return LegendarySettings.OverrideMode.REPLACE;
        errors.add("pokemon-overrides.yml -> " + path + " must be 'inherit' or 'replace', but found '" + value + "'.");
        return LegendarySettings.OverrideMode.INHERIT;
    }

    private static String normalizeResourceLocation(String raw, String file, String path, List<String> errors) {
        if (raw == null || raw.isBlank() || !raw.contains(":")) {
            errors.add(file + " -> " + path + " must use namespace:path, but found '" + raw + "'.");
            return null;
        }
        ResourceLocation id = ResourceLocation.tryParse(raw.trim().toLowerCase(Locale.ROOT));
        if (id == null) {
            errors.add(file + " -> " + path + " contains invalid resource ID '" + raw + "'.");
            return null;
        }
        return id.toString();
    }

    private static void validateBossbarPlaceholders(String message, List<String> errors) {
        Set<String> allowed = Set.of("pokemon", "level", "time", "player");
        Matcher matcher = PLACEHOLDER.matcher(message == null ? "" : message);
        while (matcher.find()) {
            String placeholder = matcher.group(1).toLowerCase(Locale.ROOT);
            if (!allowed.contains(placeholder)) errors.add("bossbar.yml -> title contains unknown placeholder '{" + matcher.group(1) + "}'. Allowed: " + allowed + ".");
        }
    }

    private CommandSettings parseCommands(Config source, List<String> errors) {
        String root = normalizeLiteral(source.getString("command.name", "legendaryspawn"), "legendaryspawn", "commands.yml -> command.name", errors);
        List<String> aliases = new ArrayList<>();
        for (String alias : source.getStringList("command.aliases")) {
            String normalized = normalizeLiteral(alias, "", "commands.yml -> command.aliases", errors);
            if (!normalized.isBlank() && !normalized.equals(root) && !aliases.contains(normalized)) {
                aliases.add(normalized);
            }
        }

        int level = validPermissionLevel(source.getInt("command.permission-level", 2), "commands.yml -> command.permission-level", errors);
        String permission = source.getString("command.permission", "").trim();
        String noPermission = source.getString("command.no-permission-message", "&cYou do not have permission to use this command.");

        CommandSettings.Subcommand reload = subcommand(source, "reload", errors);
        CommandSettings.Subcommand status = subcommand(source, "status", errors);
        CommandSettings.Subcommand force = subcommand(source, "force", errors);
        CommandSettings.Subcommand roll = subcommand(source, "roll", errors);
        CommandSettings.Subcommand reschedule = subcommand(source, "reschedule", errors);
        validateUniqueSubcommandNames(List.of(reload, status, force, roll, reschedule), errors);

        return new CommandSettings(
                root,
                List.copyOf(aliases),
                level,
                permission,
                noPermission,
                reload,
                status,
                force,
                roll,
                reschedule
        );
    }

    private CommandSettings.Subcommand subcommand(Config source, String key, List<String> errors) {
        String base = "subcommands." + key;
        String name = normalizeLiteral(source.getString(base + ".name", key), key, "commands.yml -> " + base + ".name", errors);
        int level = validPermissionLevel(source.getInt(base + ".permission-level", 2), "commands.yml -> " + base + ".permission-level", errors);
        String permission = source.getString(base + ".permission", "").trim();
        return new CommandSettings.Subcommand(name, level, permission);
    }

    private static void validateRuntimeSettings(
            MinecraftServer server,
            LegendarySettings candidate,
            List<String> errors,
            List<String> warnings
    ) {
        if (server == null) {
            errors.add("Runtime validation could not run because the Minecraft server instance is unavailable.");
            return;
        }

        if (candidate.protection().enabled()
                && !candidate.protection().preventNaturalDespawn()
                && !candidate.protection().restoreIfRemoved()) {
            warnings.add("protection.yml -> enabled is true, but both prevent-natural-despawn and restore-if-removed are false, so the protection currently does nothing.");
        }

        validateWorldIds(server, candidate.worlds().allowedWorlds(), "worlds.yml", "allowed-worlds", errors);
        validateWorldIds(server, candidate.worlds().blockedWorlds(), "worlds.yml", "blocked-worlds", errors);
        List<Species> implemented = PokemonSpecies.getImplemented();
        if (implemented.isEmpty()) {
            errors.add("Cobblemon currently reports zero implemented Pokemon species. The legendary pool cannot be validated.");
            return;
        }

        Set<String> implementedIds = new HashSet<>();
        Set<String> availableLabels = new HashSet<>();
        for (Species species : implemented) {
            implementedIds.add(species.getResourceIdentifier().toString().toLowerCase(Locale.ROOT));
            if (species.getLabels() != null) {
                for (String label : species.getLabels()) {
                    if (label != null && !label.isBlank()) {
                        availableLabels.add(label.toLowerCase(Locale.ROOT));
                    }
                }
            }
        }

        for (String label : candidate.pokemonPool().requiredLabels()) {
            if (!availableLabels.contains(label)) {
                errors.add("pokemon.yml -> required-labels contains unknown label '" + label + "'. No implemented Pokemon currently has this label.");
            }
        }
        for (String label : candidate.blacklist().labels()) {
            if (!availableLabels.contains(label)) {
                errors.add("blacklist.yml -> labels contains unknown label '" + label + "'. No implemented Pokemon currently has this label.");
            }
        }
        for (String label : candidate.weights().labelWeights().keySet()) {
            if (!availableLabels.contains(label)) {
                errors.add("weights.yml -> labels contains unknown label '" + label + "'. No implemented Pokemon currently has this label.");
            }
        }

        validateSpeciesIds(candidate.pokemonPool().specificPokemon(), "pokemon.yml", "specific-pokemon", implementedIds, errors);
        validateSpeciesIds(candidate.blacklist().pokemon(), "blacklist.yml", "pokemon", implementedIds, errors);
        validateSpeciesIds(candidate.cooldowns().speciesOverrides().keySet(), "cooldowns.yml", "species.overrides", implementedIds, errors);
        validateSpeciesIds(candidate.pokemonOverrides().keySet(), "pokemon-overrides.yml", "pokemon", implementedIds, errors);
        for (Map.Entry<String, LegendarySettings.PokemonOverride> entry : candidate.pokemonOverrides().entrySet()) {
            validateWorldIds(server, entry.getValue().allowedWorlds(), "pokemon-overrides.yml", "pokemon." + entry.getKey() + ".worlds.allowed", errors);
            validateWorldIds(server, entry.getValue().blockedWorlds(), "pokemon-overrides.yml", "pokemon." + entry.getKey() + ".worlds.blocked", errors);
            validateBiomeIds(server, entry.getValue().allowedBiomes(), "pokemon-overrides.yml", "pokemon." + entry.getKey() + ".biomes.allowed", errors);
            validateBiomeIds(server, entry.getValue().blockedBiomes(), "pokemon-overrides.yml", "pokemon." + entry.getKey() + ".biomes.blocked", errors);
        }

        for (String id : candidate.pokemonPool().specificPokemon()) {
            ResourceLocation identifier = ResourceLocation.tryParse(id);
            Species species = identifier == null ? null : PokemonSpecies.getByIdentifier(identifier);
            if (species == null || !implementedIds.contains(id)) {
                continue;
            }
            Set<String> labels = normalizedLabels(species);
            if (!matchesRequiredLabel(labels, candidate.pokemonPool().requiredLabels())) {
                errors.add("pokemon.yml -> specific Pokemon '" + id + "' does not match required-labels " + candidate.pokemonPool().requiredLabels() + ".");
            }
            if (matchesAnyLabel(labels, candidate.blacklist().labels())) {
                errors.add("pokemon.yml -> specific Pokemon '" + id + "' is removed by blacklist.yml labels " + candidate.blacklist().labels() + ".");
            }
            if (candidate.blacklist().pokemon().contains(id)) {
                errors.add("pokemon.yml -> specific Pokemon '" + id + "' is also present in blacklist.yml.");
            }
        }

        BiomeSpawnIndex biomeIndex = null;
        if (candidate.biomes().enabled()) {
            if (!candidate.biomes().useCobblemonSpawnData()) {
                warnings.add("biomes.yml -> enabled is true but use-cobblemon-spawn-data is false, so biome filtering currently has no active data source.");
            } else {
                try {
                    biomeIndex = BiomeSpawnIndex.build();
                } catch (RuntimeException exception) {
                    errors.add("biomes.yml -> Cobblemon world spawn biome data could not be read: " + describe(exception) + ".");
                    LegendarySpawn.logRuntimeError("[Biomes] Failed to build Cobblemon biome spawn index during validation: " + describe(exception), exception);
                }
            }
        }

        int basePool = 0;
        int validPool = 0;
        int missingBiomeData = 0;
        int emptyBiomeData = 0;
        for (Species species : implemented) {
            String id = species.getResourceIdentifier().toString().toLowerCase(Locale.ROOT);
            Set<String> labels = normalizedLabels(species);
            if (!candidate.pokemonPool().specificPokemon().isEmpty() && !candidate.pokemonPool().specificPokemon().contains(id)) {
                continue;
            }
            if (candidate.blacklist().pokemon().contains(id)) {
                continue;
            }
            if (!matchesRequiredLabel(labels, candidate.pokemonPool().requiredLabels())) {
                continue;
            }
            if (matchesAnyLabel(labels, candidate.blacklist().labels())) {
                continue;
            }

            basePool++;
            LegendarySettings.PokemonOverride override = candidate.pokemonOverrides().getOrDefault(id, LegendarySettings.PokemonOverride.empty());
            if (biomeIndex != null && override.biomeMode() == LegendarySettings.OverrideMode.INHERIT) {
                if (!biomeIndex.hasSpawnData(species)) {
                    missingBiomeData++;
                } else if (!biomeIndex.hasAnyValidBiome(species)) {
                    emptyBiomeData++;
                }
                if (!biomeIndex.isSpeciesEligible(species, candidate.biomes())) {
                    continue;
                }
            }
            validPool++;
        }

        if (basePool == 0) {
            errors.add("The final Pokemon pool is empty after applying pokemon.yml and blacklist.yml.");
            return;
        }

        if (biomeIndex != null) {
            if (missingBiomeData > 0) {
                String action = candidate.biomes().whenNoSpawnData() == LegendarySettings.MissingBiomeDataBehavior.ALLOW_ANY
                        ? "will be allowed in any biome"
                        : "will be removed from the biome-aware pool";
                warnings.add("biomes.yml -> " + missingBiomeData + " valid Pokemon have no Cobblemon world spawn biome data and " + action + ".");
            }
            if (emptyBiomeData > 0) {
                warnings.add("biomes.yml -> " + emptyBiomeData + " valid Pokemon have spawn entries but those entries resolve to zero valid biomes, so they are excluded while biome filtering is enabled.");
            }
        }

        if (validPool == 0) {
            errors.add("The final Pokemon pool is empty after applying pokemon.yml, blacklist.yml and biomes.yml.");
        } else if (validPool == 1) {
            warnings.add("The current Pokemon/blacklist/biome configuration leaves only 1 valid Pokemon in the automatic spawn pool.");
        }
    }

    private static void validateWorldIds(
            MinecraftServer server,
            Set<String> ids,
            String file,
            String path,
            List<String> errors
    ) {
        for (String rawDimension : ids) {
            ResourceLocation id = ResourceLocation.tryParse(rawDimension);
            if (id == null) {
                continue;
            }
            ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, id);
            if (server.getLevel(key) == null) {
                errors.add(file + " -> " + path + " contains unknown or unloaded world '" + rawDimension + "'.");
            }
        }
    }

    private static void validateBiomeIds(MinecraftServer server, Set<String> ids, String file, String path, List<String> errors) {
        var registry = server.registryAccess().registryOrThrow(Registries.BIOME);
        for (String raw : ids) {
            ResourceLocation id = ResourceLocation.tryParse(raw);
            if (id != null && !registry.containsKey(id)) {
                errors.add(file + " -> " + path + " contains unknown biome '" + raw + "'.");
            }
        }
    }

    private static void validateSpeciesIds(
            Set<String> ids,
            String file,
            String path,
            Set<String> implementedIds,
            List<String> errors
    ) {
        for (String id : ids) {
            ResourceLocation identifier = ResourceLocation.tryParse(id);
            Species species = identifier == null ? null : PokemonSpecies.getByIdentifier(identifier);
            if (species == null) {
                errors.add(file + " -> " + path + " contains unknown Pokemon '" + id + "'. The species is not registered by Cobblemon/datapacks.");
                continue;
            }
            if (!implementedIds.contains(id)) {
                errors.add(file + " -> " + path + " contains Pokemon '" + id + "', but that species is not implemented and cannot spawn.");
            }
        }
    }

    private static Set<String> normalizedLabels(Species species) {
        Set<String> labels = new HashSet<>();
        if (species.getLabels() == null) {
            return labels;
        }
        for (String label : species.getLabels()) {
            if (label != null && !label.isBlank()) {
                labels.add(label.toLowerCase(Locale.ROOT));
            }
        }
        return labels;
    }

    private static boolean matchesRequiredLabel(Set<String> speciesLabels, List<String> required) {
        return required.isEmpty() || matchesAnyLabel(speciesLabels, required);
    }

    private static boolean matchesAnyLabel(Set<String> speciesLabels, List<String> wanted) {
        if (wanted.isEmpty() || speciesLabels.isEmpty()) {
            return false;
        }
        for (String label : wanted) {
            if (speciesLabels.contains(label)) {
                return true;
            }
        }
        return false;
    }

    private static void validateAnnouncementPlaceholders(String message, List<String> errors) {
        Matcher matcher = PLACEHOLDER.matcher(message == null ? "" : message);
        while (matcher.find()) {
            String placeholder = matcher.group(1).toLowerCase(Locale.ROOT);
            if (!ANNOUNCEMENT_PLACEHOLDERS.contains(placeholder)) {
                errors.add("config.yml -> announcement.message contains unknown placeholder '{" + matcher.group(1) + "}'. Allowed: " + ANNOUNCEMENT_PLACEHOLDERS + ".");
            }
        }
    }

    private static void validateMessagePlaceholders(String message, String path, List<String> errors) {
        Matcher matcher = PLACEHOLDER.matcher(message == null ? "" : message);
        while (matcher.find()) {
            String placeholder = matcher.group(1).toLowerCase(Locale.ROOT);
            if (!ANNOUNCEMENT_PLACEHOLDERS.contains(placeholder)) {
                errors.add("messages.yml -> " + path + " contains unknown placeholder '{" + matcher.group(1) + "}'. Allowed: " + ANNOUNCEMENT_PLACEHOLDERS + ".");
            }
        }
    }

    private static void validateConfigShape(Config source, List<String> errors) {
        validateAllowedKeys(source, "", Set.of(
                "enabled", "minimum-online-players", "pause-timer-when-below-minimum-players", "spawn-chance", "debug", "interval", "spawn", "announcement"
        ), "config.yml", errors);
        validateSection(source, "interval", "config.yml", errors);
        validateSection(source, "spawn", "config.yml", errors);
        validateSection(source, "announcement", "config.yml", errors);
        validateAllowedKeys(source, "interval", Set.of("min-minutes", "max-minutes"), "config.yml", errors);
        validateAllowedKeys(source, "spawn", Set.of(
                "min-distance", "max-distance", "attempts", "min-level", "max-level", "allow-water", "count-towards-spawn-cap",
                "shiny-one-in-chance",
                "dimensions", "required-labels", "excluded-labels", "species-whitelist", "species-blacklist"
        ), "config.yml", errors);
        validateAllowedKeys(source, "announcement", Set.of("enabled", "message", "show-coordinates"), "config.yml", errors);

        movedConfigKey(source, "spawn.dimensions", "worlds.yml -> allowed-worlds / blocked-worlds / allow-all-worlds", errors);
        movedConfigKey(source, "spawn.required-labels", "pokemon.yml -> required-labels", errors);
        movedConfigKey(source, "spawn.excluded-labels", "blacklist.yml -> labels", errors);
        movedConfigKey(source, "spawn.species-whitelist", "pokemon.yml -> specific-pokemon", errors);
        movedConfigKey(source, "spawn.species-blacklist", "blacklist.yml -> pokemon", errors);

        validateBoolean(source, "enabled", "config.yml", errors);
        validateInteger(source, "minimum-online-players", "config.yml", errors);
        validateBoolean(source, "pause-timer-when-below-minimum-players", "config.yml", errors);
        validateNumber(source, "spawn-chance", "config.yml", errors);
        validateBoolean(source, "debug", "config.yml", errors);
        validateInteger(source, "interval.min-minutes", "config.yml", errors);
        validateInteger(source, "interval.max-minutes", "config.yml", errors);
        validateInteger(source, "spawn.min-distance", "config.yml", errors);
        validateInteger(source, "spawn.max-distance", "config.yml", errors);
        validateInteger(source, "spawn.attempts", "config.yml", errors);
        validateInteger(source, "spawn.min-level", "config.yml", errors);
        validateInteger(source, "spawn.max-level", "config.yml", errors);
        validateBoolean(source, "spawn.allow-water", "config.yml", errors);
        validateBoolean(source, "spawn.count-towards-spawn-cap", "config.yml", errors);
        validateInteger(source, "spawn.shiny-one-in-chance", "config.yml", errors);
        validateBoolean(source, "announcement.enabled", "config.yml", errors);
        validateString(source, "announcement.message", "config.yml", errors);
        validateBoolean(source, "announcement.show-coordinates", "config.yml", errors);
    }

    private static void validateWorldsShape(Config source, List<String> errors) {
        validateAllowedKeys(source, "", Set.of("allow-all-worlds", "allowed-worlds", "blocked-worlds"), "worlds.yml", errors);
        validateBoolean(source, "allow-all-worlds", "worlds.yml", errors);
        validateStringList(source, "allowed-worlds", "worlds.yml", errors);
        validateStringList(source, "blocked-worlds", "worlds.yml", errors);
    }

    private static void validatePokemonShape(Config source, List<String> errors) {
        validateAllowedKeys(source, "", Set.of("required-labels", "specific-pokemon"), "pokemon.yml", errors);
        validateStringList(source, "required-labels", "pokemon.yml", errors);
        validateStringList(source, "specific-pokemon", "pokemon.yml", errors);
    }

    private static void validateBlacklistShape(Config source, List<String> errors) {
        validateAllowedKeys(source, "", Set.of("pokemon", "labels"), "blacklist.yml", errors);
        validateStringList(source, "pokemon", "blacklist.yml", errors);
        validateStringList(source, "labels", "blacklist.yml", errors);
    }

    private static void validateBiomesShape(Config source, List<String> errors) {
        validateAllowedKeys(source, "", Set.of("enabled", "use-cobblemon-spawn-data", "when-no-spawn-data"), "biomes.yml", errors);
        validateBoolean(source, "enabled", "biomes.yml", errors);
        validateBoolean(source, "use-cobblemon-spawn-data", "biomes.yml", errors);
        validateString(source, "when-no-spawn-data", "biomes.yml", errors);
    }

    private static void validateProtectionShape(Config source, List<String> errors) {
        validateAllowedKeys(source, "", Set.of("enabled", "duration-minutes", "prevent-natural-despawn", "restore-if-removed"), "protection.yml", errors);
        validateBoolean(source, "enabled", "protection.yml", errors);
        validateInteger(source, "duration-minutes", "protection.yml", errors);
        validateBoolean(source, "prevent-natural-despawn", "protection.yml", errors);
        validateBoolean(source, "restore-if-removed", "protection.yml", errors);
    }

    private static void movedConfigKey(Config source, String oldPath, String newLocation, List<String> errors) {
        if (source.contains(oldPath)) {
            errors.add("config.yml -> '" + oldPath + "' moved to " + newLocation + ". Move the value there and remove the old key.");
        }
    }

    private static void validateCommandsShape(Config source, List<String> errors) {
        validateAllowedKeys(source, "", Set.of("command", "subcommands"), "commands.yml", errors);
        validateSection(source, "command", "commands.yml", errors);
        validateSection(source, "subcommands", "commands.yml", errors);
        validateAllowedKeys(source, "command", Set.of("name", "aliases", "permission-level", "permission", "no-permission-message"), "commands.yml", errors);
        validateAllowedKeys(source, "subcommands", Set.of("reload", "status", "force", "roll", "reschedule"), "commands.yml", errors);
        validateString(source, "command.name", "commands.yml", errors);
        validateStringList(source, "command.aliases", "commands.yml", errors);
        validateInteger(source, "command.permission-level", "commands.yml", errors);
        validateString(source, "command.permission", "commands.yml", errors);
        validateString(source, "command.no-permission-message", "commands.yml", errors);

        for (String key : List.of("reload", "status", "force", "roll", "reschedule")) {
            String path = "subcommands." + key;
            validateSection(source, path, "commands.yml", errors);
            validateAllowedKeys(source, path, Set.of("name", "permission-level", "permission"), "commands.yml", errors);
            validateString(source, path + ".name", "commands.yml", errors);
            validateInteger(source, path + ".permission-level", "commands.yml", errors);
            validateString(source, path + ".permission", "commands.yml", errors);
        }
    }

    private static void validateWeightsShape(Config source, List<String> errors) {
        validateAllowedKeys(source, "", Set.of("enabled", "default-weight", "labels"), "weights.yml", errors);
        validateBoolean(source, "enabled", "weights.yml", errors);
        validateNumber(source, "default-weight", "weights.yml", errors);
        validateSection(source, "labels", "weights.yml", errors);
        ConfigSection section = source.getSection("labels");
        if (section != null) for (String key : section.getKeys()) {
            Object value = section.asMap().get(key);
            if (!(value instanceof Number)) errors.add("weights.yml -> labels." + key + " must be a number.");
        }
    }

    private static void validateCooldownsShape(Config source, List<String> errors) {
        validateAllowedKeys(source, "", Set.of("species", "global-after-event"), "cooldowns.yml", errors);
        validateSection(source, "species", "cooldowns.yml", errors);
        validateSection(source, "global-after-event", "cooldowns.yml", errors);
        validateAllowedKeys(source, "species", Set.of("enabled", "default-minutes", "overrides"), "cooldowns.yml", errors);
        validateAllowedKeys(source, "global-after-event", Set.of("enabled", "captured-minutes", "defeated-minutes"), "cooldowns.yml", errors);
        validateBoolean(source, "species.enabled", "cooldowns.yml", errors);
        validateInteger(source, "species.default-minutes", "cooldowns.yml", errors);
        validateSection(source, "species.overrides", "cooldowns.yml", errors);
        validateBoolean(source, "global-after-event.enabled", "cooldowns.yml", errors);
        validateInteger(source, "global-after-event.captured-minutes", "cooldowns.yml", errors);
        validateInteger(source, "global-after-event.defeated-minutes", "cooldowns.yml", errors);
    }

    private static void validateLimitsShape(Config source, List<String> errors) {
        validateAllowedKeys(source, "", Set.of("active-limit"), "limits.yml", errors);
        validateSection(source, "active-limit", "limits.yml", errors);
        validateAllowedKeys(source, "active-limit", Set.of("enabled", "max-active", "apply-to-force"), "limits.yml", errors);
        validateBoolean(source, "active-limit.enabled", "limits.yml", errors);
        validateInteger(source, "active-limit.max-active", "limits.yml", errors);
        validateBoolean(source, "active-limit.apply-to-force", "limits.yml", errors);
    }

    private static void validateOverridesShape(Config source, List<String> errors) {
        validateAllowedKeys(source, "", Set.of("pokemon"), "pokemon-overrides.yml", errors);
        validateSection(source, "pokemon", "pokemon-overrides.yml", errors);
    }

    private static void validateBossbarShape(Config source, List<String> errors) {
        validateAllowedKeys(source, "", Set.of("enabled", "title", "color", "overlay", "show-to-all-players"), "bossbar.yml", errors);
        validateBoolean(source, "enabled", "bossbar.yml", errors);
        validateString(source, "title", "bossbar.yml", errors);
        validateString(source, "color", "bossbar.yml", errors);
        validateString(source, "overlay", "bossbar.yml", errors);
        validateBoolean(source, "show-to-all-players", "bossbar.yml", errors);
    }

    private static void validateEventsShape(Config source, List<String> errors) {
        validateAllowedKeys(source, "", Set.of("captured", "defeated", "killed", "protection-expired", "despawned"), "events.yml", errors);
        for (String key : List.of("captured", "defeated", "killed", "protection-expired", "despawned")) validateBoolean(source, key, "events.yml", errors);
    }

    private static void validateHoverShape(Config source, List<String> errors) {
        validateAllowedKeys(source, "", Set.of("enabled", "messages", "hint", "lines", "values"), "hover.yml", errors);
        validateBoolean(source, "enabled", "hover.yml", errors);
        validateSection(source, "messages", "hover.yml", errors);
        validateSection(source, "hint", "hover.yml", errors);
        validateSection(source, "values", "hover.yml", errors);
        validateAllowedKeys(source, "messages", Set.of(
                "spawn", "captured", "defeated", "killed", "protection-expired", "despawned", "force-command", "roll-command"
        ), "hover.yml", errors);
        for (String key : List.of("spawn", "captured", "defeated", "killed", "protection-expired", "despawned", "force-command", "roll-command")) {
            validateBoolean(source, "messages." + key, "hover.yml", errors);
        }
        validateAllowedKeys(source, "hint", Set.of("enabled", "text"), "hover.yml", errors);
        validateBoolean(source, "hint.enabled", "hover.yml", errors);
        validateString(source, "hint.text", "hover.yml", errors);
        validateAllowedKeys(source, "values", Set.of(
                "shiny-yes", "shiny-no", "gender-male", "gender-female", "gender-genderless", "gender-unknown", "empty-move"
        ), "hover.yml", errors);
        validateString(source, "values.shiny-yes", "hover.yml", errors);
        validateString(source, "values.shiny-no", "hover.yml", errors);
        validateString(source, "values.gender-male", "hover.yml", errors);
        validateString(source, "values.gender-female", "hover.yml", errors);
        validateString(source, "values.gender-genderless", "hover.yml", errors);
        validateString(source, "values.gender-unknown", "hover.yml", errors);
        validateString(source, "values.empty-move", "hover.yml", errors);

        if (!source.contains("lines") && source.getBoolean("enabled", true)) {
            errors.add("hover.yml -> 'lines' is required when the Pokemon hover is enabled.");
        }
        if (source.contains("lines")) {
            Object value = source.getObject("lines");
            if (!(value instanceof List<?> list)) {
                errors.add("hover.yml -> 'lines' must be a YAML list, but found " + printable(value) + ".");
            } else {
                if (list.isEmpty()) {
                    errors.add("hover.yml -> 'lines' must contain at least one line.");
                }
                for (int i = 0; i < list.size(); i++) {
                    Object entry = list.get(i);
                    if (!(entry instanceof String line)) {
                        errors.add("hover.yml -> 'lines[" + i + "]' must be text, but found " + printable(entry) + ".");
                        continue;
                    }
                    validateHoverPlaceholders(line, i, errors);
                }
            }
        }
    }

    private static void validateHoverPlaceholders(String line, int index, List<String> errors) {
        Matcher matcher = PLACEHOLDER.matcher(line == null ? "" : line);
        while (matcher.find()) {
            String placeholder = matcher.group(1).toLowerCase(Locale.ROOT);
            if (!HOVER_PLACEHOLDERS.contains(placeholder)) {
                errors.add("hover.yml -> lines[" + index + "] contains unknown or disallowed placeholder '{" + matcher.group(1)
                        + "}'. Hover only accepts Pokemon data. Allowed: " + HOVER_PLACEHOLDERS + ".");
            }
        }
    }

    private static void validateMessagesShape(Config source, List<String> errors) {
        validateAllowedKeys(source, "", Set.of("commands", "status", "placeholders", "events"), "messages.yml", errors);
        validateSection(source, "commands", "messages.yml", errors);
        validateSection(source, "commands.reload", "messages.yml", errors);
        validateSection(source, "status", "messages.yml", errors);
        validateSection(source, "placeholders", "messages.yml", errors);
        validateSection(source, "events", "messages.yml", errors);
        for (String key : List.of("captured", "defeated", "killed", "protection-expired", "despawned")) {
            validateSection(source, "events." + key, "messages.yml", errors);
            validateAllowedKeys(source, "events." + key,
                    key.equals("killed") ? Set.of("message", "unknown-killer", "enabled") : Set.of("message", "enabled"),
                    "messages.yml", errors);
            if (source.contains("events." + key + ".enabled")) {
                errors.add("messages.yml -> events." + key + ".enabled moved to events.yml -> " + key + ". Move the value there and remove the old key.");
            }
            validateString(source, "events." + key + ".message", "messages.yml", errors);
            if (key.equals("killed")) validateString(source, "events.killed.unknown-killer", "messages.yml", errors);
            validateMessagePlaceholders(source.getString("events." + key + ".message", ""), "events." + key + ".message", errors);
        }
        validateAllowedKeys(source, "commands", Set.of("reload"), "messages.yml", errors);
        validateAllowedKeys(source, "commands.reload", Set.of("success", "failed"), "messages.yml", errors);
        validateAllowedKeys(source, "status", Set.of("header", "paused"), "messages.yml", errors);
        validateAllowedKeys(source, "placeholders", Set.of("hidden-coordinate"), "messages.yml", errors);
        validateAllowedKeys(source, "events", Set.of("captured", "defeated", "killed", "protection-expired", "despawned"), "messages.yml", errors);
        validateString(source, "commands.reload.success", "messages.yml", errors);
        validateString(source, "commands.reload.failed", "messages.yml", errors);
        validateString(source, "status.header", "messages.yml", errors);
        validateString(source, "status.paused", "messages.yml", errors);
        validateString(source, "placeholders.hidden-coordinate", "messages.yml", errors);
    }

    private static void validateStateShape(Config source, List<String> errors) {
        validateAllowedKeys(source, "", Set.of("timer", "cooldowns"), "state.yml", errors);
        validateSection(source, "timer", "state.yml", errors);
        validateAllowedKeys(source, "timer", Set.of("next-attempt-epoch-millis", "paused", "paused-remaining-millis"), "state.yml", errors);
        validateWholeNumber(source, "timer.next-attempt-epoch-millis", "state.yml", errors);
        validateBoolean(source, "timer.paused", "state.yml", errors);
        validateWholeNumber(source, "timer.paused-remaining-millis", "state.yml", errors);
        long nextAttempt = source.getLong("timer.next-attempt-epoch-millis", 0L);
        long pausedRemaining = source.getLong("timer.paused-remaining-millis", 0L);
        if (nextAttempt < 0L) errors.add("state.yml -> timer.next-attempt-epoch-millis cannot be negative.");
        if (pausedRemaining < 0L) errors.add("state.yml -> timer.paused-remaining-millis cannot be negative.");
        if (source.contains("cooldowns")) {
            validateSection(source, "cooldowns", "state.yml", errors);
            validateAllowedKeys(source, "cooldowns", Set.of("global-until-epoch-millis", "species-until"), "state.yml", errors);
            validateWholeNumber(source, "cooldowns.global-until-epoch-millis", "state.yml", errors);
            if (source.getLong("cooldowns.global-until-epoch-millis", 0L) < 0L) errors.add("state.yml -> cooldowns.global-until-epoch-millis cannot be negative.");
            if (source.contains("cooldowns.species-until")) {
                validateStringList(source, "cooldowns.species-until", "state.yml", errors);
                for (String entry : source.getStringList("cooldowns.species-until")) {
                    int separator = entry.lastIndexOf('|');
                    if (separator <= 0 || separator >= entry.length() - 1) {
                        errors.add("state.yml -> cooldowns.species-until contains invalid internal entry '" + entry + "'.");
                        continue;
                    }
                    ResourceLocation id = ResourceLocation.tryParse(entry.substring(0, separator));
                    try {
                        long until = Long.parseLong(entry.substring(separator + 1));
                        if (id == null || until < 0L) errors.add("state.yml -> cooldowns.species-until contains invalid internal entry '" + entry + "'.");
                    } catch (NumberFormatException exception) {
                        errors.add("state.yml -> cooldowns.species-until contains invalid internal entry '" + entry + "'.");
                    }
                }
            }
        }
    }

    private static void validateAllowedKeys(Config source, String path, Set<String> allowed, String file, List<String> errors) {
        Iterable<String> keys = path.isEmpty() ? source.getKeys() : source.getKeys(path);
        for (String key : keys) {
            if (!allowed.contains(key)) {
                String full = path.isEmpty() ? key : path + "." + key;
                errors.add(file + " -> unknown key '" + full + "'. Check the spelling or remove it.");
            }
        }
    }

    private static void validateSection(Config source, String path, String file, List<String> errors) {
        if (!source.contains(path)) {
            return;
        }
        Object value = source.getObject(path);
        if (!(value instanceof Map<?, ?>)) {
            errors.add(file + " -> '" + path + "' must be a YAML section, not " + typeName(value) + ".");
        }
    }

    private static void validateBoolean(Config source, String path, String file, List<String> errors) {
        if (!source.contains(path)) {
            return;
        }
        Object value = source.getObject(path);
        if (!(value instanceof Boolean)) {
            errors.add(file + " -> '" + path + "' must be true or false, but found " + printable(value) + ".");
        }
    }

    private static void validateNumber(Config source, String path, String file, List<String> errors) {
        if (!source.contains(path)) {
            return;
        }
        Object value = source.getObject(path);
        if (!(value instanceof Number)) {
            errors.add(file + " -> '" + path + "' must be a number, but found " + printable(value) + ".");
        }
    }

    private static void validateInteger(Config source, String path, String file, List<String> errors) {
        if (!source.contains(path)) {
            return;
        }
        Object value = source.getObject(path);
        if (!(value instanceof Number number)) {
            errors.add(file + " -> '" + path + "' must be a whole number, but found " + printable(value) + ".");
            return;
        }
        double numeric = number.doubleValue();
        if (!Double.isFinite(numeric) || numeric != Math.rint(numeric) || numeric < Integer.MIN_VALUE || numeric > Integer.MAX_VALUE) {
            errors.add(file + " -> '" + path + "' must be a whole number between " + Integer.MIN_VALUE + " and " + Integer.MAX_VALUE + ", but found " + printable(value) + ".");
        }
    }

    private static void validateWholeNumber(Config source, String path, String file, List<String> errors) {
        if (!source.contains(path)) {
            return;
        }
        Object value = source.getObject(path);
        if (!(value instanceof Number number)) {
            errors.add(file + " -> '" + path + "' must be a whole number, but found " + printable(value) + ".");
            return;
        }
        double numeric = number.doubleValue();
        if (!Double.isFinite(numeric) || numeric != Math.rint(numeric)) {
            errors.add(file + " -> '" + path + "' must be a whole number, but found " + printable(value) + ".");
        }
    }

    private static void validateString(Config source, String path, String file, List<String> errors) {
        if (!source.contains(path)) {
            return;
        }
        Object value = source.getObject(path);
        if (!(value instanceof String)) {
            errors.add(file + " -> '" + path + "' must be text, but found " + printable(value) + ".");
        }
    }

    private static void validateStringList(Config source, String path, String file, List<String> errors) {
        if (!source.contains(path)) {
            return;
        }
        Object value = source.getObject(path);
        if (!(value instanceof List<?> list)) {
            errors.add(file + " -> '" + path + "' must be a YAML list, but found " + printable(value) + ".");
            return;
        }
        for (int i = 0; i < list.size(); i++) {
            Object entry = list.get(i);
            if (!(entry instanceof String string)) {
                errors.add(file + " -> '" + path + "[" + i + "]' must be text, but found " + printable(entry) + ".");
            } else if (string.isBlank()) {
                errors.add(file + " -> '" + path + "[" + i + "]' cannot be blank.");
            }
        }
    }

    private static void validateUniqueSubcommandNames(List<CommandSettings.Subcommand> subcommands, List<String> errors) {
        Set<String> seen = new HashSet<>();
        for (CommandSettings.Subcommand subcommand : subcommands) {
            if (!seen.add(subcommand.name())) {
                errors.add("commands.yml -> subcommand name '" + subcommand.name() + "' is used more than once. Each subcommand must have a unique name.");
            }
        }
    }

    private static List<String> commandRestartWarnings(CommandSettings previous, CommandSettings current) {
        if (previous == null) {
            return List.of();
        }

        List<String> warnings = new ArrayList<>();
        if (!previous.rootName().equals(current.rootName()) || !previous.aliases().equals(current.aliases())) {
            warnings.add("commands.yml -> command.name/command.aliases changed. Access settings are active now, but names and aliases require a server restart.");
        }
        if (!previous.reload().name().equals(current.reload().name())
                || !previous.status().name().equals(current.status().name())
                || !previous.force().name().equals(current.force().name())
                || !previous.roll().name().equals(current.roll().name())
                || !previous.reschedule().name().equals(current.reschedule().name())) {
            warnings.add("commands.yml -> one or more subcommand names changed. Access settings are active now, but new command literals require a server restart.");
        }
        return warnings;
    }

    private static String normalizeLiteral(String value, String fallback, String path, List<String> errors) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty() || !COMMAND_LITERAL.matcher(normalized).matches()) {
            errors.add(path + " contains invalid command literal '" + value + "'. Use a literal without spaces.");
            return fallback;
        }
        return normalized;
    }

    private static int validPermissionLevel(int level, String path, List<String> errors) {
        if (level < 0 || level > 4) {
            errors.add(path + " must be between 0 and 4, but found " + level + ".");
            return 2;
        }
        return level;
    }

    private static List<String> normalizeList(List<String> input) {
        List<String> normalized = new ArrayList<>();
        if (input == null) {
            return normalized;
        }
        for (String value : input) {
            if (value == null) {
                continue;
            }
            String clean = value.trim().toLowerCase(Locale.ROOT);
            if (!clean.isEmpty() && !normalized.contains(clean)) {
                normalized.add(clean);
            }
        }
        return normalized;
    }

    private static Set<String> normalizeResourceLocationSet(
            List<String> input,
            String file,
            String path,
            List<String> errors
    ) {
        Set<String> normalized = new LinkedHashSet<>();
        if (input == null) {
            return normalized;
        }

        for (String raw : input) {
            if (raw == null || raw.isBlank()) {
                continue;
            }

            String value = raw.trim().toLowerCase(Locale.ROOT);
            if (!value.contains(":")) {
                errors.add(file + " -> " + path + " contains '" + raw + "' without a namespace. Use namespace:path, for example minecraft:overworld or cobblemon:rayquaza.");
                continue;
            }

            ResourceLocation id = ResourceLocation.tryParse(value);
            if (id == null) {
                errors.add(file + " -> " + path + " contains invalid id '" + raw + "'. Use namespace:path.");
                continue;
            }
            normalized.add(id.toString().toLowerCase(Locale.ROOT));
        }
        return normalized;
    }

    private static void collectConfigErrors(Config source, List<String> errors) {
        if (source == null) {
            errors.add("A configuration file could not be loaded.");
            return;
        }
        if (!source.isValid()) {
            List<String> validation = source.getValidationErrors();
            if (validation == null || validation.isEmpty()) {
                errors.add(source.getFileName() + " contains invalid YAML.");
            } else {
                for (String error : validation) {
                    errors.add(source.getFileName() + " -> " + error);
                }
            }
        }
    }

    private static String typeName(Object value) {
        return value == null ? "null" : value.getClass().getSimpleName();
    }

    private static String printable(Object value) {
        if (value == null) {
            return "null";
        }
        return "'" + value + "' (" + typeName(value) + ")";
    }

    private static String describe(Throwable exception) {
        String message = exception.getMessage();
        return exception.getClass().getSimpleName() + (message == null || message.isBlank() ? "" : ": " + message);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
