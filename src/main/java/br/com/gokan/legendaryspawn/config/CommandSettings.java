package br.com.gokan.legendaryspawn.config;

import java.util.List;

public record CommandSettings(
        String rootName,
        List<String> aliases,
        int permissionLevel,
        String permission,
        String noPermissionMessage,
        Subcommand reload,
        Subcommand status,
        Subcommand force,
        Subcommand roll,
        Subcommand reschedule
) {
    public record Subcommand(String name, int permissionLevel, String permission) {
    }
}
