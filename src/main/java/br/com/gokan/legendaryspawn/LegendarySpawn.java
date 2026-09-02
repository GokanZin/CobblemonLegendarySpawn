package br.com.gokan.legendaryspawn;

import br.com.gokan.core.gkapi.AbstractModFabric;
import br.com.gokan.core.gkapi.utils.logger.GKLogger;
import br.com.gokan.legendaryspawn.command.LegendarySpawnCommands;
import br.com.gokan.legendaryspawn.config.LegendaryConfigManager;
import br.com.gokan.legendaryspawn.config.ReloadResult;
import br.com.gokan.legendaryspawn.config.ValidationReport;
import br.com.gokan.legendaryspawn.scheduler.LegendaryScheduler;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;

import java.util.List;

public final class LegendarySpawn extends AbstractModFabric {

    public static final String MOD_ID = "legendaryspawn";
    public static LegendarySpawn INSTANCE;
    public static GKLogger gkLogger;

    private LegendaryConfigManager configManager;
    private LegendaryScheduler scheduler;
    private int tickCounter;

    @Override
    protected void onInit() {
        INSTANCE = this;
        gkLogger = new GKLogger("LegendarySpawn");
        configManager = new LegendaryConfigManager(this);
        configManager.load();
        new LegendarySpawnCommands(this).register();
        ServerTickEvents.END_SERVER_TICK.register(this::onServerTick);
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
            LegendaryScheduler currentScheduler = scheduler;
            if (currentScheduler == null) return;
            try {
                currentScheduler.onLivingEntityDeath(entity, damageSource);
            } catch (RuntimeException exception) {
                logRuntimeError("[Death] Unexpected error while processing a living entity death: " + describe(exception), exception);
            }
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            CommandSourceStack source = handler.player.createCommandSourceStack();
            if (!configManager.isHealthy() && source.hasPermission(2)) {
                printCurrentConfigProblems(source, "The server started with an invalid LegendarySpawn configuration. Fix the files and run /legendaryspawn reload.");
            }
        });
    }

    @Override
    protected void onStartServer(MinecraftServer minecraftServer) {
        gkLogger.sideModEnable("Gokan", ".gokan.", "https://discord.gg/SuC2c8VWd5", "1.21.1 - FABRIC");
        scheduler = new LegendaryScheduler(this, minecraftServer);
    }

    @Override
    protected void onServerStarted(MinecraftServer minecraftServer) {
        ValidationReport report;
        try {
            report = configManager.validateRuntime(minecraftServer);
        } catch (RuntimeException exception) {
            String error = "Runtime configuration validation crashed: " + describe(exception);
            logRuntimeError("[Config] " + error, exception);
            report = new ValidationReport(List.of(error), List.of());
        }
        printStartupReport(report);
        if (scheduler != null) {
            scheduler.initialize();
            scheduler.onServerStarted();
        }
    }

    @Override
    protected void onStopServer(MinecraftServer minecraftServer) {
        if (scheduler != null) {
            scheduler.shutdown();
        }
    }

    @Override
    protected String getModId() {
        return MOD_ID;
    }

    public LegendaryConfigManager configManager() {
        return configManager;
    }

    public LegendaryScheduler scheduler() {
        return scheduler;
    }

    public void printReloadReport(CommandSourceStack source, ReloadResult result) {
        synchronized (gkLogger) {
            if (result.success()) {
                gkLogger.info("[Reload] all LegendarySpawn YAML files validated successfully.");
            } else {
                for (String error : result.errors()) {
                    gkLogger.error("[Reload] " + error);
                }
            }
            for (String warning : result.warnings()) {
                gkLogger.info("[Reload warning] " + warning);
            }
            gkLogger.printConsole();
        }

        GKLogger playerLogger = new GKLogger("LegendarySpawn");
        if (result.success()) {
            playerLogger.info("Configuration reloaded and validated successfully.");
            for (String warning : result.warnings()) {
                playerLogger.info("Warning: " + warning);
            }
            playerLogger.printFancy(source, "The new valid settings are active now.");
        } else {
            playerLogger.error(result.errors());
            for (String warning : result.warnings()) {
                playerLogger.info("Warning: " + warning);
            }
            playerLogger.printFancy(source, "Reload cancelled. The previous valid configuration is still active.");
        }
    }

    public void printCurrentConfigProblems(CommandSourceStack source, String footer) {
        GKLogger playerLogger = new GKLogger("LegendarySpawn");
        List<String> errors = configManager.validationErrors();
        if (errors.isEmpty()) {
            playerLogger.info("No active configuration errors were recorded.");
        } else {
            playerLogger.error(errors);
        }
        for (String warning : configManager.validationWarnings()) {
            playerLogger.info("Warning: " + warning);
        }
        playerLogger.printFancy(source, footer);
    }

    public static void logRuntimeError(String message, Throwable throwable) {
        if (gkLogger == null) {
            return;
        }
        if (throwable == null) {
            gkLogger.logger.error(message);
        } else {
            gkLogger.logger.error(message, throwable);
        }
    }

    public static void logRuntimeWarning(String message) {
        if (gkLogger != null) {
            gkLogger.logger.warn(message);
        }
    }

    public static void logRuntimeInfo(String message) {
        if (gkLogger != null) {
            gkLogger.logger.info(message);
        }
    }

    private void printStartupReport(ValidationReport report) {
        synchronized (gkLogger) {
            gkLogger.info("[Startup] Loaded config.yml.");
            gkLogger.info("[Startup] Loaded commands.yml.");
            gkLogger.info("[Startup] Loaded messages.yml.");
            gkLogger.info("[Startup] Loaded pokemon.yml.");
            gkLogger.info("[Startup] Loaded blacklist.yml.");
            gkLogger.info("[Startup] Loaded worlds.yml.");
            gkLogger.info("[Startup] Loaded biomes.yml.");
            gkLogger.info("[Startup] Loaded protection.yml.");
            gkLogger.info("[Startup] Loaded weights.yml.");
            gkLogger.info("[Startup] Loaded cooldowns.yml.");
            gkLogger.info("[Startup] Loaded limits.yml.");
            gkLogger.info("[Startup] Loaded pokemon-overrides.yml.");
            gkLogger.info("[Startup] Loaded bossbar.yml.");
            gkLogger.info("[Startup] Loaded events.yml.");
            gkLogger.info("[Startup] Loaded hover.yml.");
            gkLogger.info("[Startup] Loaded state.yml.");
            if (report.valid()) {
                gkLogger.info("[Startup] Configuration validation passed. LegendarySpawn is ready.");
            } else {
                for (String error : report.errors()) {
                    gkLogger.error("[Startup] " + error);
                }
            }
            for (String warning : report.warnings()) {
                gkLogger.info("[Startup warning] " + warning);
            }
            gkLogger.printConsole();
        }
    }

    private void onServerTick(MinecraftServer server) {
        if (++tickCounter < 20) {
            return;
        }
        tickCounter = 0;
        if (scheduler == null || !configManager.isHealthy()) {
            return;
        }

        try {
            scheduler.tick();
        } catch (RuntimeException exception) {
            logRuntimeError("[Scheduler] Unexpected tick error: " + describe(exception), exception);
        }
    }

    private static String describe(RuntimeException exception) {
        String message = exception.getMessage();
        return exception.getClass().getSimpleName() + (message == null || message.isBlank() ? "" : ": " + message);
    }
}
