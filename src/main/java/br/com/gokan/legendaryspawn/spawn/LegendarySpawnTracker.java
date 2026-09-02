package br.com.gokan.legendaryspawn.spawn;

import br.com.gokan.legendaryspawn.LegendarySpawn;
import br.com.gokan.legendaryspawn.config.LegendarySettings;
import br.com.gokan.legendaryspawn.scheduler.LegendaryScheduler;
import br.com.gokan.legendaryspawn.util.TextUtil;
import br.com.gokan.legendaryspawn.util.PokemonHoverUtil;
import br.com.gokan.legendaryspawn.util.TimeUtil;
import com.cobblemon.mod.common.CobblemonEntities;
import com.cobblemon.mod.common.api.entity.Despawner;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.events.battles.BattleFaintedEvent;
import com.cobblemon.mod.common.api.events.battles.BattleFledEvent;
import com.cobblemon.mod.common.api.events.pokemon.PokemonCapturedEvent;
import com.cobblemon.mod.common.api.reactive.ObservableSubscription;
import com.cobblemon.mod.common.battles.actor.PlayerBattleActor;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.Species;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

public final class LegendarySpawnTracker {

    private static final long RESTORE_LOG_COOLDOWN_MILLIS = 30_000L;
    private static final long BATTLE_TRANSITION_GRACE_MILLIS = 5_000L;
    private static final Despawner<PokemonEntity> NO_NATURAL_DESPAWN = new Despawner<>() {
        @Override public void beginTracking(PokemonEntity entity) {}
        @Override public boolean shouldDespawn(PokemonEntity entity) { return false; }
    };

    private final LegendarySpawn mod;
    private final MinecraftServer server;
    private final Map<UUID, TrackedLegend> tracked = new LinkedHashMap<>();
    private ObservableSubscription<PokemonCapturedEvent> captureSubscription;
    private ObservableSubscription<BattleFaintedEvent> faintedSubscription;
    private ObservableSubscription<BattleFledEvent> fledSubscription;
    private boolean started;

    public LegendarySpawnTracker(LegendarySpawn mod, MinecraftServer server) {
        this.mod = mod;
        this.server = server;
    }

    public void start() {
        if (started) return;
        try {
            captureSubscription = CobblemonEvents.POKEMON_CAPTURED.subscribe((Consumer<PokemonCapturedEvent>) this::onPokemonCapturedSafely);
            faintedSubscription = CobblemonEvents.BATTLE_FAINTED.subscribe((Consumer<BattleFaintedEvent>) this::onPokemonFaintedSafely);
            fledSubscription = CobblemonEvents.BATTLE_FLED.subscribe((Consumer<BattleFledEvent>) this::onBattleFledSafely);
            started = true;
        } catch (RuntimeException exception) {
            unsubscribeQuietly();
            started = false;
            LegendarySpawn.logRuntimeError("[Tracking] Could not subscribe to Cobblemon capture/battle events. Tracking was disabled to avoid duplication: " + describe(exception), exception);
        }
    }

    public boolean isStarted() { return started; }

    public void stop() {
        unsubscribeQuietly();
        for (TrackedLegend legend : tracked.values()) {
            releaseNaturalDespawnProtection(legend, currentEntity(legend));
            removeBossbar(legend);
        }
        tracked.clear();
        started = false;
    }

    private void unsubscribeQuietly() {
        if (captureSubscription != null) {
            try { captureSubscription.unsubscribe(); } catch (RuntimeException exception) { LegendarySpawn.logRuntimeError("[Tracking] Could not unsubscribe from POKEMON_CAPTURED cleanly: " + describe(exception), exception); }
            captureSubscription = null;
        }
        if (faintedSubscription != null) {
            try { faintedSubscription.unsubscribe(); } catch (RuntimeException exception) { LegendarySpawn.logRuntimeError("[Tracking] Could not unsubscribe from BATTLE_FAINTED cleanly: " + describe(exception), exception); }
            faintedSubscription = null;
        }
        if (fledSubscription != null) {
            try { fledSubscription.unsubscribe(); } catch (RuntimeException exception) { LegendarySpawn.logRuntimeError("[Tracking] Could not unsubscribe from BATTLE_FLED cleanly: " + describe(exception), exception); }
            fledSubscription = null;
        }
    }

