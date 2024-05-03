package folk.sisby.surveyor.client;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import folk.sisby.surveyor.SurveyorEvents;
import folk.sisby.surveyor.SurveyorExploration;
import folk.sisby.surveyor.SurveyorNetworking;
import folk.sisby.surveyor.WorldSummary;
import folk.sisby.surveyor.landmark.LandmarkType;
import folk.sisby.surveyor.packet.C2SKnownLandmarksPacket;
import folk.sisby.surveyor.packet.C2SKnownStructuresPacket;
import folk.sisby.surveyor.packet.C2SKnownTerrainPacket;
import folk.sisby.surveyor.packet.S2CGroupChangedPacket;
import folk.sisby.surveyor.packet.S2CGroupUpdatedPacket;
import folk.sisby.surveyor.packet.S2CPacket;
import folk.sisby.surveyor.packet.S2CStructuresAddedPacket;
import folk.sisby.surveyor.packet.S2CUpdateRegionPacket;
import folk.sisby.surveyor.packet.SyncLandmarksAddedPacket;
import folk.sisby.surveyor.packet.SyncLandmarksRemovedPacket;
import folk.sisby.surveyor.terrain.RegionSummary;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.math.BlockPos;

import java.util.function.Function;

public class SurveyorClientNetworking {
    public static void init() {
        SurveyorNetworking.C2S_SENDER = p -> {
            if (!ClientPlayNetworking.canSend(p.getId())) return;
            p.toBufs().forEach(buf -> ClientPlayNetworking.send(p.getId(), buf));
        };
        ClientPlayNetworking.registerGlobalReceiver(S2CStructuresAddedPacket.ID, (c, h, b, s) -> handleClientUnparsed(b, SurveyorClientNetworking::handleStructuresAdded));
        ClientPlayNetworking.registerGlobalReceiver(S2CUpdateRegionPacket.ID, (c, h, b, s) -> handleClientUnparsed(b, SurveyorClientNetworking::handleTerrainAdded));
        ClientPlayNetworking.registerGlobalReceiver(S2CGroupChangedPacket.ID, (c, h, b, s) -> handleClient(b, S2CGroupChangedPacket::read, SurveyorClientNetworking::handleGroupChanged));
        ClientPlayNetworking.registerGlobalReceiver(S2CGroupUpdatedPacket.ID, (c, h, b, s) -> handleClient(b, S2CGroupUpdatedPacket::read, SurveyorClientNetworking::handleGroupUpdated));
        ClientPlayNetworking.registerGlobalReceiver(SyncLandmarksAddedPacket.ID, (c, h, b, s) -> handleClientUnparsed(b, SurveyorClientNetworking::handleLandmarksAdded));
        ClientPlayNetworking.registerGlobalReceiver(SyncLandmarksRemovedPacket.ID, (c, h, b, s) -> handleClient(b, SyncLandmarksRemovedPacket::read, SurveyorClientNetworking::handleLandmarksRemoved));
    }

    private static void handleStructuresAdded(ClientWorld world, WorldSummary summary, PacketByteBuf buf) {
        S2CStructuresAddedPacket packet = S2CStructuresAddedPacket.handle(buf, world, summary);
        if (MinecraftClient.getInstance().player != null) {
            SurveyorExploration exploration = (packet.shared() ? SurveyorClient.getSharedExploration() : SurveyorClient.getPersonalExploration());
            packet.keySet().forEach((key, pos) -> exploration.addStructure(world.getRegistryKey(), key, pos));
        }
    }

    private static void handleTerrainAdded(ClientWorld world, WorldSummary summary, PacketByteBuf buf) {
        S2CUpdateRegionPacket packet = S2CUpdateRegionPacket.handle(buf, world.getRegistryManager(), summary);
        (packet.shared() ? SurveyorClient.getSharedExploration() : SurveyorClient.getPersonalExploration()).mergeRegion(world.getRegistryKey(), packet.regionPos(), packet.chunks());
        SurveyorEvents.Invoke.terrainUpdated(world, packet.chunks().stream().mapToObj(i -> RegionSummary.chunkForBit(packet.regionPos(), i)).toList());
    }

    private static void handleGroupChanged(ClientWorld world, WorldSummary summary, S2CGroupChangedPacket packet) {
        if (!SurveyorClient.getSharedExploration().groupPlayers().equals(packet.players().keySet())) {
            SurveyorClient.getSharedExploration().groupPlayers().clear();
            SurveyorClient.getSharedExploration().groupPlayers().addAll(packet.players().keySet());
        }
        NetworkHandlerSummary.of(MinecraftClient.getInstance().getNetworkHandler()).mergeSummaries(packet.players());
        SurveyorClient.getSharedExploration().replaceTerrain(world.getRegistryKey(), packet.regionBits());
        SurveyorClient.getSharedExploration().replaceStructures(world.getRegistryKey(), packet.structureKeys());
        SurveyorClient.getExploration().updateClientForLandmarks(world);
        if (summary != null) {
            new C2SKnownTerrainPacket(summary.terrain().bitSet(null)).send();
            new C2SKnownStructuresPacket(summary.structures().keySet(null)).send();
            new C2SKnownLandmarksPacket(summary.landmarks().keySet(null)).send();
        }
    }

    private static void handleGroupUpdated(ClientWorld world, WorldSummary summary, S2CGroupUpdatedPacket packet) {
        NetworkHandlerSummary.of(MinecraftClient.getInstance().getNetworkHandler()).mergeSummaries(packet.players());
    }

    private static void handleLandmarksAdded(ClientWorld world, WorldSummary summary, PacketByteBuf buf) {
        SyncLandmarksAddedPacket packet = SyncLandmarksAddedPacket.handle(buf, world, summary, null);
    }

    private static void handleLandmarksRemoved(ClientWorld world, WorldSummary summary, SyncLandmarksRemovedPacket packet) {
        Multimap<LandmarkType<?>, BlockPos> changed = HashMultimap.create();
        packet.landmarks().forEach((type, pos) -> summary.landmarks().removeForBatch(changed, type, pos));
        summary.landmarks().handleChanged(world, changed, true, null);
    }

    private static <T extends S2CPacket> void handleClient(PacketByteBuf buf, Function<PacketByteBuf, T> reader, ClientPacketHandler<T> handler) {
        T packet = reader.apply(buf);
        WorldSummary summary = MinecraftClient.getInstance().world == null ? null : WorldSummary.of(MinecraftClient.getInstance().world);
        if (summary != null && !summary.isClient()) return;
        MinecraftClient.getInstance().execute(() -> handler.handle(MinecraftClient.getInstance().world, summary, packet));
    }

    private static void handleClientUnparsed(PacketByteBuf buf, ClientPacketHandler<PacketByteBuf> handler) {
        ClientWorld world = MinecraftClient.getInstance().world;
        WorldSummary summary = MinecraftClient.getInstance().world == null ? null : WorldSummary.of(MinecraftClient.getInstance().world);
        if (summary != null && !summary.isClient()) return;
        handler.handle(world, summary, buf);
    }

    public interface ClientPacketHandler<T> {
        void handle(ClientWorld clientWorld, WorldSummary summary, T packet);
    }
}
