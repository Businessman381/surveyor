package folk.sisby.surveyor;

import com.mojang.authlib.GameProfile;
import folk.sisby.surveyor.config.NetworkMode;
import folk.sisby.surveyor.packet.S2CGroupChangedPacket;
import folk.sisby.surveyor.packet.S2CGroupUpdatedPacket;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.WorldSavePath;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public final class ServerSummary {
    public static ServerSummary of(MinecraftServer server) {
        return ((SurveyorServer) server).surveyor$getSummary();
    }

    public static final String KEY_GROUPS = "groups";
    public static final UUID HOST = UUID.fromString("00000000-0000-0000-0000-000000000000");

    private final Map<UUID, PlayerSummary> offlineSummaries;
    private final Map<UUID, Set<UUID>> shareGroups;
    private boolean dirty = false;

    public ServerSummary(Map<UUID, PlayerSummary> offlineSummaries, @Nullable Map<UUID, Set<UUID>> shareGroups) {
        this.offlineSummaries = offlineSummaries;
        this.shareGroups = shareGroups;
    }

    public static Map<UUID, Set<UUID>> loadShareGroups(MinecraftServer server) {
        File folder = Surveyor.getSavePath(World.OVERWORLD, server);
        NbtCompound sharingNbt = new NbtCompound();
        File sharingFile = new File(folder, "sharing.dat");
        if (sharingFile.exists()) {
            try {
                sharingNbt = NbtIo.readCompressed(sharingFile);
            } catch (IOException e) {
                Surveyor.LOGGER.error("[Surveyor] Error loading sharing file.", e);
            }
        }
        Map<UUID, Set<UUID>> shareGroups = new ConcurrentHashMap<>();
        sharingNbt.getList(KEY_GROUPS, NbtElement.LIST_TYPE).stream().map(l -> ((NbtList) l).stream().map(s -> UUID.fromString(s.asString())).collect(Collectors.toCollection(HashSet::new))).forEach(set -> {
            for (UUID uuid : set) {
                shareGroups.put(uuid, set);
            }
        });
        return shareGroups;
    }

    public static ServerSummary load(MinecraftServer server) {
        Map<UUID, Set<UUID>> shareGroups = Surveyor.CONFIG.networking.globalSharing ? null : loadShareGroups(server);

        File playerFolder = server.getSavePath(WorldSavePath.PLAYERDATA).toFile();

        Map<UUID, PlayerSummary> offlineSummaries = new ConcurrentHashMap<>();

        NbtCompound hostData = server.getSaveProperties().getPlayerData();
        UUID hostProfile = Optional.ofNullable(server.getHostProfile()).map(GameProfile::getId).orElse(null);
        if (hostData != null) {
            if (hostProfile != null) hostData.putString(PlayerSummary.KEY_USERNAME, server.getHostProfile().getName());
            offlineSummaries.put(ServerSummary.HOST, new PlayerSummary.OfflinePlayerSummary(ServerSummary.HOST, hostData, false));
        }

        for (File file : Optional.ofNullable(playerFolder.listFiles((dir, name) -> name.endsWith(".dat"))).orElse(new File[0])) {
            UUID uuid;
            try {
                uuid = UUID.fromString(file.getName().substring(0, file.getName().length() - ".dat".length()));
                if (uuid.equals(hostProfile)) continue;
            } catch (IllegalArgumentException ex) {
                continue;
            }
            if (shareGroups != null && !shareGroups.containsKey(uuid)) continue;
            try {
                NbtCompound playerNbt = NbtIo.readCompressed(file);
                offlineSummaries.put(uuid, new PlayerSummary.OfflinePlayerSummary(uuid, playerNbt, false));
            } catch (IOException e) {
                Surveyor.LOGGER.error("[Surveyor] Error loading offline player data for {}!", uuid, e);
            }
        }

        if (shareGroups != null) {
            for (UUID uuid : shareGroups.keySet()) {
                if (!offlineSummaries.containsKey(uuid)) {
                    Surveyor.LOGGER.warn("[Surveyor] Player data was missing for shared player {}! Removing from groups...", uuid);
                    shareGroups.get(uuid).remove(uuid);
                    shareGroups.remove(uuid);
                }
            }
        }

        return new ServerSummary(offlineSummaries, shareGroups);
    }

    public void save(MinecraftServer server, boolean force, boolean suppressLogs) {
        if (!isDirty() && StreamSupport.stream(server.getWorlds().spliterator(), false).map(WorldSummary::of).noneMatch(WorldSummary::isDirty)) return;
        if (!suppressLogs) Surveyor.LOGGER.info("[Surveyor] Saving server data...");
        for (ServerWorld world : server.getWorlds()) {
            if (!world.savingDisabled || force) {
                WorldSummary.of(world).save(world, Surveyor.getSavePath(world.getRegistryKey(), server), suppressLogs);
            }
        }
        File folder = Surveyor.getSavePath(World.OVERWORLD, server);
        if (isDirty()) {
            File sharingFile = new File(folder, "sharing.dat");
            try {
                NbtIo.writeCompressed(writeNbt(new NbtCompound()), sharingFile);
                dirty = false;
            } catch (IOException e) {
                Surveyor.LOGGER.error("[Surveyor] Error writing sharing file.", e);
            }
        }
        if (!suppressLogs) Surveyor.LOGGER.info("[Surveyor] Finished saving server data.");
    }

    private NbtCompound writeNbt(NbtCompound nbt) {
        nbt.put(KEY_GROUPS, new NbtList(getGroups().stream().filter(s -> s.size() > 1).map(s -> (NbtElement) new NbtList(s.stream().map(u -> (NbtElement) NbtString.of(u.toString())).toList(), NbtElement.STRING_TYPE)).toList(), NbtElement.LIST_TYPE));
        return nbt;
    }

    public PlayerSummary getPlayer(UUID uuid, MinecraftServer server) {
        ServerPlayerEntity player = Surveyor.getPlayer(server, uuid);
        if (player != null) {
            return PlayerSummary.of(player);
        } else {
            return offlineSummaries.get(uuid);
        }
    }

    public SurveyorExploration getExploration(UUID player, MinecraftServer server) {
        PlayerSummary summary = getPlayer(player, server);
        return summary == null ? null : summary.exploration();
    }

    public void updatePlayer(UUID uuid, NbtCompound nbt, boolean online, MinecraftServer server) {
        PlayerSummary newSummary = new PlayerSummary.OfflinePlayerSummary(uuid, nbt, online);
        offlineSummaries.put(uuid, newSummary);
        for (ServerPlayerEntity friend : groupOtherServerPlayers(uuid, server)) {
            S2CGroupUpdatedPacket.of(uuid, newSummary).send(friend);
        }
    }

    public Set<Set<UUID>> getGroups() {
        return shareGroups == null ? new HashSet<>() : new HashSet<>(shareGroups.values());
    }

    public Map<UUID, PlayerSummary> getOfflineSummaries(MinecraftServer server) {
        return offlineSummaries.keySet().stream().filter(u -> getPlayer(u, server) != null).collect(Collectors.toMap(u -> u, u -> getPlayer(u, server)));
    }

    public Set<UUID> getGroup(UUID player) {
        return shareGroups == null ? new HashSet<>(offlineSummaries.keySet()) : shareGroups.computeIfAbsent(player, p -> new HashSet<>(Set.of(p)));
    }

    public Map<UUID, PlayerSummary> getGroupSummaries(UUID player, MinecraftServer server) {
        return getGroup(player).stream().filter(u -> getPlayer(u, server) != null).collect(Collectors.toMap(u -> u, u -> getPlayer(u, server)));
    }

    public void joinGroup(UUID player1, UUID player2, MinecraftServer server) {
        if (shareGroups == null) return;
        if (getGroup(player1).size() > 1 && getGroup(player2).size() > 1) throw new IllegalStateException("Can't merge two groups!");
        if (getGroup(player1).size() > 1) {
            getGroup(player1).add(player2);
            shareGroups.put(player2, getGroup(player1));
        } else {
            getGroup(player2).add(player1);
            shareGroups.put(player1, getGroup(player2));
        }
        SurveyorExploration groupExploration = groupExploration(player1, server);
        for (ServerPlayerEntity friend : groupServerPlayers(player1, server)) {
            new S2CGroupChangedPacket(getGroupSummaries(player1, server), groupExploration.terrain().getOrDefault(friend.getWorld().getRegistryKey(), new HashMap<>()), groupExploration.structures().getOrDefault(friend.getWorld().getRegistryKey(), new HashMap<>())).send(friend);
        }
        dirty();
    }

    public void leaveGroup(UUID player, MinecraftServer server) {
        if (shareGroups == null) return;
        getGroup(player).remove(player); // Shares set instance with group members.
        SurveyorExploration groupExploration = groupExploration(player, server);
        for (ServerPlayerEntity friend : groupOtherServerPlayers(player, server)) {
            new S2CGroupChangedPacket(getGroupSummaries(Surveyor.getUuid(friend), server), groupExploration.terrain().getOrDefault(friend.getWorld().getRegistryKey(), new HashMap<>()), groupExploration.structures().getOrDefault(friend.getWorld().getRegistryKey(), new HashMap<>())).send(friend);
        }
        shareGroups.put(player, new HashSet<>());
        getGroup(player).add(player);
        ServerPlayerEntity serverPlayer = Surveyor.getPlayer(server, player);
        if (serverPlayer != null) new S2CGroupChangedPacket(getGroupSummaries(player, server), new HashMap<>(), new HashMap<>()).send(serverPlayer);
        dirty();
    }

    public int groupSize(UUID player) {
        return getGroup(player).size();
    }

    public Set<PlayerSummary> groupPlayers(UUID player, MinecraftServer server) {
        return getGroup(player).stream().map(u -> getPlayer(u, server)).collect(Collectors.toSet());
    }

    public SurveyorExploration groupExploration(UUID player, MinecraftServer server) {
        return PlayerSummary.OfflinePlayerSummary.OfflinePlayerExploration.ofMerged(getGroup(player).stream().map(u -> getExploration(u, server)).filter(Objects::nonNull).collect(Collectors.toSet()));
    }

    public Set<ServerPlayerEntity> groupServerPlayers(UUID player, MinecraftServer server) {
        return getGroup(player).stream().map(uuid -> Surveyor.getPlayer(server, player)).filter(Objects::nonNull).collect(Collectors.toSet());
    }

    public Set<ServerPlayerEntity> groupOtherServerPlayers(UUID player, MinecraftServer server) {
        return getGroup(player).stream().filter(u -> !u.equals(player)).map(server.getPlayerManager()::getPlayer).filter(Objects::nonNull).collect(Collectors.toSet());
    }

    public static void onPlayerJoin(ServerPlayNetworkHandler handler, PacketSender sender, MinecraftServer server) {
        ServerSummary serverSummary = ServerSummary.of(server);
        UUID uuid = Surveyor.getUuid(handler.player);
        boolean known = serverSummary.offlineSummaries.containsKey(uuid);
        serverSummary.updatePlayer(uuid, handler.player.writeNbt(new NbtCompound()), true, server);
        if (serverSummary.groupSize(uuid) > 1) {
            SurveyorExploration groupExploration = serverSummary.groupExploration(uuid, server);
            new S2CGroupChangedPacket(serverSummary.getGroupSummaries(uuid, server), groupExploration.terrain().getOrDefault(handler.player.getWorld().getRegistryKey(), new HashMap<>()), groupExploration.structures().getOrDefault(handler.player.getWorld().getRegistryKey(), new HashMap<>())).send(handler.player);
            if (!known && Surveyor.CONFIG.networking.globalSharing) {
                for (ServerPlayerEntity friend : serverSummary.groupOtherServerPlayers(uuid, server)) {
                    new S2CGroupChangedPacket(serverSummary.getGroupSummaries(uuid, server), groupExploration.terrain().getOrDefault(friend.getWorld().getRegistryKey(), new HashMap<>()), groupExploration.structures().getOrDefault(friend.getWorld().getRegistryKey(), new HashMap<>())).send(friend);
                }
            }
        }
    }

    public static void onTick(MinecraftServer server) {
        if (Surveyor.CONFIG.networking.positions.atMost(NetworkMode.SOLO) || (server.getTicks() % Surveyor.CONFIG.networking.positionTicks) != 0) return;
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            Map<UUID, PlayerSummary> group = Surveyor.CONFIG.networking.positions.atLeast(NetworkMode.SERVER) ? ServerSummary.of(server).getOfflineSummaries(server) : ServerSummary.of(server).getGroupSummaries(Surveyor.getUuid(player), server);
            PlayerSummary playerSummary = group.get(Surveyor.getUuid(player));
            group.entrySet().removeIf(e -> e.getKey().equals(Surveyor.getUuid(player)));
            group.entrySet().removeIf(e -> !e.getValue().online());
            group.entrySet().removeIf(e -> !e.getValue().dimension().equals(playerSummary.dimension()));
            group.entrySet().removeIf(e -> {
                ServerPlayerEntity friend = server.getPlayerManager().getPlayer(e.getKey());
                return (friend == null || !friend.isSpectator()) && e.getValue().pos().squaredDistanceTo(playerSummary.pos()) < ((playerSummary.viewDistance() * playerSummary.viewDistance() + 1) << 4);
            });
            if (!group.isEmpty()) new S2CGroupUpdatedPacket(group).send(player);
        }
    }

    public boolean isDirty() {
        return dirty && shareGroups != null;
    }

    private void dirty() {
        dirty = true;
    }
}