    public boolean register(Pokemon pokemon, PokemonEntity entity, Species species, int level, UUID targetPlayerUuid, String targetPlayerName) {
        if (!started) {
            LegendarySpawn.logRuntimeWarning("[Tracking] " + species.getResourceIdentifier() + " spawned while tracking was unavailable.");
            return false;
        }
        TrackedLegend legend = null;
        try {
            long now = System.currentTimeMillis();
            TrackedLegend previous = tracked.remove(pokemon.getUuid());
            if (previous != null) {
                releaseNaturalDespawnProtection(previous, currentEntity(previous));
                removeBossbar(previous);
            }
            ResourceLocation biomeId = entity.level().getBiome(entity.blockPosition()).unwrapKey().map(key -> key.location()).orElse(null);
            String biomeIdentifier = biomeId == null ? "unknown" : biomeId.toString();
            String biomeName = TextUtil.humanizeResourcePath(biomeIdentifier);
            legend = new TrackedLegend(pokemon, species.getResourceIdentifier().toString(), species.getName(), level, now,
                    entity.level().dimension(), entity.getX(), entity.getY(), entity.getZ(), targetPlayerUuid, targetPlayerName, biomeIdentifier, biomeName);
            legend.currentEntityUuid = entity.getUUID();
            legend.currentEntity = entity;
            tracked.put(pokemon.getUuid(), legend);
            synchronizeProtection(legend, entity, now);
            synchronizeBossbar(legend, now);
            return true;
        } catch (RuntimeException exception) {
            TrackedLegend failed = tracked.remove(pokemon.getUuid());
            if (failed == null) failed = legend;
            if (failed != null) {
                try { releaseNaturalDespawnProtection(failed, entity); }
                catch (RuntimeException cleanupException) { LegendarySpawn.logRuntimeError("[Tracking] Failed to clean protection after registration error for " + species.getResourceIdentifier() + ": " + describe(cleanupException), cleanupException); }
                try { removeBossbar(failed); }
                catch (RuntimeException cleanupException) { LegendarySpawn.logRuntimeError("[Tracking] Failed to clean bossbar after registration error for " + species.getResourceIdentifier() + ": " + describe(cleanupException), cleanupException); }
            }
            LegendarySpawn.logRuntimeError("[Tracking] Could not register " + species.getResourceIdentifier() + " safely: " + describe(exception), exception);
            return false;
        }
    }

    public void tick(long now) {
        if (tracked.isEmpty()) return;
        Iterator<TrackedLegend> iterator = tracked.values().iterator();
        while (iterator.hasNext()) {
            TrackedLegend legend = iterator.next();
            try {
                tickLegend(legend, iterator, now);
            } catch (RuntimeException exception) {
                throttledTrackingError(legend, now, "Unexpected tracker error for " + legend.speciesId + ": " + describe(exception), exception);
                synchronizeBossbarSafely(legend, now);
            }
        }
    }

