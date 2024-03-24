package folk.sisby.surveyor;

import folk.sisby.surveyor.landmark.Landmark;
import folk.sisby.surveyor.packet.S2CStructuresAddedPacket;
import folk.sisby.surveyor.structure.WorldStructureSummary;
import folk.sisby.surveyor.terrain.RegionSummary;
import folk.sisby.surveyor.util.MapUtil;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.gen.structure.Structure;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public interface SurveyorExploration {
    static SurveyorExploration of(ServerPlayerEntity player) {
        return ((SurveyorPlayer) player).surveyor$getExploration();
    }

    String KEY_EXPLORED_TERRAIN = "exploredTerrain";
    String KEY_EXPLORED_STRUCTURES = "exploredStructures";

    Map<RegistryKey<World>, Map<ChunkPos, BitSet>> terrain();

    Map<RegistryKey<World>, Map<RegistryKey<Structure>, LongSet>> structures();

    Set<UUID> sharedPlayers();

    World getWorld();

    @Nullable ServerPlayerEntity getServerPlayer();

    int getViewDistance();

    default boolean exploredChunk(RegistryKey<World> worldKey, ChunkPos pos) {
        ChunkPos regionPos = new ChunkPos(pos.getRegionX(), pos.getRegionZ());
        Map<ChunkPos, BitSet> regions = terrain().get(worldKey);
        return Surveyor.CONFIG.shareAllTerrain || regions != null && regions.containsKey(regionPos) && regions.get(regionPos).get(RegionSummary.bitForChunk(pos));
    }

    default boolean exploredStructure(RegistryKey<World> worldKey, RegistryKey<Structure> structure, ChunkPos pos) {
        Map<RegistryKey<Structure>, LongSet> structures = structures().get(worldKey);
        return Surveyor.CONFIG.shareAllStructures || structures != null && structures.containsKey(structure) && structures.get(structure).contains(pos.toLong());
    }

    default boolean exploredLandmark(RegistryKey<World> worldKey, Landmark<?> landmark) {
        return Surveyor.CONFIG.shareAllLandmarks || (landmark.owner() == null ? exploredChunk(worldKey, new ChunkPos(landmark.pos())) : sharedPlayers().contains(landmark.owner()));
    }

    default void limitTerrainBitset(RegistryKey<World> worldKey, Map<ChunkPos, BitSet> bitSets) {
        if (Surveyor.CONFIG.shareAllTerrain) return;
        Map<ChunkPos, BitSet> regions = terrain().get(worldKey);
        if (regions == null) {
            bitSets.clear();
            return;
        }
        bitSets.forEach((rPos, set) -> {
            if (regions.containsKey(rPos)) {
                set.and(regions.get(rPos));
            } else {
                set.clear();
            }
        });
    }

    default void limitStructureKeySet(RegistryKey<World> worldKey, Map<RegistryKey<Structure>, Set<ChunkPos>> keySet) {
        if (Surveyor.CONFIG.shareAllStructures) return;
        Map<RegistryKey<Structure>, LongSet> structures = structures().get(worldKey);
        if (structures == null) {
            keySet.clear();
            return;
        }
        keySet.forEach((key, starts) -> {
            if (structures.containsKey(key)) {
                structures.get(key).longStream().mapToObj(ChunkPos::new).toList().forEach(starts::remove);
            } else {
                starts.clear();
            }
        });
    }

    default void mergeRegion(RegistryKey<World> worldKey, ChunkPos regionPos, BitSet bitSet) {
        terrain().computeIfAbsent(worldKey, k -> new HashMap<>()).computeIfAbsent(regionPos, p -> new BitSet(RegionSummary.REGION_SIZE)).or(bitSet);
    }

    default void addChunk(ChunkPos pos) {
        terrain().computeIfAbsent(getWorld().getRegistryKey(), k -> new HashMap<>()).computeIfAbsent(new ChunkPos(pos.getRegionX(), pos.getRegionZ()), k -> new BitSet(RegionSummary.BITSET_SIZE)).set(RegionSummary.bitForChunk(pos));
        // Sync to shared players if serverPlayer exists and they don't have it
    }

    default void addStructure(RegistryKey<World> worldKey, RegistryKey<Structure> structureKey, ChunkPos pos) {
        structures().computeIfAbsent(worldKey, k -> new HashMap<>()).computeIfAbsent(structureKey, s -> new LongOpenHashSet()).add(pos.toLong());
    }

    default void addStructure(ServerWorld world, Structure structure, ChunkPos pos) {
        RegistryKey<World> worldKey = getWorld().getRegistryKey();
        RegistryKey<Structure> structureKey = getWorld().getRegistryManager().get(RegistryKeys.STRUCTURE).getKey(structure).orElseThrow();
        structures().computeIfAbsent(worldKey, k -> new HashMap<>()).computeIfAbsent(structureKey, s -> new LongOpenHashSet()).add(pos.toLong());
        ServerPlayerEntity serverPlayer = getServerPlayer();
        if (serverPlayer != null) {
            WorldStructureSummary summary = WorldSummary.of(world).structures();
            new S2CStructuresAddedPacket(Map.of(structureKey, Map.of(pos, summary.get(structureKey, pos))), Map.of(structureKey, summary.getType(structureKey)), MapUtil.hashMultiMapOf(Map.of(structureKey, summary.getTags(structureKey)))).send(serverPlayer);
            // Sync to shared players if they don't have it
        }
    }

    default NbtCompound write(NbtCompound nbt) {
        NbtCompound terrainCompound = new NbtCompound();
        terrain().forEach((worldKey, map) -> {
            long[] regionArray = new long[map.size() * 17];
            int i = 16;
            for (Map.Entry<ChunkPos, BitSet> entry : map.entrySet()) {
                regionArray[i - 16] = entry.getKey().toLong();
                long[] regionBits = entry.getValue().toLongArray();
                System.arraycopy(regionBits, 0, regionArray, i - 15 + (16 - regionBits.length), regionBits.length);
                i += 17;
            }
            terrainCompound.putLongArray(worldKey.getValue().toString(), regionArray);
        });
        nbt.put(KEY_EXPLORED_TERRAIN, terrainCompound);

        NbtCompound structuresCompound = new NbtCompound();
        structures().forEach((worldKey, map) -> {
            NbtCompound worldStructuresCompound = new NbtCompound();
            for (RegistryKey<Structure> structure : map.keySet()) {
                worldStructuresCompound.putLongArray(structure.getValue().toString(), map.get(structure).toLongArray());
            }
            structuresCompound.put(worldKey.getValue().toString(), worldStructuresCompound);
        });
        nbt.put(KEY_EXPLORED_STRUCTURES, structuresCompound);
        return nbt;
    }

    default void read(NbtCompound nbt) {
        terrain().clear();
        NbtCompound terrainCompound = nbt.getCompound(KEY_EXPLORED_TERRAIN);
        for (String worldKeyString : terrainCompound.getKeys()) {
            long[] regionArray = terrainCompound.getLongArray(worldKeyString);
            Map<ChunkPos, BitSet> regionMap = new HashMap<>();
            for (int i = 16; i < regionArray.length; i += 17) {
                regionMap.put(new ChunkPos(regionArray[i - 16]), BitSet.valueOf(Arrays.copyOfRange(regionArray, i - 15, i)));
            }
            terrain().put(RegistryKey.of(RegistryKeys.WORLD, new Identifier(worldKeyString)), regionMap);
        }

        structures().clear();
        NbtCompound structuresCompound = nbt.getCompound(KEY_EXPLORED_STRUCTURES);
        for (String worldKeyString : structuresCompound.getKeys()) {
            Map<RegistryKey<Structure>, LongSet> structureMap = new HashMap<>();
            NbtCompound worldStructuresCompound = structuresCompound.getCompound(worldKeyString);
            for (String key : worldStructuresCompound.getKeys()) {
                structureMap.put(RegistryKey.of(RegistryKeys.STRUCTURE, new Identifier(key)), new LongOpenHashSet(LongSet.of(worldStructuresCompound.getLongArray(key))));
            }
            structures().put(RegistryKey.of(RegistryKeys.WORLD, new Identifier(worldKeyString)), structureMap);
        }
    }

    default void copyFrom(SurveyorExploration them) {
        terrain().clear();
        terrain().putAll(them.terrain());
        structures().clear();
        structures().putAll(them.structures());
    }
}
