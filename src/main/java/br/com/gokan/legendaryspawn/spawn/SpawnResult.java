package br.com.gokan.legendaryspawn.spawn;

import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.Species;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public record SpawnResult(
        boolean success,
        String reason,
        Species species,
        Pokemon pokemon,
        int level,
        BlockPos position,
        ServerLevel world,
        ServerPlayer targetPlayer
) {
    public static SpawnResult failure(String reason) {
        return new SpawnResult(false, reason, null, null, 0, null, null, null);
    }

    public static SpawnResult success(Species species, Pokemon pokemon, int level, BlockPos position, ServerLevel world, ServerPlayer targetPlayer) {
        return new SpawnResult(true, "", species, pokemon, level, position, world, targetPlayer);
    }
}