    private void tickLegend(TrackedLegend legend, Iterator<TrackedLegend> iterator, long now) {
        LegendarySettings.Protection protection = mod.configManager().settings().protection();
        boolean protectedNow = isWithinProtectionWindow(legend, protection, now);
        PokemonEntity lastKnownEntity = legend.currentEntity;
        if (lastKnownEntity != null && (lastKnownEntity.isDeadOrDying() || lastKnownEntity.getHealth() <= 0.0F)) {
            iterator.remove();
            completeKilled(legend, "something", lastKnownEntity);
            return;
        }
        PokemonEntity entity = currentEntity(legend);

        if (!protectedNow && protection.enabled() && !legend.protectionExpiredAnnounced) {
            legend.protectionExpiredAnnounced = true;
            releaseNaturalDespawnProtection(legend, entity);
            removeBossbar(legend);
            if (mod.configManager().settings().events().protectionExpired()) {
                announceEvent("events.protection-expired", legend, legend.targetPlayerName, "&eThe legendary &6{pokemon} &eis no longer protected from despawning.");
            }
        } else if (protectedNow) {
            // The countdown belongs to the tracked spawn, not to the current PokemonEntity instance.
            // Cobblemon can temporarily replace or detach the entity during battle transitions/flee.
            // Keeping the bossbar update independent prevents the timer from freezing in that state.
            synchronizeBossbarSafely(legend, now);
        } else {
            removeBossbar(legend);
        }

        if (entity != null && entity.isAlive() && !entity.isRemoved()) {
            legend.battleTransitionGraceUntilMillis = 0L;
            rememberPosition(legend, entity);
            synchronizeProtection(legend, entity, now);
            return;
        }

        if (protectedNow && protection.restoreIfRemoved()) {
            if (now < legend.battleTransitionGraceUntilMillis) {
                return;
            }
            restore(legend, protection, now);
            return;
        }

        ServerLevel lastWorld = server.getLevel(legend.worldKey);
        BlockPos lastPos = blockPosition(legend);
        if (lastWorld != null && !lastWorld.hasChunk(lastPos.getX() >> 4, lastPos.getZ() >> 4)) {
            // Unloaded chunks are not treated as a despawn. The bossbar countdown is intentionally
            // kept running until protection expires; no chunk is force-loaded just for tracking.
            return;
        }

        removeBossbar(legend);
        if (mod.configManager().settings().events().despawned()) {
            announceEvent("events.despawned", legend, legend.targetPlayerName, "&7The wild legendary &f{pokemon} &7has disappeared.");
        }
        LegendarySpawn.logRuntimeInfo("[Tracking] " + legend.speciesId + " disappeared after protection ended.");
        iterator.remove();
    }

    public void onLivingEntityDeath(LivingEntity entity, DamageSource damageSource) {
        if (!(entity instanceof PokemonEntity pokemonEntity)) return;
        TrackedLegend legend = tracked.remove(pokemonEntity.getPokemon().getUuid());
        if (legend == null) return;
        String killer = resolveKillerName(damageSource);
        completeKilled(legend, killer, pokemonEntity);
    }

    private void completeKilled(TrackedLegend legend, String killer, PokemonEntity entity) {
        removeBossbarSafely(legend);
        try {
            releaseNaturalDespawnProtection(legend, entity);
        } catch (RuntimeException exception) {
            LegendarySpawn.logRuntimeError("[Death] Could not release despawn protection for " + legend.speciesId + ": " + describe(exception), exception);
        }
        if (mod.configManager().settings().events().killed()) {
            announceEvent("events.killed", legend, killer, "&cThe legendary &e{pokemon} &cwas killed by &f{player}&c!");
        }
        if (mod.scheduler() != null) {
            mod.scheduler().onTrackedLegendCompleted(legend.speciesId, LegendaryScheduler.CompletionType.DEFEATED);
        }
        LegendarySpawn.logRuntimeInfo("[Death] " + legend.speciesId + " was killed by " + killer + ". It will not be restored by spawn protection.");
    }

    private String resolveKillerName(DamageSource damageSource) {
        Entity attacker = damageSource == null ? null : damageSource.getEntity();
        if (attacker == null && damageSource != null) attacker = damageSource.getDirectEntity();
        if (attacker instanceof ServerPlayer player) return player.getGameProfile().getName();
        if (attacker != null) {
            String name = attacker.getName().getString();
            if (!name.isBlank()) return name;
        }
        return mod.configManager().message("events.killed.unknown-killer", "something");
    }

