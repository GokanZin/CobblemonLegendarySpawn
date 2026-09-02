package br.com.gokan.legendaryspawn.spawn;

import br.com.gokan.legendaryspawn.LegendarySpawn;
import br.com.gokan.legendaryspawn.config.LegendarySettings;
import br.com.gokan.legendaryspawn.util.TextUtil;
import br.com.gokan.legendaryspawn.util.PokemonHoverUtil;
import com.cobblemon.mod.common.CobblemonEntities;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.api.reactive.ObservableSubscription;
import com.cobblemon.mod.common.api.spawning.CobblemonSpawnPools;
import com.cobblemon.mod.common.api.spawning.detail.SpawnPool;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.Species;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

public final class LegendarySpawner {

    private final LegendarySpawn mod;
    private final MinecraftServer server;
    private final LegendarySpawnTracker tracker;
    private static final long BIOME_RETRY_DELAY_MILLIS = 30_000L;

    private volatile BiomeSpawnIndex biomeIndex = BiomeSpawnIndex.empty();
    private volatile long nextBiomeRetryAtMillis;
    private SpawnPool subscribedSpawnPool;
    private ObservableSubscription<SpawnPool> spawnPoolSubscription;
    private boolean biomeTrackingStarted;

    public LegendarySpawner(LegendarySpawn mod, MinecraftServer server) {
        this.mod = mod;
        this.server = server;
        this.tracker = new LegendarySpawnTracker(mod, server);
    }

    public void startTracking() { tracker.start(); }
    public boolean isTrackingAvailable() { return tracker.isStarted(); }
    public void stopTracking() { tracker.stop(); }
    public void tickTracking(long now) { tracker.tick(now); }
    public void onLivingEntityDeath(LivingEntity entity, DamageSource damageSource) { tracker.onLivingEntityDeath(entity, damageSource); }
    public void onTrackingSettingsReloaded() { tracker.onSettingsReloaded(); }
    public int trackedLegendCount() { return tracker.trackedCount(); }
    public int protectedLegendCount() { return tracker.protectedCount(); }

    public void startBiomeTracking() {
        LegendarySettings allSettings = mod.configManager().settings();
        LegendarySettings.Biomes settings = allSettings.biomes();
        if (!allSettings.enabled() || !settings.enabled() || !settings.useCobblemonSpawnData()) {
            if (biomeTrackingStarted) stopBiomeTracking();
            return;
        }
        if (biomeTrackingStarted) {
            refreshBiomeIndex("settings reload");
            return;
        }
        try {
            SpawnPool pool = CobblemonSpawnPools.INSTANCE.getWORLD_SPAWN_POOL();
            subscribedSpawnPool = pool;
            refreshBiomeIndex("server startup");
            spawnPoolSubscription = pool.getObservable().subscribe((Consumer<SpawnPool>) ignored -> refreshBiomeIndex("Cobblemon spawn pool reload"));
            biomeTrackingStarted = true;
        } catch (RuntimeException exception) {
            LegendarySpawn.logRuntimeError("[Biomes] Could not start Cobblemon spawn-pool tracking: " + describe(exception), exception);
        }
    }

    public void stopBiomeTracking() {
        SpawnPool pool = subscribedSpawnPool;
        ObservableSubscription<SpawnPool> subscription = spawnPoolSubscription;
        spawnPoolSubscription = null;
        subscribedSpawnPool = null;
        biomeTrackingStarted = false;
        biomeIndex = BiomeSpawnIndex.empty();
        if (pool != null && subscription != null) {
            try { pool.getObservable().unsubscribe(subscription); }
            catch (RuntimeException exception) { LegendarySpawn.logRuntimeError("[Biomes] Could not remove Cobblemon spawn-pool subscription cleanly: " + describe(exception), exception); }
        }
    }

    public void refreshBiomeIndex(String reason) {
        try {
            BiomeSpawnIndex refreshed = BiomeSpawnIndex.build();
            biomeIndex = refreshed;
            nextBiomeRetryAtMillis = 0L;
            if (mod.configManager().settings().debug()) {
                LegendarySpawn.logRuntimeInfo("[Debug] Biome spawn index refreshed after " + reason + ": species=" + refreshed.speciesWithSpawnData() + ", pokemon-details=" + refreshed.pokemonDetails() + ", herd-details=" + refreshed.herdDetails());
            }
        } catch (RuntimeException exception) {
            nextBiomeRetryAtMillis = System.currentTimeMillis() + BIOME_RETRY_DELAY_MILLIS;
            LegendarySpawn.logRuntimeError("[Biomes] Failed to refresh Cobblemon biome spawn data after " + reason + ": " + describe(exception) + ". Automatic retry is throttled for 30 seconds.", exception);
        }
    }

