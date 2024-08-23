package folk.sisby.surveyor.packet;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import folk.sisby.surveyor.Surveyor;
import folk.sisby.surveyor.structure.StructureStartSummary;
import folk.sisby.surveyor.structure.WorldStructureSummary;
import folk.sisby.surveyor.util.MapUtil;
import io.netty.buffer.Unpooled;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.gen.structure.Structure;
import net.minecraft.world.gen.structure.StructureType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record S2CStructuresAddedPacket(boolean shared, Map<RegistryKey<Structure>, Map<ChunkPos, StructureStartSummary>> structures, Map<RegistryKey<Structure>, RegistryKey<StructureType<?>>> types, Multimap<RegistryKey<Structure>, TagKey<Structure>> tags) implements S2CPacket {
	public static final Identifier ID = new Identifier(Surveyor.ID, "s2c_structures_added");

	public static S2CStructuresAddedPacket of(boolean shared, Multimap<RegistryKey<Structure>, ChunkPos> keys, WorldStructureSummary summary) {
		return summary.createUpdatePacket(shared, keys);
	}

	public static S2CStructuresAddedPacket of(boolean shared, RegistryKey<Structure> key, ChunkPos pos, WorldStructureSummary summary) {
		return of(shared, MapUtil.asMultiMap(Map.of(key, List.of(pos))), summary);
	}

	public static S2CStructuresAddedPacket read(PacketByteBuf buf) {
		return new S2CStructuresAddedPacket(
			buf.readBoolean(),
			buf.readMap(
				b -> b.readRegistryKey(RegistryKeys.STRUCTURE),
				b -> b.readMap(
					PacketByteBuf::readChunkPos,
					b2 -> new StructureStartSummary(b2.readList(b3 -> WorldStructureSummary.readStructurePieceNbt(Objects.requireNonNull(b3.readNbt()))))
				)
			),
			buf.readMap(
				b -> b.readRegistryKey(RegistryKeys.STRUCTURE),
				b -> b.readRegistryKey(RegistryKeys.STRUCTURE_TYPE)
			),
			MapUtil.asMultiMap(buf.readMap(
				b -> b.readRegistryKey(RegistryKeys.STRUCTURE),
				b -> b.readList(b2 -> TagKey.of(RegistryKeys.STRUCTURE, b2.readIdentifier()))
			))
		);
	}

	@Override
	public void writeBuf(PacketByteBuf buf) {
		buf.writeBoolean(shared);
		buf.writeMap(structures,
			PacketByteBuf::writeRegistryKey,
			(b, posMap) -> b.writeMap(posMap,
				PacketByteBuf::writeChunkPos,
				(b2, summary) -> b2.writeCollection(summary.getChildren(), (b3, piece) -> b3.writeNbt(piece.toNbt()))
			)
		);
		buf.writeMap(types,
			PacketByteBuf::writeRegistryKey,
			PacketByteBuf::writeRegistryKey
		);
		buf.writeMap(tags.asMap(),
			PacketByteBuf::writeRegistryKey,
			(b, c) -> b.writeCollection(c, (b2, t) -> b2.writeIdentifier(t.id()))
		);
	}

	@Override
	public Collection<PacketByteBuf> toBufs() {
		List<PacketByteBuf> bufs = new ArrayList<>();
		PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
		writeBuf(buf);
		if (buf.readableBytes() < MAX_PAYLOAD_SIZE) {
			bufs.add(buf);
		} else {
			Multimap<RegistryKey<Structure>, ChunkPos> keySet = MapUtil.keyMultiMap(structures);
			if (keySet.size() == 1) {
				Surveyor.LOGGER.error("Couldn't create a structure update packet for {} - an individual structure would be too large to send!", keySet.keys().stream().findFirst().orElseThrow().getValue());
				return List.of();
			}
			Multimap<RegistryKey<Structure>, ChunkPos> firstHalf = HashMultimap.create();
			Multimap<RegistryKey<Structure>, ChunkPos> secondHalf = HashMultimap.create();
			keySet.forEach((key, pos) -> {
				if (firstHalf.size() < keySet.size() / 2) {
					firstHalf.put(key, pos);
				} else {
					secondHalf.put(key, pos);
				}
			});
			bufs.addAll(new S2CStructuresAddedPacket(shared, MapUtil.splitByKeyMap(structures, firstHalf), MapUtil.splitByKeySet(types, firstHalf.keySet()), MapUtil.asMultiMap(MapUtil.splitByKeySet(tags.asMap(), firstHalf.keySet()))).toBufs());
			bufs.addAll(new S2CStructuresAddedPacket(shared, MapUtil.splitByKeyMap(structures, secondHalf), MapUtil.splitByKeySet(types, secondHalf.keySet()), MapUtil.asMultiMap(MapUtil.splitByKeySet(tags.asMap(), secondHalf.keySet()))).toBufs());
		}
		return bufs;
	}

	@Override
	public Identifier getId() {
		return ID;
	}
}