    public void onSettingsReloaded() {
        long now = System.currentTimeMillis();
        for (TrackedLegend legend : tracked.values()) {
            PokemonEntity entity = currentEntity(legend);
            if (entity != null && entity.isAlive() && !entity.isRemoved()) synchronizeProtection(legend, entity, now);
            synchronizeBossbarSafely(legend, now);
        }
    }

    public int trackedCount() { return tracked.size(); }

    public int protectedCount() {
        long now = System.currentTimeMillis();
        LegendarySettings.Protection settings = mod.configManager().settings().protection();
        int count = 0;
        for (TrackedLegend legend : tracked.values()) if (isWithinProtectionWindow(legend, settings, now)) count++;
        return count;
    }

    private void onPokemonCapturedSafely(PokemonCapturedEvent event) {
        try { onPokemonCaptured(event); }
        catch (RuntimeException exception) { LegendarySpawn.logRuntimeError("[Capture] Failed to process POKEMON_CAPTURED: " + describe(exception), exception); }
    }

    private void onPokemonFaintedSafely(BattleFaintedEvent event) {
        try { onPokemonFainted(event); }
        catch (RuntimeException exception) { LegendarySpawn.logRuntimeError("[Battle] Failed to process BATTLE_FAINTED: " + describe(exception), exception); }
    }

    private void onBattleFledSafely(BattleFledEvent event) {
        try { onBattleFled(event); }
        catch (RuntimeException exception) { LegendarySpawn.logRuntimeError("[Battle] Failed to process BATTLE_FLED: " + describe(exception), exception); }
    }

    private void onBattleFled(BattleFledEvent event) {
        long now = System.currentTimeMillis();
        for (var actor : event.getBattle().getActors()) {
            if (!(actor instanceof com.cobblemon.mod.common.battles.actor.PokemonBattleActor pokemonActor)) continue;
            UUID pokemonUuid = pokemonActor.getPokemon().getOriginalPokemon().getUuid();
            TrackedLegend legend = tracked.get(pokemonUuid);
            if (legend == null) continue;
            legend.battleTransitionGraceUntilMillis = now + BATTLE_TRANSITION_GRACE_MILLIS;
            synchronizeBossbarSafely(legend, now);
            if (mod.configManager().settings().debug()) {
                LegendarySpawn.logRuntimeInfo("[Debug] Battle flee transition detected for " + legend.speciesId + "; restore checks are delayed for 5 seconds while the bossbar countdown keeps running.");
            }
        }
    }

    private void onPokemonCaptured(PokemonCapturedEvent event) {
        TrackedLegend legend = tracked.remove(event.getPokemon().getUuid());
        if (legend == null) return;
        removeBossbarSafely(legend);
        try {
            releaseNaturalDespawnProtection(legend, currentEntity(legend));
        } catch (RuntimeException exception) {
            LegendarySpawn.logRuntimeError("[Capture] Could not release despawn protection for " + legend.speciesId + ": " + describe(exception), exception);
        }
        ServerPlayer player = event.getPlayer();
        if (mod.configManager().settings().events().captured()) announceEvent("events.captured", legend, player.getGameProfile().getName(), "&a{player} captured the legendary &e{pokemon}&a!");
        if (mod.scheduler() != null) mod.scheduler().onTrackedLegendCompleted(legend.speciesId, LegendaryScheduler.CompletionType.CAPTURED);
        LegendarySpawn.logRuntimeInfo("[Capture] " + player.getGameProfile().getName() + " captured " + legend.speciesId + ".");
    }

