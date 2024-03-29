package folk.sisby.surveyor;

import com.google.common.collect.Multimap;
import folk.sisby.surveyor.landmark.LandmarkType;
import folk.sisby.surveyor.landmark.WorldLandmarks;
import folk.sisby.surveyor.structure.WorldStructureSummary;
import folk.sisby.surveyor.terrain.WorldTerrainSummary;
import folk.sisby.surveyor.util.MapUtil;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.gen.structure.Structure;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SurveyorEvents {
    private static final Map<Identifier, WorldLoad> worldLoad = new HashMap<>();
    private static final Map<Identifier, TerrainUpdated> terrainUpdated = new HashMap<>();
    private static final Map<Identifier, StructuresAdded> structuresAdded = new HashMap<>();
    private static final Map<Identifier, LandmarksAdded> landmarksAdded = new HashMap<>();
    private static final Map<Identifier, LandmarksRemoved> landmarksRemoved = new HashMap<>();

    public static class Invoke {
        public static void worldLoad(ServerWorld world) {
            if (worldLoad.isEmpty()) return;
            WorldSummary summary = WorldSummary.of(world);
            worldLoad.forEach((id, handler) -> handler.onWorldLoad(world, summary));
        }

        public static void terrainUpdated(World world, Collection<ChunkPos> chunks) {
            if (terrainUpdated.isEmpty() || chunks.isEmpty()) return;
            WorldTerrainSummary summary = WorldSummary.of(world).terrain();
            terrainUpdated.forEach((id, handler) -> handler.onTerrainUpdated(world, summary, chunks));
        }

        public static void terrainUpdated(World world, ChunkPos pos) {

            terrainUpdated(world, List.of(pos));
        }

        public static void structuresAdded(World world, Multimap<RegistryKey<Structure>, ChunkPos> structures) {
            if (structuresAdded.isEmpty() || structures.isEmpty()) return;
            WorldStructureSummary summary = WorldSummary.of(world).structures();
            structuresAdded.forEach((id, handler) -> handler.onStructuresAdded(world, summary, structures));
        }

        public static void structuresAdded(World world, RegistryKey<Structure> key, ChunkPos pos) {
            structuresAdded(world, MapUtil.asMultiMap(Map.of(key, List.of(pos))));
        }

        public static void landmarksAdded(World world, Multimap<LandmarkType<?>, BlockPos> landmarks) {
            if (landmarksAdded.isEmpty() || landmarks.isEmpty()) return;
            WorldLandmarks summary = WorldSummary.of(world).landmarks();
            landmarksAdded.forEach((id, handler) -> handler.onLandmarksAdded(world, summary, landmarks));
        }

        public static void landmarksRemoved(World world, Multimap<LandmarkType<?>, BlockPos> landmarks) {
            if (landmarksRemoved.isEmpty() || landmarks.isEmpty()) return;
            WorldLandmarks summary = WorldSummary.of(world).landmarks();
            landmarksRemoved.forEach((id, handler) -> handler.onLandmarksRemoved(world, summary, landmarks));
        }
    }

    public static class Register {
        public static void worldLoad(Identifier id, WorldLoad handler) {
            worldLoad.put(id, handler);
        }

        public static void terrainUpdated(Identifier id, TerrainUpdated handler) {
            terrainUpdated.put(id, handler);
        }

        public static void structuresAdded(Identifier id, StructuresAdded handler) {
            structuresAdded.put(id, handler);
        }

        public static void landmarksAdded(Identifier id, LandmarksAdded handler) {
            landmarksAdded.put(id, handler);
        }

        public static void landmarksRemoved(Identifier id, LandmarksRemoved handler) {
            landmarksRemoved.put(id, handler);
        }
    }

    @FunctionalInterface
    public interface WorldLoad {
        void onWorldLoad(ServerWorld world, WorldSummary worldSummary);
    }

    @FunctionalInterface
    public interface TerrainUpdated {
        void onTerrainUpdated(World world, WorldTerrainSummary worldStructures, Collection<ChunkPos> chunks);
    }

    @FunctionalInterface
    public interface StructuresAdded {
        void onStructuresAdded(World world, WorldStructureSummary worldStructures, Multimap<RegistryKey<Structure>, ChunkPos> structures);
    }

    @FunctionalInterface
    public interface LandmarksAdded {
        void onLandmarksAdded(World world, WorldLandmarks worldLandmarks, Multimap<LandmarkType<?>, BlockPos> landmarks);
    }

    @FunctionalInterface
    public interface LandmarksRemoved {
        void onLandmarksRemoved(World world, WorldLandmarks worldLandmarks, Multimap<LandmarkType<?>, BlockPos> landmarks);
    }
}
