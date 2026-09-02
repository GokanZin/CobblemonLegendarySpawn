package br.com.gokan.legendaryspawn.command;

import br.com.gokan.core.gkapi.modules.command.CommandBuilder;
import br.com.gokan.core.gkapi.utils.player.PlayerUtil;
import br.com.gokan.legendaryspawn.LegendarySpawn;
import br.com.gokan.legendaryspawn.config.CommandSettings;
import br.com.gokan.legendaryspawn.config.LegendarySettings;
import br.com.gokan.legendaryspawn.config.ReloadResult;
import br.com.gokan.legendaryspawn.scheduler.LegendaryScheduler;
import br.com.gokan.legendaryspawn.spawn.SpawnResult;
import br.com.gokan.legendaryspawn.util.TextUtil;
import br.com.gokan.legendaryspawn.util.PokemonHoverUtil;
import br.com.gokan.legendaryspawn.util.TimeUtil;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.function.Function;

public final class LegendarySpawnCommands {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z")
            .withLocale(Locale.ENGLISH)
            .withZone(ZoneId.systemDefault());
    private static final DecimalFormat PERCENT = new DecimalFormat("0.##", DecimalFormatSymbols.getInstance(Locale.US));

    private final LegendarySpawn mod;

    public LegendarySpawnCommands(LegendarySpawn mod) {
        this.mod = mod;
    }

    public void register() {
        CommandSettings registered = mod.configManager().commandSettings();

        CommandBuilder root = new CommandBuilder(registered.rootName())
                .aliases(registered.aliases().toArray(String[]::new))
                .description("Controls automatic legendary Pokemon spawns.")
                .setPermissionLevel(0)
                .defaultExecutor(context -> executeWithAccess(context, CommandSettings::status, this::status));

        root.subcommand(subcommand(registered.reload().name(), CommandSettings::reload, this::reload));
        root.subcommand(subcommand(registered.status().name(), CommandSettings::status, this::status));
        root.subcommand(subcommand(registered.force().name(), CommandSettings::force, this::force));
        root.subcommand(subcommand(registered.roll().name(), CommandSettings::roll, this::roll));
        root.subcommand(subcommand(registered.reschedule().name(), CommandSettings::reschedule, this::reschedule));
        root.register();
    }

    private CommandBuilder subcommand(
            String registeredName,
            Function<CommandSettings, CommandSettings.Subcommand> selector,
            Command<CommandSourceStack> executor
    ) {
        return new CommandBuilder(registeredName)
                .setPermissionLevel(0)
                .executor(context -> executeWithAccess(context, selector, executor));
    }

    private int executeWithAccess(
            CommandContext<CommandSourceStack> context,
            Function<CommandSettings, CommandSettings.Subcommand> selector,
            Command<CommandSourceStack> executor
    ) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        CommandSettings current = mod.configManager().commandSettings();
        CommandSourceStack source = context.getSource();

        if (!hasAccess(source, current.permissionLevel(), current.permission())) {
            send(source, current.noPermissionMessage());
            return 0;
        }

        CommandSettings.Subcommand subcommand = selector.apply(current);
        if (!hasAccess(source, subcommand.permissionLevel(), subcommand.permission())) {
            send(source, current.noPermissionMessage());
            return 0;
        }