    private void onPokemonFainted(BattleFaintedEvent event) {
        UUID pokemonUuid = event.getKilled().getOriginalPokemon().getUuid();
        TrackedLegend legend = tracked.remove(pokemonUuid);
        if (legend == null) return;
        removeBossbarSafely(legend);
        try {
            releaseNaturalDespawnProtection(legend, currentEntity(legend));
        } catch (RuntimeException exception) {
            LegendarySpawn.logRuntimeError("[Battle] Could not release despawn protection for " + legend.speciesId + ": " + describe(exception), exception);
        }
        String players = defeatingPlayers(event);
        if (mod.configManager().settings().events().defeated()) announceEvent("events.defeated", legend, players, "&c{player} defeated the legendary &e{pokemon}&c!");
        if (mod.scheduler() != null) mod.scheduler().onTrackedLegendCompleted(legend.speciesId, LegendaryScheduler.CompletionType.DEFEATED);
        LegendarySpawn.logRuntimeInfo("[Battle] " + players + " defeated " + legend.speciesId + ".");
    }

    private String defeatingPlayers(BattleFaintedEvent event) {
        List<String> names = new ArrayList<>();
        try {
            for (var actor : event.getKilled().getActor().getSide().getOppositeSide().getActors()) {
                if (actor instanceof PlayerBattleActor playerActor) {
                    ServerPlayer player = playerActor.getEntity();
                    String name = player == null ? playerActor.getName().getString() : player.getGameProfile().getName();
                    if (!name.isBlank() && !names.contains(name)) names.add(name);
                }
            }
        } catch (RuntimeException exception) {
            LegendarySpawn.logRuntimeError("[Battle] Could not resolve defeating players: " + describe(exception), exception);
        }
        return names.isEmpty() ? "A player" : String.join(", ", names);
    }

    private void announceEvent(String path, TrackedLegend legend, String playerName, String fallback) {
        String message = mod.configManager().message(path + ".message", fallback);
        if (message.isBlank()) return;
        BlockPos pos = blockPosition(legend);
        boolean showCoordinates = mod.configManager().settings().announcement().showCoordinates();
        String hidden = mod.configManager().message("placeholders.hidden-coordinate", "hidden");
        String rawMessage = TextUtil.trimOuterBlankLines(message);
        if (rawMessage.isBlank()) return;
        String context = path.startsWith("events.") ? path.substring("events.".length()) : path;
        MutableComponent formatted = TextUtil.component(rawMessage, (placeholder, style) -> switch (placeholder.toLowerCase(java.util.Locale.ROOT)) {
            case "pokemon" -> pokemonNameComponent(legend, style, context);
            case "level" -> net.minecraft.network.chat.Component.literal(Integer.toString(legend.level)).withStyle(style);
            case "player" -> net.minecraft.network.chat.Component.literal(playerName == null ? "" : playerName).withStyle(style);
            case "dimension" -> net.minecraft.network.chat.Component.literal(legend.worldKey.location().toString()).withStyle(style);
            case "biome" -> net.minecraft.network.chat.Component.literal(legend.biomeName).withStyle(style);
            case "biome_id" -> net.minecraft.network.chat.Component.literal(legend.biomeId).withStyle(style);
            case "x" -> net.minecraft.network.chat.Component.literal(showCoordinates ? Integer.toString(pos.getX()) : hidden).withStyle(style);
            case "y" -> net.minecraft.network.chat.Component.literal(showCoordinates ? Integer.toString(pos.getY()) : hidden).withStyle(style);
            case "z" -> net.minecraft.network.chat.Component.literal(showCoordinates ? Integer.toString(pos.getZ()) : hidden).withStyle(style);
            default -> null;
        });
        broadcast(server.getPlayerList().getPlayers(), formatted);
    }

    private MutableComponent pokemonNameComponent(TrackedLegend legend, net.minecraft.network.chat.Style style, String context) {
        return PokemonHoverUtil.nameComponent(mod.configManager(), legend.pokemon, legend.pokemonName, style, context);
    }

    private static void broadcast(Collection<ServerPlayer> players, MutableComponent message) {
        if (message == null || message.getString().isBlank()) return;
        for (ServerPlayer player : players) player.sendSystemMessage(message.copy());
    }

