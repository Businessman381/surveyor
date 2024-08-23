package folk.sisby.surveyor.packet;

import folk.sisby.surveyor.PlayerSummary;
import folk.sisby.surveyor.Surveyor;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.gen.structure.Structure;

import java.util.BitSet;
import java.util.Map;
import java.util.UUID;

public record S2CGroupChangedPacket(Map<UUID, PlayerSummary> players, Map<ChunkPos, BitSet> regionBits, Map<RegistryKey<Structure>, LongSet> structureKeys) implements S2CPacket {
	public static final Identifier ID = new Identifier(Surveyor.ID, "s2c_group_changed");

	public static S2CGroupChangedPacket read(PacketByteBuf buf) {
		return new S2CGroupChangedPacket(
			buf.readMap(PacketByteBuf::readUuid, PlayerSummary.OfflinePlayerSummary::readBuf),
			buf.readMap(PacketByteBuf::readChunkPos, PacketByteBuf::readBitSet),
			buf.readMap(
				b -> b.readRegistryKey(RegistryKeys.STRUCTURE),
				b -> new LongOpenHashSet(b.readLongArray())
			)
		);
	}

	@Override
	public void writeBuf(PacketByteBuf buf) {
		buf.writeMap(players, PacketByteBuf::writeUuid, PlayerSummary.OfflinePlayerSummary::writeBuf);
		buf.writeMap(regionBits, PacketByteBuf::writeChunkPos, PacketByteBuf::writeBitSet);
		buf.writeMap(structureKeys, PacketByteBuf::writeRegistryKey, (b, starts) -> b.writeLongArray(starts.toLongArray()));
	}

	@Override
	public Identifier getId() {
		return ID;
	}
}
