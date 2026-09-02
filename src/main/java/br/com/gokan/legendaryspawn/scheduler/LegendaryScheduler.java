package br.com.gokan.legendaryspawn.scheduler;

import br.com.gokan.core.gkapi.modules.config.Config;
import br.com.gokan.legendaryspawn.LegendarySpawn;
import br.com.gokan.legendaryspawn.config.LegendarySettings;
import br.com.gokan.legendaryspawn.spawn.LegendarySpawner;
import br.com.gokan.legendaryspawn.spawn.SpawnResult;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public final class LegendaryScheduler {

    public enum CompletionType { CAPTURED, DEFEATED }

    private final LegendarySpawn mod;
    private final MinecraftServer server;
    private final LegendarySpawner spawner;
    private final Map<String, Long> speciesCooldownUntil = new LinkedHashMap<>();
    private long globalCooldownUntil;
    private long nextAttemptEpochMillis;
    private long pausedRemainingMillis;
    private boolean paused;
    private boolean initialized;

    public LegendaryScheduler(LegendarySpawn mod, MinecraftServer server) {
        this.mod = mod;
        this.server = server;
        this.spawner = new LegendarySpawner(mod, server);
    }

    public void initialize() {
        if (initialized) return;
        initialized = true;
        Config state = mod.configManager().state();
        nextAttemptEpochMillis = Math.max(0L, state.getLong("timer.next-attempt-epoch-millis", 0L));
        pausedRemainingMillis = Math.max(0L, state.getLong("timer.paused-remaining-millis", 0L));
        paused = state.getBoolean("timer.paused", false);
        globalCooldownUntil = mod.configManager().globalCooldownUntil();
        speciesCooldownUntil.clear();
        speciesCooldownUntil.putAll(mod.configManager().speciesCooldownState());
        cleanupCooldowns(System.currentTimeMillis(), false);

        long now = System.currentTimeMillis();
        LegendarySettings settings = mod.configManager().settings();
        if (!mod.configManager().isHealthy()) return;
        if (!settings.enabled()) {
            if (!paused) pause(now);
            return;
        }
        if (paused) {
            if (settings.pauseTimerWhenBelowMinimumPlayers() && !hasEnoughPlayers()) {
                saveState();
                return;
            }
            resume(now);
            return;
        }
        if (settings.pauseTimerWhenBelowMinimumPlayers() && !hasEnoughPlayers()) pause(now);
        else if (nextAttemptEpochMillis <= 0L) scheduleNewInterval(now);
    }

    public void onServerStarted() {
        if (!mod.configManager().isHealthy()) return;
        spawner.startTracking();
        spawner.startBiomeTracking();
        if (mod.configManager().settings().debug()) LegendarySpawn.logRuntimeInfo("[Debug] Legendary scheduler started. " + statusLine());
    }

    public void tick() {
        if (!initialized || !mod.configManager().isHealthy()) return;
        LegendarySettings settings = mod.configManager().settings();
        long now = System.currentTimeMillis();
        spawner.tickTracking(now);
        cleanupCooldowns(now, true);

        if (!settings.enabled()) {
            if (!paused) pause(now);
            return;
        }
        boolean enoughPlayers = hasEnoughPlayers();
        if (settings.pauseTimerWhenBelowMinimumPlayers()) {
            if (!enoughPlayers) {
                if (!paused) pause(now);
                return;
            }
            if (paused) resume(now);
        } else {
            if (paused) resume(now);
            if (!enoughPlayers) return;
        }
        if (nextAttemptEpochMillis <= 0L) {
            scheduleNewInterval(now);
            return;
        }
        if (now < nextAttemptEpochMillis) return;
        if (!spawner.isTrackingAvailable()) {
            debug("Timer is due, but Cobblemon event/protection tracking is unavailable. The automatic roll is being held.");
            return;
        }
        if (isGlobalCooldownActive(now)) {
            debug("Timer is due, but global post-event cooldown is active for " + (globalCooldownUntil - now) + " ms.");
            return;
        }
        if (activeLimitReached(false)) {
            debug("Timer is due, but active legendary limit is currently reached.");
            return;
        }
        if (spawner.validSpecies(false).isEmpty()) {
            debug("Timer is due, but no species/player combination is currently eligible (cooldown, world or pool filters). The roll is being held.");
            return;
        }
        executeAutomaticRoll(now);
    }

    public RollResult rollNow() {
        if (!initialized || !mod.configManager().isHealthy()) return RollResult.notRolled("LegendarySpawn configuration is not valid yet.");
        long now = System.currentTimeMillis();
        if (!spawner.isTrackingAvailable()) return RollResult.notRolled("Cobblemon event/protection tracking is unavailable. The roll was not spent.");
        if (!hasEnoughPlayers()) return RollResult.notRolled("Not enough players are online. The configured minimum is " + mod.configManager().settings().minimumOnlinePlayers() + ".");
        if (isGlobalCooldownActive(now)) return RollResult.notRolled("Global post-event cooldown is active for " + (globalCooldownUntil - now) / 1000L + "s.");
        if (activeLimitReached(false)) return RollResult.notRolled("The active legendary limit is currently reached.");
        if (spawner.validSpecies(false).isEmpty()) return RollResult.notRolled("No Pokemon/player combination is currently eligible after cooldown, world and pool filters.");
        return performChanceRoll(false);
    }

    public SpawnResult forceSpawn() {
        if (!initialized || !mod.configManager().isHealthy()) return SpawnResult.failure("LegendarySpawn configuration is not valid yet.");
        if (!spawner.isTrackingAvailable()) return SpawnResult.failure("Cobblemon event/protection tracking is unavailable. Spawn was cancelled to avoid an untracked legendary.");
        if (activeLimitReached(true)) return SpawnResult.failure("The active legendary limit is currently reached and limits.yml applies it to force spawns.");
        try {
            SpawnResult result = spawner.spawnRandom(true);
            if (result.success()) recordSpeciesSpawn(result.species().getResourceIdentifier().toString());
            return result;
        } catch (RuntimeException exception) {
            LegendarySpawn.logRuntimeError("[Spawn] Unexpected error while forcing a spawn: " + describe(exception), exception);
            return SpawnResult.failure("Unexpected spawn error. Check the server log for details.");
        }
    }

    public void onTrackedLegendCompleted(String speciesId, CompletionType type) {
        LegendarySettings.Cooldowns settings = mod.configManager().settings().cooldowns();
        if (!settings.globalAfterEventEnabled()) return;
        int minutes = type == CompletionType.CAPTURED ? settings.capturedMinutes() : settings.defeatedMinutes();
        if (minutes <= 0) return;
        long until = System.currentTimeMillis() + minutes * 60_000L;
        globalCooldownUntil = Math.max(globalCooldownUntil, until);
        saveCooldowns();
        debug("Global cooldown started after " + type.name().toLowerCase() + ": " + minutes + " minutes.");
    }

    public boolean isSpeciesAvailable(String speciesId) {
        if (!mod.configManager().settings().cooldowns().speciesEnabled()) return true;
        return speciesCooldownUntil.getOrDefault(speciesId.toLowerCase(), 0L) <= System.currentTimeMillis();
    }

    public long speciesCooldownRemaining(String speciesId) {
        return Math.max(0L, speciesCooldownUntil.getOrDefault(speciesId.toLowerCase(), 0L) - System.currentTimeMillis());
    }

    public long globalCooldownRemainingMillis() {
        return Math.max(0L, globalCooldownUntil - System.currentTimeMillis());
    }

    public long reschedule() {
        if (!initialized || !mod.configManager().isHealthy()) return 0L;
        long duration = randomIntervalMillis();
        if (paused) {
            pausedRemainingMillis = duration;
            nextAttemptEpochMillis = 0L;
        } else {
            nextAttemptEpochMillis = System.currentTimeMillis() + duration;
            pausedRemainingMillis = 0L;
        }
        saveState();
        debug("Timer manually rescheduled to " + duration + " ms.");
        return duration;
    }

    public void onSettingsReloaded() {
        if (!initialized) initialize();
        if (!mod.configManager().isHealthy()) return;
        spawner.startTracking();
        spawner.startBiomeTracking();
        spawner.onTrackingSettingsReloaded();
        cleanupCooldowns(System.currentTimeMillis(), true);
        LegendarySettings settings = mod.configManager().settings();
        long now = System.currentTimeMillis();
        if (!settings.enabled()) {
            if (!paused) pause(now);
            return;
        }
        if (paused) {
            if (settings.pauseTimerWhenBelowMinimumPlayers() && !hasEnoughPlayers()) {
                saveState();
                return;
            }
            resume(now);
            return;
        }
        if (settings.pauseTimerWhenBelowMinimumPlayers() && !hasEnoughPlayers()) pause(now);
    }

    public void shutdown() {
        spawner.stopBiomeTracking();
        spawner.stopTracking();
        if (!initialized || !mod.configManager().isHealthy()) return;
        LegendarySettings settings = mod.configManager().settings();
        if ((!settings.enabled() || settings.pauseTimerWhenBelowMinimumPlayers()) && !paused) pause(System.currentTimeMillis());
        else saveState();
        saveCooldowns();
    }

    public void onLivingEntityDeath(LivingEntity entity, DamageSource damageSource) {
        spawner.onLivingEntityDeath(entity, damageSource);
    }

    public int validSpeciesCount() { return spawner.validSpecies(false).size(); }
    public int eligiblePlayerCount() { return spawner.eligiblePlayers().size(); }
    public int trackedLegendCount() { return spawner.trackedLegendCount(); }
    public int protectedLegendCount() { return spawner.protectedLegendCount(); }
    public int onlinePlayerCount() { return server.getPlayerList().getPlayerCount(); }
    public boolean isPaused() { return paused; }
    public long remainingMillis() { return paused ? Math.max(0L, pausedRemainingMillis) : Math.max(0L, nextAttemptEpochMillis - System.currentTimeMillis()); }
    public long nextAttemptEpochMillis() { return paused ? 0L : nextAttemptEpochMillis; }
    public String statusLine() { return "paused=" + paused + ", remaining=" + remainingMillis() + "ms, pool=" + validSpeciesCount(); }

    private void executeAutomaticRoll(long now) {
        try {
            RollResult result = performChanceRoll(true);
            debug("Automatic roll: " + result.message());
        } catch (RuntimeException exception) {
            LegendarySpawn.logRuntimeError("[Scheduler] Automatic roll failed unexpectedly: " + describe(exception), exception);
        } finally {
            scheduleNewInterval(Math.max(now, System.currentTimeMillis()));
        }
    }

    private RollResult performChanceRoll(boolean automatic) {
        double chance = mod.configManager().settings().spawnChance();
        double value = ThreadLocalRandom.current().nextDouble();
        if (value >= chance) return RollResult.failedChance(value, chance, automatic);
        SpawnResult spawn;
        try {
            spawn = spawner.spawnRandom(false);
        } catch (RuntimeException exception) {
            LegendarySpawn.logRuntimeError("[Spawn] Chance passed, but an unexpected spawn error occurred: " + describe(exception), exception);
            return RollResult.spawnFailed(value, chance, "Unexpected spawn error. Check the server log for details.", automatic);
        }
        if (!spawn.success()) {
            LegendarySpawn.logRuntimeWarning("[Spawn] Legendary roll passed, but spawn failed: " + spawn.reason());
            return RollResult.spawnFailed(value, chance, spawn.reason(), automatic);
        }
        recordSpeciesSpawn(spawn.species().getResourceIdentifier().toString());
        LegendarySpawn.logRuntimeInfo("[Spawn] Spawned " + spawn.species().getResourceIdentifier() + " level " + spawn.level()
                + " near " + spawn.targetPlayer().getGameProfile().getName() + " at " + spawn.position().getX() + ", " + spawn.position().getY() + ", " + spawn.position().getZ());
        return RollResult.spawned(value, chance, spawn, automatic);
    }

    private void recordSpeciesSpawn(String speciesId) {
        LegendarySettings.Cooldowns settings = mod.configManager().settings().cooldowns();
        if (!settings.speciesEnabled()) return;
        String id = speciesId.toLowerCase();
        int minutes = settings.speciesOverrides().getOrDefault(id, settings.defaultSpeciesMinutes());
        if (minutes <= 0) {
            speciesCooldownUntil.remove(id);
        } else {
            speciesCooldownUntil.put(id, System.currentTimeMillis() + minutes * 60_000L);
        }
        saveCooldowns();
    }

    private boolean activeLimitReached(boolean force) {
        LegendarySettings.Limits limits = mod.configManager().settings().limits();
        if (!limits.activeLimitEnabled()) return false;
        if (force && !limits.applyToForce()) return false;
        return trackedLegendCount() >= limits.maxActive();
    }

    private boolean isGlobalCooldownActive(long now) {
        return mod.configManager().settings().cooldowns().globalAfterEventEnabled() && globalCooldownUntil > now;
    }

    private void cleanupCooldowns(long now, boolean persist) {
        boolean changed = false;
        Iterator<Map.Entry<String, Long>> iterator = speciesCooldownUntil.entrySet().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().getValue() <= now) {
                iterator.remove();
                changed = true;
            }
        }
        if (globalCooldownUntil <= now && globalCooldownUntil != 0L) {
            globalCooldownUntil = 0L;
            changed = true;
        }
        if (changed && persist) saveCooldowns();
    }

    private boolean hasEnoughPlayers() { return onlinePlayerCount() >= mod.configManager().settings().minimumOnlinePlayers(); }

    private long randomIntervalMillis() {
        LegendarySettings.Interval interval = mod.configManager().settings().interval();
        long min = interval.minMinutes() * 60_000L;
        long max = interval.maxMinutes() * 60_000L;
        return min == max ? min : ThreadLocalRandom.current().nextLong(min, max + 1L);
    }

    private void scheduleNewInterval(long now) {
        long duration = randomIntervalMillis();
        nextAttemptEpochMillis = now + duration;
        pausedRemainingMillis = 0L;
        paused = false;
        saveState();
    }

    private void pause(long now) {
        if (paused) return;
        pausedRemainingMillis = nextAttemptEpochMillis <= 0L ? randomIntervalMillis() : Math.max(0L, nextAttemptEpochMillis - now);
        nextAttemptEpochMillis = 0L;
        paused = true;
        saveState();
    }

    private void resume(long now) {
        if (!paused) return;
        long remaining = pausedRemainingMillis > 0L ? pausedRemainingMillis : randomIntervalMillis();
        nextAttemptEpochMillis = now + remaining;
        pausedRemainingMillis = 0L;
        paused = false;
        saveState();
    }

    private void saveState() { mod.configManager().saveState(nextAttemptEpochMillis, paused, pausedRemainingMillis); }
    private void saveCooldowns() { mod.configManager().saveCooldownState(globalCooldownUntil, Map.copyOf(speciesCooldownUntil)); }
    private void debug(String message) { if (mod.configManager().settings().debug()) LegendarySpawn.logRuntimeInfo("[Debug] " + message); }
    private static String describe(RuntimeException exception) { String message = exception.getMessage(); return exception.getClass().getSimpleName() + (message == null || message.isBlank() ? "" : ": " + message); }

    public record RollResult(boolean rolled, boolean chancePassed, boolean spawned, double rollValue, double chance, SpawnResult spawnResult, String message, boolean automatic) {
        public static RollResult notRolled(String message) { return new RollResult(false, false, false, -1.0D, 0.0D, null, message, false); }
        public static RollResult failedChance(double value, double chance, boolean automatic) { return new RollResult(true, false, false, value, chance, null, "Chance roll failed.", automatic); }
        public static RollResult spawnFailed(double value, double chance, String message, boolean automatic) { return new RollResult(true, true, false, value, chance, null, message, automatic); }
        public static RollResult spawned(double value, double chance, SpawnResult result, boolean automatic) { return new RollResult(true, true, true, value, chance, result, "Spawn succeeded.", automatic); }
    }
}