    private void synchronizeBossbarSafely(TrackedLegend legend, long now) {
        try {
            synchronizeBossbar(legend, now);
        } catch (RuntimeException exception) {
            throttledTrackingError(legend, now, "Bossbar update failed for " + legend.speciesId + ": " + describe(exception), exception);
        }
    }

    private void synchronizeBossbar(TrackedLegend legend, long now) {
        LegendarySettings.Bossbar settings = mod.configManager().settings().bossbar();
        LegendarySettings.Protection protection = mod.configManager().settings().protection();
        if (!settings.enabled() || !isWithinProtectionWindow(legend, protection, now)) {
            removeBossbar(legend);
            return;
        }

        long duration = protection.durationMinutes() * 60_000L;
        long remaining = Math.max(0L, legend.spawnedAtMillis + duration - now);
        float progress = duration <= 0L ? 0.0F : (float) Math.max(0.0D, Math.min(1.0D, (double) remaining / duration));
        if (legend.bossbar == null) {
            legend.bossbar = new ServerBossEvent(TextUtil.component(""), bossbarColor(settings.color()), bossbarOverlay(settings.overlay()));
        }

        String title = settings.title()
                .replace("{pokemon}", legend.pokemonName)
                .replace("{level}", Integer.toString(legend.level))
                .replace("{player}", legend.targetPlayerName)
                .replace("{time}", TimeUtil.formatDuration(remaining));
        legend.bossbar.setName(TextUtil.component(title));
        legend.bossbar.setProgress(progress);
        synchronizeBossbarViewers(legend, settings);
    }

    private void synchronizeBossbarViewers(TrackedLegend legend, LegendarySettings.Bossbar settings) {
        if (legend.bossbar == null) return;
        java.util.Set<ServerPlayer> wanted = new java.util.HashSet<>();
        if (settings.showToAllPlayers()) {
            wanted.addAll(server.getPlayerList().getPlayers());
        } else {
            ServerPlayer target = server.getPlayerList().getPlayer(legend.targetPlayerUuid);
            if (target != null) wanted.add(target);
        }

        java.util.Set<ServerPlayer> currentViewers = new java.util.HashSet<>(legend.bossbar.getPlayers());
        for (ServerPlayer current : currentViewers) {
            if (!wanted.contains(current)) legend.bossbar.removePlayer(current);
        }
        for (ServerPlayer player : wanted) {
            if (!currentViewers.contains(player)) legend.bossbar.addPlayer(player);
        }
    }

    private static BossEvent.BossBarColor bossbarColor(String raw) {
        return switch (raw) {
            case "pink" -> BossEvent.BossBarColor.PINK;
            case "blue" -> BossEvent.BossBarColor.BLUE;
            case "red" -> BossEvent.BossBarColor.RED;
            case "green" -> BossEvent.BossBarColor.GREEN;
            case "purple" -> BossEvent.BossBarColor.PURPLE;
            case "white" -> BossEvent.BossBarColor.WHITE;
            default -> BossEvent.BossBarColor.YELLOW;
        };
    }

    private static BossEvent.BossBarOverlay bossbarOverlay(String raw) {
        return switch (raw) {
            case "notched_6" -> BossEvent.BossBarOverlay.NOTCHED_6;
            case "notched_10" -> BossEvent.BossBarOverlay.NOTCHED_10;
            case "notched_12" -> BossEvent.BossBarOverlay.NOTCHED_12;
            case "notched_20" -> BossEvent.BossBarOverlay.NOTCHED_20;
            default -> BossEvent.BossBarOverlay.PROGRESS;
        };
    }

    private static void removeBossbar(TrackedLegend legend) {
        if (legend.bossbar != null) {
            legend.bossbar.removeAllPlayers();
            legend.bossbar.setVisible(false);
            legend.bossbar = null;
        }
    }