        return executor.run(context);
    }

    private static boolean hasAccess(CommandSourceStack source, int permissionLevel, String permission) {
        if (permissionLevel > 0 && !source.hasPermission(permissionLevel)) {
            return false;
        }

        if (permission == null || permission.isBlank()) {
            return true;
        }

        if (source.getEntity() instanceof ServerPlayer player) {
            return PlayerUtil.isPermission(player, permission);
        }
        return true;
    }

    private int reload(CommandContext<CommandSourceStack> context) {
        ReloadResult result = mod.configManager().reload(context.getSource().getServer());
        CommandSourceStack source = context.getSource();

        mod.printReloadReport(source, result);
        if (!result.success()) {
            return 0;
        }

        if (mod.scheduler() != null) {
            mod.scheduler().onSettingsReloaded();
        }
        return 1;
    }

    private int status(CommandContext<CommandSourceStack> context) {
        LegendaryScheduler scheduler = mod.scheduler();
        CommandSourceStack source = context.getSource();
        if (!mod.configManager().isHealthy()) {
            mod.printCurrentConfigProblems(source, "Fix the configuration and run /legendaryspawn reload before using the spawn system.");
            return 0;
        }
        if (scheduler == null) {
            send(source, "&cThe scheduler is not available yet.");
            return 0;
        }

        double chance = mod.configManager().settings().spawnChance() * 100.0D;
        String next = scheduler.isPaused()
                ? mod.configManager().message("status.paused", "Paused")
                : DATE_TIME.format(Instant.ofEpochMilli(scheduler.nextAttemptEpochMillis()));

        send(source, mod.configManager().message("status.header", "&6LegendarySpawn status"));
        send(source, "&7Enabled: &f" + mod.configManager().settings().enabled());
        send(source, "&7Next roll: &f" + next);
        send(source, "&7Time remaining: &f" + TimeUtil.formatDuration(scheduler.remainingMillis()));
        send(source, "&7Chance: &f" + PERCENT.format(chance) + "%");
        send(source, "&7Valid species (currently eligible): &f" + scheduler.validSpeciesCount());
        send(source, "&7Pokemon pool: &f" + pokemonPoolDescription(mod.configManager().settings()));
        send(source, "&7World mode: &f" + worldModeDescription(mod.configManager().settings()));
        send(source, "&7Biome restrictions: &f" + biomeModeDescription(mod.configManager().settings()));
        send(source, "&7Spawn protection: &f" + protectionModeDescription(mod.configManager().settings()));
        send(source, "&7Tracked special spawns: &f" + scheduler.trackedLegendCount() + " &7(&f" + scheduler.protectedLegendCount() + " &7protected)");
        LegendarySettings.Limits limits = mod.configManager().settings().limits();
        send(source, "&7Active limit: &f" + (limits.activeLimitEnabled() ? scheduler.trackedLegendCount() + "/" + limits.maxActive() : "Disabled"));
        send(source, "&7Global cooldown: &f" + (scheduler.globalCooldownRemainingMillis() > 0L ? TimeUtil.formatDuration(scheduler.globalCooldownRemainingMillis()) : "Ready"));
        send(source, "&7Label weights: &f" + (mod.configManager().settings().weights().enabled() ? "Enabled" : "Disabled"));
        send(source, "&7Bossbar: &f" + (mod.configManager().settings().bossbar().enabled() ? "Enabled" : "Disabled"));
        send(source, "&7Players: &f" + scheduler.onlinePlayerCount() + "&7/&f" + mod.configManager().settings().minimumOnlinePlayers());
        send(source, "&7Eligible spawn targets: &f" + scheduler.eligiblePlayerCount());
        return 1;
    }

    private int force(CommandContext<CommandSourceStack> context) {
        LegendaryScheduler scheduler = mod.scheduler();
        if (!mod.configManager().isHealthy()) {
            mod.printCurrentConfigProblems(context.getSource(), "Fix the configuration and reload before forcing a spawn.");
            return 0;
        }
        if (scheduler == null) {
            send(context.getSource(), "&cThe scheduler is not available yet.");
            return 0;
        }

        SpawnResult result = scheduler.forceSpawn();
        if (!result.success()) {
            send(context.getSource(), "&cForce spawn failed: &f" + result.reason());
            return 0;
        }

        sendPokemonResult(context.getSource(), "&aSpawned &f{pokemon} &aat level &f" + result.level() + "&a. The automatic timer was not changed.", result, "force-command");
        return 1;
    }

    private int roll(CommandContext<CommandSourceStack> context) {
        LegendaryScheduler scheduler = mod.scheduler();
        if (!mod.configManager().isHealthy()) {
            mod.printCurrentConfigProblems(context.getSource(), "Fix the configuration and reload before running a roll.");
            return 0;
        }
        if (scheduler == null) {
            send(context.getSource(), "&cThe scheduler is not available yet.");
            return 0;
        }

        LegendaryScheduler.RollResult result = scheduler.rollNow();
        if (!result.rolled()) {
            send(context.getSource(), "&eRoll not spent: &f" + result.message());
            return 0;
        }

        String rolled = PERCENT.format(result.rollValue() * 100.0D) + "%";
        String required = PERCENT.format(result.chance() * 100.0D) + "%";
        if (!result.chancePassed()) {
            send(context.getSource(), "&eRoll failed. &7Rolled &f" + rolled + "&7 against &f" + required + "&7. The automatic timer was not changed.");
            return 1;
        }
        if (!result.spawned()) {
            send(context.getSource(), "&cRoll passed, but the spawn failed: &f" + result.message());
            return 0;
        }

        sendPokemonResult(context.getSource(), "&aRoll passed and spawned &f{pokemon}&a. The automatic timer was not changed.", result.spawnResult(), "roll-command");
        return 1;
    }

    private int reschedule(CommandContext<CommandSourceStack> context) {
        LegendaryScheduler scheduler = mod.scheduler();
        if (!mod.configManager().isHealthy()) {
            mod.printCurrentConfigProblems(context.getSource(), "Fix the configuration and reload before rescheduling the timer.");
            return 0;
        }
        if (scheduler == null) {
            send(context.getSource(), "&cThe scheduler is not available yet.");
            return 0;
        }

        long duration = scheduler.reschedule();
        send(context.getSource(), "&aNew automatic interval: &f" + TimeUtil.formatDuration(duration) + "&a.");
        return 1;
    }

    private static String pokemonPoolDescription(LegendarySettings settings) {
        if (!settings.pokemonPool().specificPokemon().isEmpty()) {
            return "Specific list from pokemon.yml (" + settings.pokemonPool().specificPokemon().size() + ")";
        }
        if (settings.pokemonPool().requiredLabels().isEmpty()) {
            return "Dynamic Cobblemon registry (no required labels)";
        }
        return "Dynamic Cobblemon registry, labels " + settings.pokemonPool().requiredLabels();
    }

    private static String worldModeDescription(LegendarySettings settings) {
        if (settings.worlds().allowAllWorlds()) {
            return "All worlds, " + settings.worlds().blockedWorlds().size() + " blocked";
        }
        return settings.worlds().allowedWorlds().size() + " allowed, " + settings.worlds().blockedWorlds().size() + " blocked";
    }

    private static String biomeModeDescription(LegendarySettings settings) {
        LegendarySettings.Biomes biomes = settings.biomes();
        if (!biomes.enabled()) {
            return "Disabled";
        }
        if (!biomes.useCobblemonSpawnData()) {
            return "Enabled, Cobblemon spawn data disabled";
        }
        String missing = biomes.whenNoSpawnData() == LegendarySettings.MissingBiomeDataBehavior.ALLOW_ANY
                ? "allow any biome when missing"
                : "reject when missing";
        return "Cobblemon spawn data (" + missing + ")";
    }

    private static String protectionModeDescription(LegendarySettings settings) {
        LegendarySettings.Protection protection = settings.protection();
        if (!protection.enabled()) {
            return "Disabled";
        }
        String restore = protection.restoreIfRemoved() ? ", restore removed entities" : "";
        String natural = protection.preventNaturalDespawn() ? ", block natural despawn" : "";
        return protection.durationMinutes() + "m" + natural + restore;
    }

    private void sendPokemonResult(CommandSourceStack source, String message, SpawnResult result, String context) {
        source.sendSystemMessage(TextUtil.component(TextUtil.trimOuterBlankLines(message), (placeholder, style) -> {
            if (placeholder.equalsIgnoreCase("pokemon") && result.pokemon() != null) {
                return PokemonHoverUtil.nameComponent(mod.configManager(), result.pokemon(), result.species().getName(), style, context);
            }
            return null;
        }));
    }

    private static void send(CommandSourceStack source, String message) {
        source.sendSystemMessage(TextUtil.component(message));
    }
}
