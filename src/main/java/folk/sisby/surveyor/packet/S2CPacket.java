package folk.sisby.surveyor.packet;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public interface S2CPacket extends SurveyorPacket {
    default void send(Collection<ServerPlayerEntity> players) {
        Collection<PacketByteBuf> bufs = null;
        for (ServerPlayerEntity player : players) {
            if (!ServerPlayNetworking.canSend(player, getId()) || player.getServer().isHost(player.getGameProfile())) continue;
            if (bufs == null) bufs = toBufs();
            bufs.forEach(buf -> ServerPlayNetworking.send(player, getId(), buf));
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

    default void send(MinecraftServer server) {
        send(server.getPlayerManager().getPlayerList());
    }

    default void send(ServerPlayerEntity sender, MinecraftServer server) {
        List<ServerPlayerEntity> players = new ArrayList<>(server.getPlayerManager().getPlayerList());
        players.remove(sender);
        send(players);
    }
}