    private static void removeBossbarSafely(TrackedLegend legend) {
        try {
            removeBossbar(legend);
        } catch (RuntimeException exception) {
            legend.bossbar = null;
            LegendarySpawn.logRuntimeError("[Bossbar] Failed to remove bossbar for " + legend.speciesId + ": " + describe(exception), exception);
        }
    }

    private void synchronizeProtection(TrackedLegend legend, PokemonEntity entity, long now) {
        LegendarySettings.Protection settings = mod.configManager().settings().protection();
        if (isWithinProtectionWindow(legend, settings, now) && settings.preventNaturalDespawn()) applyNaturalDespawnProtection(legend, entity);
        else releaseNaturalDespawnProtection(legend, entity);
    }

    private boolean restore(TrackedLegend legend, LegendarySettings.Protection settings, long now) {
        ServerLevel world = server.getLevel(legend.worldKey);
        if (world == null) { throttledRestoreWarning(legend, now, "world '" + legend.worldKey.location() + "' is not loaded"); return false; }
        BlockPos pos = blockPosition(legend);
        if (!world.getWorldBorder().isWithinBounds(pos)) { throttledRestoreWarning(legend, now, "last position is outside the world border"); return false; }
        if (!world.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) return false;
        try {
            PokemonEntity entity = new PokemonEntity(world, legend.pokemon, CobblemonEntities.POKEMON);
            entity.setCountsTowardsSpawnCap(mod.configManager().settings().spawn().countTowardsSpawnCap());
            entity.setPos(legend.x, legend.y, legend.z);
            if (!world.noCollision(entity, entity.getBoundingBox())) { throttledRestoreWarning(legend, now, "the last position is currently obstructed"); return false; }
            if (!world.addFreshEntity(entity)) { throttledRestoreWarning(legend, now, "Minecraft rejected the replacement entity"); return false; }
            legend.currentEntityUuid = entity.getUUID();
            legend.currentEntity = entity;
            legend.naturalDespawnProtected = false;
            legend.originalDespawner = null;
            rememberPosition(legend, entity);
            synchronizeProtection(legend, entity, now);
            LegendarySpawn.logRuntimeWarning("[Protection] Restored " + legend.speciesId + " after its entity was removed during protection.");
            return true;
        } catch (RuntimeException exception) {
            if (now - legend.lastRestoreLogMillis >= RESTORE_LOG_COOLDOWN_MILLIS) {
                legend.lastRestoreLogMillis = now;
                LegendarySpawn.logRuntimeError("[Protection] Failed to restore " + legend.speciesId + ": " + describe(exception), exception);
            }
            return false;
        }
    }

    private void applyNaturalDespawnProtection(TrackedLegend legend, PokemonEntity entity) {
        if (legend.naturalDespawnProtected && legend.currentEntityUuid.equals(entity.getUUID())) return;
        if (legend.naturalDespawnProtected) releaseNaturalDespawnProtection(legend, null);
        legend.currentEntityUuid = entity.getUUID();
        legend.originalDespawner = entity.getDespawner();
        entity.setDespawner(NO_NATURAL_DESPAWN);
        NO_NATURAL_DESPAWN.beginTracking(entity);
        legend.naturalDespawnProtected = true;
    }

    private void releaseNaturalDespawnProtection(TrackedLegend legend, PokemonEntity entity) {
        if (!legend.naturalDespawnProtected) return;
        if (entity != null && legend.currentEntityUuid.equals(entity.getUUID()) && legend.originalDespawner != null) {
            try { entity.setDespawner(legend.originalDespawner); legend.originalDespawner.beginTracking(entity); }
            catch (RuntimeException exception) { LegendarySpawn.logRuntimeError("[Protection] Could not restore normal Cobblemon despawner for " + legend.speciesId + ": " + describe(exception), exception); }
        }
        legend.originalDespawner = null;
        legend.naturalDespawnProtected = false;
    }