    public List<Species> validSpecies(boolean bypassCooldowns) {
        LegendarySettings settings = mod.configManager().settings();
        LegendarySettings.PokemonPool pool = settings.pokemonPool();
        LegendarySettings.Blacklist blacklist = settings.blacklist();
        BiomeSpawnIndex currentBiomeIndex = currentBiomeIndex(settings.biomes());
        if (settings.biomes().enabled() && settings.biomes().useCobblemonSpawnData() && !currentBiomeIndex.loaded()) {
            return List.of();
        }
        List<Species> result = new ArrayList<>();
        Set<String> onlineDimensions = new java.util.HashSet<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            onlineDimensions.add(player.serverLevel().dimension().location().toString().toLowerCase(Locale.ROOT));
        }
        for (Species species : PokemonSpecies.getImplemented()) {
            String id = species.getResourceIdentifier().toString().toLowerCase(Locale.ROOT);
            Set<String> labels = species.getLabels();
            if (!pool.specificPokemon().isEmpty() && !pool.specificPokemon().contains(id)) continue;
            if (blacklist.pokemon().contains(id)) continue;
            if (!matchesRequiredLabel(labels, pool.requiredLabels())) continue;
            if (matchesAnyLabel(labels, blacklist.labels())) continue;
            LegendarySettings.PokemonOverride override = settings.pokemonOverrides().getOrDefault(id, LegendarySettings.PokemonOverride.empty());
            if (override.biomeMode() == LegendarySettings.OverrideMode.INHERIT && !currentBiomeIndex.isSpeciesEligible(species, settings.biomes())) continue;
            if (!bypassCooldowns && mod.scheduler() != null && !mod.scheduler().isSpeciesAvailable(id)) continue;
            if (!hasEligibleWorld(settings.worlds(), override, onlineDimensions)) continue;
            result.add(species);
        }
        return result;
    }


    private static boolean hasEligibleWorld(LegendarySettings.Worlds global, LegendarySettings.PokemonOverride override, Set<String> onlineDimensions) {
        for (String dimension : onlineDimensions) {
            if (isWorldAllowed(global, override, dimension)) return true;
        }
        return false;
    }

    public List<ServerPlayer> eligiblePlayers() {
        LegendarySettings settings = mod.configManager().settings();
        List<Species> species = validSpecies(false);
        if (species.isEmpty()) return List.of();
        List<ServerPlayer> players = new ArrayList<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            String dimension = player.serverLevel().dimension().location().toString().toLowerCase(Locale.ROOT);
            for (Species entry : species) {
                String id = entry.getResourceIdentifier().toString().toLowerCase(Locale.ROOT);
                LegendarySettings.PokemonOverride override = settings.pokemonOverrides().getOrDefault(id, LegendarySettings.PokemonOverride.empty());
                if (isWorldAllowed(settings.worlds(), override, dimension)) {
                    players.add(player);
                    break;
                }
            }
        }
        return players;
    }

    private List<ServerPlayer> eligiblePlayers(Species species) {
        LegendarySettings settings = mod.configManager().settings();
        String id = species.getResourceIdentifier().toString().toLowerCase(Locale.ROOT);
        LegendarySettings.PokemonOverride override = settings.pokemonOverrides().getOrDefault(id, LegendarySettings.PokemonOverride.empty());
        List<ServerPlayer> players = new ArrayList<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            String dimension = player.serverLevel().dimension().location().toString().toLowerCase(Locale.ROOT);
            if (isWorldAllowed(settings.worlds(), override, dimension)) players.add(player);
        }
        return players;
    }

    public SpawnResult spawnRandom(boolean bypassCooldowns) {
        if (!tracker.isStarted()) return SpawnResult.failure("Cobblemon event/protection tracking is unavailable, so the mod refused to create an untracked legendary.");
        List<Species> speciesPool = validSpecies(bypassCooldowns);
        if (speciesPool.isEmpty()) return SpawnResult.failure("The configured species pool has no currently eligible Pokemon.");
        ThreadLocalRandom random = ThreadLocalRandom.current();
        Species species = chooseWeightedSpecies(speciesPool, random);
        List<ServerPlayer> players = eligiblePlayers(species);
        if (players.isEmpty()) return SpawnResult.failure("No online player is currently inside a world allowed for the selected Pokemon.");
        ServerPlayer target = players.get(random.nextInt(players.size()));

        LegendarySettings settings = mod.configManager().settings();
        LegendarySettings.Spawn spawnSettings = settings.spawn();
        int level = random.nextInt(spawnSettings.minLevel(), spawnSettings.maxLevel() + 1);
        Pokemon pokemon;
        PokemonEntity entity;
        ServerLevel world = target.serverLevel();
        try {
            pokemon = species.create(level);
            int shinyChance = spawnSettings.shinyOneInChance();
            if (shinyChance > 0 && random.nextInt(shinyChance) == 0) pokemon.setShiny(true);
            entity = new PokemonEntity(world, pokemon, CobblemonEntities.POKEMON);
            entity.setCountsTowardsSpawnCap(spawnSettings.countTowardsSpawnCap());
        } catch (RuntimeException exception) {
            LegendarySpawn.logRuntimeError("[Spawn] Failed to create " + species.getResourceIdentifier() + ": " + describe(exception), exception);
            return SpawnResult.failure("The selected species could not be created safely.");
        }

        BlockPos position = findSafePosition(target, species, entity, spawnSettings, settings.biomes(), random);
        if (position == null) {
            return SpawnResult.failure("No safe loaded position matching the world/biome rules was found after " + spawnSettings.attempts() + " attempts.");
        }
        entity.setPos(position.getX() + 0.5D, position.getY(), position.getZ() + 0.5D);
        if (!world.addFreshEntity(entity)) return SpawnResult.failure("Minecraft rejected the Pokemon entity while adding it to the world.");
        if (!tracker.register(pokemon, entity, species, level, target.getUUID(), target.getGameProfile().getName())) {
            entity.discard();
            return SpawnResult.failure("The Pokemon entity was created, but LegendarySpawn could not safely register its protection/event tracker. The entity was removed to prevent an untracked spawn.");
        }
        SpawnResult result = SpawnResult.success(species, pokemon, level, position, world, target);
        try { announce(result, pokemon); }
        catch (RuntimeException exception) { LegendarySpawn.logRuntimeError("[Announcement] Pokemon spawned, but the announcement failed: " + describe(exception), exception); }
        return result;
    }

    private Species chooseWeightedSpecies(List<Species> species, ThreadLocalRandom random) {
        LegendarySettings.Weights weights = mod.configManager().settings().weights();
        if (!weights.enabled() || species.size() == 1) return species.get(random.nextInt(species.size()));
        double total = 0.0D;
        for (Species entry : species) total += weightFor(entry, weights);
        double roll = random.nextDouble(total);
        double cursor = 0.0D;
        for (Species entry : species) {
            cursor += weightFor(entry, weights);
            if (roll < cursor) return entry;
        }
        return species.get(species.size() - 1);
    }

    private static double weightFor(Species species, LegendarySettings.Weights settings) {
        double weight = settings.defaultWeight();
        boolean matchedConfiguredLabel = false;
        Set<String> labels = species.getLabels();
        if (labels != null) {
            for (String label : labels) {
                if (label == null) continue;
                Double configured = settings.labelWeights().get(label.toLowerCase(Locale.ROOT));
                if (configured != null) {
                    weight = matchedConfiguredLabel ? Math.max(weight, configured) : configured;
                    matchedConfiguredLabel = true;
                }
            }
        }
        return weight;
    }

    private BlockPos findSafePosition(ServerPlayer target, Species species, PokemonEntity entity, LegendarySettings.Spawn settings, LegendarySettings.Biomes biomeSettings, ThreadLocalRandom random) {
        ServerLevel world = target.serverLevel();
        BiomeSpawnIndex currentBiomeIndex = currentBiomeIndex(biomeSettings);
        if (biomeSettings.enabled() && biomeSettings.useCobblemonSpawnData() && !currentBiomeIndex.loaded()) {
            return null;
        }
        LegendarySettings allSettings = mod.configManager().settings();
        String speciesId = species.getResourceIdentifier().toString().toLowerCase(Locale.ROOT);
        LegendarySettings.PokemonOverride override = allSettings.pokemonOverrides().getOrDefault(speciesId, LegendarySettings.PokemonOverride.empty());
        double centerX = target.getX();
        double centerZ = target.getZ();
        double minSquared = (double) settings.minDistance() * settings.minDistance();
        double maxSquared = (double) settings.maxDistance() * settings.maxDistance();

        for (int attempt = 0; attempt < settings.attempts(); attempt++) {
            double angle = random.nextDouble(0.0D, Math.PI * 2.0D);
            double distance = Math.sqrt(random.nextDouble(minSquared, Math.nextUp(maxSquared)));
            int x = (int) Math.floor(centerX + Math.cos(angle) * distance);
            int z = (int) Math.floor(centerZ + Math.sin(angle) * distance);
            double actualX = x + 0.5D - centerX;
            double actualZ = z + 0.5D - centerZ;
            double actualDistanceSquared = actualX * actualX + actualZ * actualZ;
            if (actualDistanceSquared < minSquared || actualDistanceSquared > maxSquared) continue;
            int chunkX = x >> 4;
            int chunkZ = z >> 4;
            if (!world.hasChunk(chunkX, chunkZ)) continue;
            int y = world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            if (y <= world.getMinBuildHeight() || y >= world.getMaxBuildHeight() - 1) continue;
            BlockPos spawnPos = new BlockPos(x, y, z);
            BlockPos groundPos = spawnPos.below();
            if (!world.getWorldBorder().isWithinBounds(spawnPos)) continue;

            ResourceLocation biomeId = world.getBiome(spawnPos).unwrapKey().map(key -> key.location()).orElse(null);
            if (!isBiomeAllowed(species, biomeId, override, biomeSettings, currentBiomeIndex)) continue;

            FluidState spawnFluid = world.getFluidState(spawnPos);
            FluidState groundFluid = world.getFluidState(groundPos);
            if (spawnFluid.is(FluidTags.LAVA) || groundFluid.is(FluidTags.LAVA)) continue;
            boolean waterSurface = groundFluid.is(FluidTags.WATER);
            if (waterSurface && !settings.allowWater()) continue;
            if (!waterSurface && !groundFluid.isEmpty()) continue;
            BlockState ground = world.getBlockState(groundPos);
            BlockState spawnState = world.getBlockState(spawnPos);
            if (!waterSurface && !ground.isFaceSturdy(world, groundPos, Direction.UP)) continue;
            if (isDangerousGround(ground) || isDangerousSpawnBlock(spawnState)) continue;
            entity.setPos(spawnPos.getX() + 0.5D, spawnPos.getY(), spawnPos.getZ() + 0.5D);
            if (!world.noCollision(entity, entity.getBoundingBox())) continue;
            return spawnPos;
        }
        return null;
    }

    private static boolean isBiomeAllowed(Species species, ResourceLocation biomeId, LegendarySettings.PokemonOverride override, LegendarySettings.Biomes global, BiomeSpawnIndex index) {
        if (biomeId == null) return false;
        String id = biomeId.toString().toLowerCase(Locale.ROOT);
        if (override.blockedBiomes().contains(id)) return false;
        if (override.biomeMode() == LegendarySettings.OverrideMode.REPLACE) return override.allowedBiomes().contains(id);
        if (!override.allowedBiomes().isEmpty() && !override.allowedBiomes().contains(id)) return false;
        return !global.enabled() || !global.useCobblemonSpawnData() || index.isBiomeAllowed(species, biomeId, global);
    }

    private BiomeSpawnIndex currentBiomeIndex(LegendarySettings.Biomes settings) {
        BiomeSpawnIndex current = biomeIndex;
        if (settings.enabled() && settings.useCobblemonSpawnData() && !current.loaded()) {
            long now = System.currentTimeMillis();
            if (now >= nextBiomeRetryAtMillis) {
                refreshBiomeIndex("lazy biome lookup");
                current = biomeIndex;
            }
        }
        return current;
    }

    private void announce(SpawnResult result, Pokemon pokemon) {
        LegendarySettings.Announcement announcement = mod.configManager().settings().announcement();
        if (!announcement.enabled()) return;
        String hidden = mod.configManager().message("placeholders.hidden-coordinate", "hidden");
        String x = announcement.showCoordinates() ? Integer.toString(result.position().getX()) : hidden;
        String y = announcement.showCoordinates() ? Integer.toString(result.position().getY()) : hidden;
        String z = announcement.showCoordinates() ? Integer.toString(result.position().getZ()) : hidden;
        ResourceLocation biomeId = result.world().getBiome(result.position()).unwrapKey().map(key -> key.location()).orElse(null);
        String biomeIdentifier = biomeId == null ? "unknown" : biomeId.toString();
        String biomeName = TextUtil.humanizeResourcePath(biomeIdentifier);
        String rawMessage = TextUtil.trimOuterBlankLines(announcement.message());
        if (rawMessage.isBlank()) return;
        MutableComponent message = TextUtil.component(rawMessage, (placeholder, style) -> switch (placeholder.toLowerCase(Locale.ROOT)) {
            case "pokemon" -> pokemonNameComponent(pokemon, result.species().getName(), style, "spawn");
            case "level" -> net.minecraft.network.chat.Component.literal(Integer.toString(result.level())).withStyle(style);
            case "player" -> net.minecraft.network.chat.Component.literal(result.targetPlayer().getGameProfile().getName()).withStyle(style);
            case "dimension" -> net.minecraft.network.chat.Component.literal(result.world().dimension().location().toString()).withStyle(style);
            case "biome" -> net.minecraft.network.chat.Component.literal(biomeName).withStyle(style);
            case "biome_id" -> net.minecraft.network.chat.Component.literal(biomeIdentifier).withStyle(style);
            case "x" -> net.minecraft.network.chat.Component.literal(x).withStyle(style);
            case "y" -> net.minecraft.network.chat.Component.literal(y).withStyle(style);
            case "z" -> net.minecraft.network.chat.Component.literal(z).withStyle(style);
            default -> null;
        });
        broadcast(server.getPlayerList().getPlayers(), message);
    }

    private MutableComponent pokemonNameComponent(Pokemon pokemon, String fallbackName, net.minecraft.network.chat.Style style, String context) {
        return PokemonHoverUtil.nameComponent(mod.configManager(), pokemon, fallbackName, style, context);
    }

    private static void broadcast(Collection<ServerPlayer> players, MutableComponent message) {
        if (message == null || message.getString().isBlank()) return;
        for (ServerPlayer player : players) player.sendSystemMessage(message.copy());
    }

    private static boolean isWorldAllowed(LegendarySettings.Worlds global, LegendarySettings.PokemonOverride override, String dimension) {
        if (override.blockedWorlds().contains(dimension)) return false;
        if (override.worldMode() == LegendarySettings.OverrideMode.REPLACE) return override.allowedWorlds().contains(dimension);
        if (!isGlobalWorldAllowed(global, dimension)) return false;
        return override.allowedWorlds().isEmpty() || override.allowedWorlds().contains(dimension);
    }

    private static boolean isGlobalWorldAllowed(LegendarySettings.Worlds worlds, String dimension) {
        if (worlds.blockedWorlds().contains(dimension)) return false;
        return worlds.allowAllWorlds() || worlds.allowedWorlds().contains(dimension);
    }

    private static boolean isDangerousGround(BlockState state) {
        return state.is(Blocks.MAGMA_BLOCK) || state.is(Blocks.CACTUS) || state.is(Blocks.CAMPFIRE) || state.is(Blocks.SOUL_CAMPFIRE) || state.is(Blocks.SWEET_BERRY_BUSH) || state.is(Blocks.WITHER_ROSE);
    }
    private static boolean isDangerousSpawnBlock(BlockState state) {
        return state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE) || state.is(Blocks.POWDER_SNOW) || state.is(Blocks.SWEET_BERRY_BUSH) || state.is(Blocks.WITHER_ROSE);
    }
    private static String describe(RuntimeException exception) { String message = exception.getMessage(); return exception.getClass().getSimpleName() + (message == null || message.isBlank() ? "" : ": " + message); }
    private static boolean matchesRequiredLabel(Set<String> speciesLabels, List<String> required) { return required.isEmpty() || matchesAnyLabel(speciesLabels, required); }
    private static boolean matchesAnyLabel(Set<String> speciesLabels, List<String> wanted) {
        if (wanted.isEmpty() || speciesLabels == null || speciesLabels.isEmpty()) return false;
        for (String speciesLabel : speciesLabels) if (speciesLabel != null && wanted.contains(speciesLabel.toLowerCase(Locale.ROOT))) return true;
        return false;
    }
}
