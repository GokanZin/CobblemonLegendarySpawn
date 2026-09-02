package br.com.gokan.legendaryspawn.spawn;

import br.com.gokan.legendaryspawn.config.LegendarySettings;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.api.spawning.CobblemonSpawnPools;
import com.cobblemon.mod.common.api.spawning.detail.PokemonHerdSpawnDetail;
import com.cobblemon.mod.common.api.spawning.detail.PokemonSpawnDetail;
import com.cobblemon.mod.common.api.spawning.detail.SpawnDetail;
import com.cobblemon.mod.common.api.spawning.detail.SpawnPool;
import com.cobblemon.mod.common.pokemon.Species;
import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class BiomeSpawnIndex {

    private static final String COBBLEMON_NAMESPACE = "cobblemon";
    private static final BiomeSpawnIndex EMPTY = new BiomeSpawnIndex(Map.of(), 0, 0, false);

    private final Map<String, Set<ResourceLocation>> biomesBySpecies;
    private final int pokemonDetails;
    private final int herdDetails;
    private final boolean loaded;

    private BiomeSpawnIndex(
            Map<String, Set<ResourceLocation>> biomesBySpecies,
            int pokemonDetails,
            int herdDetails,
            boolean loaded
    ) {
        this.biomesBySpecies = biomesBySpecies;
        this.pokemonDetails = pokemonDetails;
        this.herdDetails = herdDetails;
        this.loaded = loaded;
    }

    public static BiomeSpawnIndex empty() {
        return EMPTY;
    }

    public static BiomeSpawnIndex build() {
        SpawnPool pool = CobblemonSpawnPools.INSTANCE.getWORLD_SPAWN_POOL();
        Map<String, Set<ResourceLocation>> mutable = new HashMap<>();
        int pokemonDetails = 0;
        int herdDetails = 0;

        for (SpawnDetail detail : pool) {
            if (detail instanceof PokemonSpawnDetail pokemonDetail) {
                pokemonDetails++;
                addSpecies(mutable, pokemonDetail.getPokemon().getSpecies(), detail.getValidBiomes());
                continue;
            }

            if (detail instanceof PokemonHerdSpawnDetail herdDetail) {
                herdDetails++;
                for (PokemonHerdSpawnDetail.Herdable herdable : herdDetail.getHerdablePokemon()) {
                    addSpecies(mutable, herdable.getPokemon().getSpecies(), detail.getValidBiomes());
                }
            }
        }

        Map<String, Set<ResourceLocation>> frozen = new HashMap<>();
        for (Map.Entry<String, Set<ResourceLocation>> entry : mutable.entrySet()) {
            frozen.put(entry.getKey(), Set.copyOf(entry.getValue()));
        }

        return new BiomeSpawnIndex(
                Collections.unmodifiableMap(frozen),
                pokemonDetails,
                herdDetails,
                true
        );
    }

    public boolean loaded() {
        return loaded;
    }

    public int speciesWithSpawnData() {
        return biomesBySpecies.size();
    }

    public int pokemonDetails() {
        return pokemonDetails;
    }

    public int herdDetails() {
        return herdDetails;
    }

    public boolean hasSpawnData(Species species) {
        return biomesBySpecies.containsKey(speciesId(species));
    }

    public boolean hasAnyValidBiome(Species species) {
        Set<ResourceLocation> biomes = biomesBySpecies.get(speciesId(species));
        return biomes != null && !biomes.isEmpty();
    }

    public boolean isSpeciesEligible(Species species, LegendarySettings.Biomes settings) {
        if (!settings.enabled() || !settings.useCobblemonSpawnData()) {
            return true;
        }

        Set<ResourceLocation> biomes = biomesBySpecies.get(speciesId(species));
        if (biomes == null) {
            return settings.whenNoSpawnData() == LegendarySettings.MissingBiomeDataBehavior.ALLOW_ANY;
        }
        return !biomes.isEmpty();
    }

    public boolean isBiomeAllowed(
            Species species,
            ResourceLocation biome,
            LegendarySettings.Biomes settings
    ) {
        if (!settings.enabled() || !settings.useCobblemonSpawnData()) {
            return true;
        }

        Set<ResourceLocation> biomes = biomesBySpecies.get(speciesId(species));
        if (biomes == null) {
            return settings.whenNoSpawnData() == LegendarySettings.MissingBiomeDataBehavior.ALLOW_ANY;
        }
        return biome != null && biomes.contains(biome);
    }

    private static void addSpecies(
            Map<String, Set<ResourceLocation>> target,
            String rawSpecies,
            Set<ResourceLocation> validBiomes
    ) {
        Species species = resolveSpecies(rawSpecies);
        if (species == null) {
            return;
        }

        String id = speciesId(species);
        Set<ResourceLocation> merged = target.computeIfAbsent(id, ignored -> new HashSet<>());
        if (validBiomes != null && !validBiomes.isEmpty()) {
            merged.addAll(validBiomes);
        }
    }

    private static Species resolveSpecies(String rawSpecies) {
        if (rawSpecies == null || rawSpecies.isBlank() || rawSpecies.equalsIgnoreCase("random")) {
            return null;
        }

        String normalized = rawSpecies.trim().toLowerCase(java.util.Locale.ROOT);
        ResourceLocation id = normalized.indexOf(':') >= 0
                ? ResourceLocation.tryParse(normalized)
                : ResourceLocation.fromNamespaceAndPath(COBBLEMON_NAMESPACE, normalized);
        return id == null ? null : PokemonSpecies.getByIdentifier(id);
    }

    private static String speciesId(Species species) {
        return species.getResourceIdentifier().toString().toLowerCase(java.util.Locale.ROOT);
    }
}
