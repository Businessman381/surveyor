package folk.sisby.surveyor.packet;

import folk.sisby.surveyor.ServerSummary;
import folk.sisby.surveyor.Surveyor;
import folk.sisby.surveyor.config.NetworkMode;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public interface S2CPacket extends SurveyorPacket {
    default void send(Collection<ServerPlayerEntity> players) {
        List<SurveyorPacket> split = this.toPayloads();
        if (split.isEmpty()) return;
        for (ServerPlayerEntity player : players) {
            if (!ServerPlayNetworking.canSend(player, getId()) || player.getServer().isHost(player.getGameProfile())) continue;
            split.forEach(p -> ServerPlayNetworking.send(player, p));
        }
    }

    default void send(ServerPlayerEntity player) {
        send(List.of(player));
    }

    default void send(ServerWorld world) {
        send(world.getPlayers());
    }

    default void send(ServerPlayerEntity sender, ServerWorld world) {
        List<ServerPlayerEntity> players = new ArrayList<>(world.getPlayers());
        players.remove(sender);
        send(players);
    }

    default void send(ServerPlayerEntity sender, ServerWorld world, NetworkMode mode) {
        if (mode.atMost(NetworkMode.SOLO)) return;
        List<ServerPlayerEntity> players = new ArrayList<>(world.getPlayers());
        players.remove(sender);
        if (mode.atMost(NetworkMode.GROUP)) ServerSummary.of(world.getServer()).groupOtherServerPlayers(Surveyor.getUuid(sender), world.getServer()).forEach(players::remove);
        send(players);
    }

    default void send(MinecraftServer server) {
        send(server.getPlayerManager().getPlayerList());
    }

    default void send(ServerPlayerEntity sender, MinecraftServer server) {
        List<ServerPlayerEntity> players = new ArrayList<>(server.getPlayerManager().getPlayerList());
        players.remove(sender);
        send(players);
    }
}
