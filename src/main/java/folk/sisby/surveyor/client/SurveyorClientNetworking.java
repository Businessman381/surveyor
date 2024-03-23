package folk.sisby.surveyor.client;

import folk.sisby.surveyor.SurveyorEvents;
import folk.sisby.surveyor.SurveyorNetworking;
import folk.sisby.surveyor.SurveyorWorld;
import folk.sisby.surveyor.WorldSummary;
import folk.sisby.surveyor.packet.SyncExploredStructuresPacket;
import folk.sisby.surveyor.packet.SyncLandmarksAddedPacket;
import folk.sisby.surveyor.packet.SyncLandmarksRemovedPacket;
import folk.sisby.surveyor.packet.S2CPacket;
import folk.sisby.surveyor.packet.S2CStructuresAddedPacket;
import folk.sisby.surveyor.packet.S2CUpdateRegionPacket;
import folk.sisby.surveyor.terrain.RegionSummary;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.math.ChunkPos;

import java.util.HashMap;
import java.util.function.Function;

public class SurveyorClientNetworking {
    public static void init() {
        SurveyorNetworking.C2S_SENDER = p -> {
            if (!ClientPlayNetworking.canSend(p.getId())) return;
            p.toBufs().forEach(buf -> ClientPlayNetworking.send(p.getId(), buf));
        };
        ClientPlayNetworking.registerGlobalReceiver(S2CStructuresAddedPacket.ID, (c, h, b, s) -> handleClient(b, S2CStructuresAddedPacket::read, SurveyorClientNetworking::handleStructuresAdded));
        ClientPlayNetworking.registerGlobalReceiver(S2CUpdateRegionPacket.ID, (c, h, b, s) -> handleClientUnparsed(b, SurveyorClientNetworking::handleTerrainAdded));
        ClientPlayNetworking.registerGlobalReceiver(SyncLandmarksAddedPacket.ID, (c, h, b, s) -> handleClient(b, SyncLandmarksAddedPacket::read, SurveyorClientNetworking::handleLandmarksAdded));
        ClientPlayNetworking.registerGlobalReceiver(SyncLandmarksRemovedPacket.ID, (c, h, b, s) -> handleClient(b, SyncLandmarksRemovedPacket::read, SurveyorClientNetworking::handleLandmarksRemoved));
        ClientPlayNetworking.registerGlobalReceiver(SyncExploredStructuresPacket.ID, (c, h, b, s) -> handleClient(b, SyncExploredStructuresPacket::read, SurveyorClientNetworking::handleExploredStructures));
    }

    private static void handleStructuresAdded(ClientWorld world, WorldSummary summary, S2CStructuresAddedPacket packet) {
        packet.structures().forEach((key, map) -> map.forEach((pos, start) -> summary.structures().put(world, key, pos, start, packet.structureTypes().get(key), packet.structureTags().get(key))));
    }

    private static void handleTerrainAdded(ClientWorld world, WorldSummary summary, PacketByteBuf buf) {
        S2CUpdateRegionPacket packet = S2CUpdateRegionPacket.handle(buf, world.getRegistryManager(), summary);
        SurveyorEvents.Invoke.terrainUpdated(world, summary.terrain(), packet.chunks().stream().mapToObj(i -> new ChunkPos((packet.regionPos().x << RegionSummary.REGION_POWER) + (i / RegionSummary.REGION_SIZE), (packet.regionPos().z << RegionSummary.REGION_POWER) + (i % RegionSummary.REGION_SIZE))).toList());
    }

    private static void handleLandmarksAdded(ClientWorld world, WorldSummary summary, SyncLandmarksAddedPacket packet) {
        packet.landmarks().forEach((type, map) -> map.forEach((pos, landmark) -> summary.landmarks().putLocal(world, landmark)));
    }

    private static void handleLandmarksRemoved(ClientWorld world, WorldSummary summary, SyncLandmarksRemovedPacket packet) {
        packet.landmarks().forEach((type, positions) -> positions.forEach(pos -> summary.landmarks().removeLocal(world, type, pos)));
    }

    private static void handleExploredStructures(ClientWorld world, WorldSummary summary, SyncExploredStructuresPacket packet) {
        packet.structureKeys().forEach((key, starts) -> SurveyorClient.getExploration().surveyor$exploredStructures().computeIfAbsent(packet.worldKey(), k -> new HashMap<>()).computeIfAbsent(key, k -> new LongOpenHashSet()).addAll(starts));
    }

    private static <T extends S2CPacket> void handleClient(PacketByteBuf buf, Function<PacketByteBuf, T> reader, ClientPacketHandler<T> handler) {
        T packet = reader.apply(buf);
        WorldSummary summary = ((SurveyorWorld) MinecraftClient.getInstance().world).surveyor$getWorldSummary();
        if (!summary.isClient()) return;
        MinecraftClient.getInstance().execute(() -> handler.handle(MinecraftClient.getInstance().world, summary, packet));
    }

    private static void handleClientUnparsed(PacketByteBuf buf, ClientPacketHandler<PacketByteBuf> handler) {
        ClientWorld world = MinecraftClient.getInstance().world;
        WorldSummary summary = ((SurveyorWorld) MinecraftClient.getInstance().world).surveyor$getWorldSummary();
        if (!summary.isClient()) return;
        handler.handle(world, summary, buf);
    }

    public interface ClientPacketHandler<T> {
        void handle(ClientWorld clientWorld, WorldSummary summary, T packet);
    }
}
