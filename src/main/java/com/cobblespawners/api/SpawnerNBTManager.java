package com.cobblespawners.api;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class SpawnerNBTManager {
    public static class PokemonInfo {
        private final BlockPos spawnerPos;
        private final String speciesName;
        private final long lastKnownTime;

        public PokemonInfo(BlockPos spawnerPos, String speciesName) {
            this.spawnerPos = spawnerPos;
            this.speciesName = speciesName;
            this.lastKnownTime = System.currentTimeMillis();
        }

        public BlockPos getSpawnerPos() { return spawnerPos; }
        public String getSpeciesName() { return speciesName; }
        public long getLastKnownTime() { return lastKnownTime; }
    }

    // --- A static tracker to persist the spawned Pokémon UUIDs per spawner ---
    private static final Map<UUID, Set<UUID>> SPAWNER_ENTITY_TRACKER = new ConcurrentHashMap<>();

    // When creating a new spawner, generate a UUID for it
    public static UUID createSpawnerUUID(BlockPos pos) {
        return UUID.nameUUIDFromBytes(("cobblespawner:" + pos.toShortString()).getBytes());
    }


    public static void addPokemon(PokemonEntity entity, BlockPos spawnerPos, String speciesName) {
        if (entity instanceof SpawnerDataProvider provider) {
            UUID spawnerUUID = createSpawnerUUID(spawnerPos);
            provider.cobblespawners$setSpawnerData(
                    spawnerPos,
                    entity.getWorld().getRegistryKey().getValue().toString(),
                    speciesName,
                    spawnerUUID
            );
            // Record the entity's UUID in our tracker
            SPAWNER_ENTITY_TRACKER
                    .computeIfAbsent(spawnerUUID, k -> ConcurrentHashMap.newKeySet())
                    .add(entity.getUuid());
        }
    }

    public static PokemonInfo getPokemonInfo(PokemonEntity entity) {
        if (entity instanceof SpawnerDataProvider provider && provider.cobblespawners$isFromSpawner()) {
            return new PokemonInfo(
                    provider.cobblespawners$getSpawnerPos(),
                    provider.cobblespawners$getSpeciesName()
            );
        }
        return null;
    }

    public static List<UUID> getUUIDsForSpawner(ServerWorld world, BlockPos spawnerPos) {
        UUID spawnerUUID = createSpawnerUUID(spawnerPos);
        Set<UUID> tracked = SPAWNER_ENTITY_TRACKER.getOrDefault(spawnerUUID, Collections.emptySet());
        List<UUID> uuids = new ArrayList<>();
        Iterator<UUID> iterator = tracked.iterator();
        while (iterator.hasNext()) {
            UUID uuid = iterator.next();
            if (world.getEntity(uuid) != null) {
                uuids.add(uuid);
            } else {
                iterator.remove(); // Clean up stale UUID
            }
        }
        return uuids;
    }

    public static int getPokemonCountForSpawner(ServerWorld world, BlockPos spawnerPos) {
        UUID spawnerUUID = createSpawnerUUID(spawnerPos);
        Set<UUID> tracked = SPAWNER_ENTITY_TRACKER.getOrDefault(spawnerUUID, Collections.emptySet());
        int count = 0;
        Iterator<UUID> iterator = tracked.iterator();
        while (iterator.hasNext()) {
            UUID uuid = iterator.next();
            if (world.getEntity(uuid) != null) {
                count++;
            } else {
                iterator.remove(); // Clean up stale UUID
            }
        }
        return count;
    }

    public static void clearPokemonForSpawner(ServerWorld world, BlockPos spawnerPos) {
        UUID spawnerUUID = createSpawnerUUID(spawnerPos);
        Set<UUID> tracked = SPAWNER_ENTITY_TRACKER.get(spawnerUUID);
        if (tracked != null) {
            // Copy the set so we can safely remove entries while iterating
            for (UUID uuid : new ArrayList<>(tracked)) {
                if (world.getEntity(uuid) instanceof PokemonEntity entity) {
                    entity.discard();
                    tracked.remove(uuid);
                }
            }
        }
    }
}