    private PokemonEntity currentEntity(TrackedLegend legend) {
        PokemonEntity current = legend.currentEntity;
        if (current != null && current.isAlive() && !current.isRemoved()) return current;
        PokemonEntity pokemonEntity = legend.pokemon.getEntity();
        if (pokemonEntity != null && pokemonEntity.isAlive() && !pokemonEntity.isRemoved()) {
            if (!pokemonEntity.getUUID().equals(legend.currentEntityUuid)) {
                legend.currentEntityUuid = pokemonEntity.getUUID();
                legend.naturalDespawnProtected = false;
                legend.originalDespawner = null;
            }
            legend.currentEntity = pokemonEntity;
            return pokemonEntity;
        }
        return null;
    }

    private void rememberPosition(TrackedLegend legend, PokemonEntity entity) {
        legend.worldKey = entity.level().dimension();
        legend.x = entity.getX(); legend.y = entity.getY(); legend.z = entity.getZ();
        legend.currentEntityUuid = entity.getUUID(); legend.currentEntity = entity;
    }

    private static boolean isWithinProtectionWindow(TrackedLegend legend, LegendarySettings.Protection settings, long now) {
        if (!settings.enabled()) return false;
        return now < legend.spawnedAtMillis + settings.durationMinutes() * 60_000L;
    }

    private void throttledRestoreWarning(TrackedLegend legend, long now, String reason) {
        if (now - legend.lastRestoreLogMillis < RESTORE_LOG_COOLDOWN_MILLIS) return;
        legend.lastRestoreLogMillis = now;
        LegendarySpawn.logRuntimeWarning("[Protection] " + legend.speciesId + " is missing but cannot be restored yet because " + reason + ".");
    }

    private void throttledTrackingError(TrackedLegend legend, long now, String message, RuntimeException exception) {
        if (now - legend.lastTrackingErrorLogMillis < RESTORE_LOG_COOLDOWN_MILLIS) return;
        legend.lastTrackingErrorLogMillis = now;
        LegendarySpawn.logRuntimeError("[Tracking] " + message, exception);
    }

    private static BlockPos blockPosition(TrackedLegend legend) { return new BlockPos((int) Math.floor(legend.x), (int) Math.floor(legend.y), (int) Math.floor(legend.z)); }
    private static String describe(RuntimeException exception) { String message = exception.getMessage(); return exception.getClass().getSimpleName() + (message == null || message.isBlank() ? "" : ": " + message); }

    private static final class TrackedLegend {
        private final Pokemon pokemon;
        private final String speciesId;
        private final String pokemonName;
        private final int level;
        private final long spawnedAtMillis;
        private final UUID targetPlayerUuid;
        private final String targetPlayerName;
        private final String biomeId;
        private final String biomeName;
        private ResourceKey<Level> worldKey;
        private UUID currentEntityUuid;
        private PokemonEntity currentEntity;
        private Despawner<PokemonEntity> originalDespawner;
        private ServerBossEvent bossbar;
        private boolean naturalDespawnProtected;
        private boolean protectionExpiredAnnounced;
        private double x;
        private double y;
        private double z;
        private long lastRestoreLogMillis;
        private long lastTrackingErrorLogMillis;
        private long battleTransitionGraceUntilMillis;

        private TrackedLegend(Pokemon pokemon, String speciesId, String pokemonName, int level, long spawnedAtMillis,
                              ResourceKey<Level> worldKey, double x, double y, double z, UUID targetPlayerUuid, String targetPlayerName,
                              String biomeId, String biomeName) {
            this.pokemon = pokemon; this.speciesId = speciesId; this.pokemonName = pokemonName; this.level = level;
            this.spawnedAtMillis = spawnedAtMillis; this.worldKey = worldKey; this.x = x; this.y = y; this.z = z;
            this.targetPlayerUuid = targetPlayerUuid; this.targetPlayerName = targetPlayerName;
            this.biomeId = biomeId; this.biomeName = biomeName;
        }
    }
}
