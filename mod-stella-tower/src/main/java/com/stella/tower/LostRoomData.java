package com.stella.tower;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jspecify.annotations.Nullable;

/**
 * Every lost room's instance, saved with the overworld: which hut leads to it, its room in the tower, the
 * level its mobs were raised from, whether its boss has fallen, and the spot beside the hut's portal.
 */
public final class LostRoomData extends SavedData {
	/** One hut's instance. */
	public record Instance(String hut, int room, int level, boolean cleared, TowerTravel.Entry back) {
		public static final Codec<Instance> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.STRING.fieldOf("hut").forGetter(Instance::hut),
			Codec.INT.fieldOf("room").forGetter(Instance::room),
			Codec.INT.fieldOf("level").forGetter(Instance::level),
			Codec.BOOL.fieldOf("cleared").forGetter(Instance::cleared),
			TowerTravel.Entry.CODEC.fieldOf("back").forGetter(Instance::back)
		).apply(instance, Instance::new));
	}

	/** One castle on the map: where its box stands, which way it faces, and whether its grounds were stocked. */
	public record Castle(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension, net.minecraft.core.BlockPos corner,
		net.minecraft.core.Direction facing, boolean stocked) {
		public static final Codec<Castle> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			net.minecraft.world.level.Level.RESOURCE_KEY_CODEC.fieldOf("dimension").forGetter(Castle::dimension),
			net.minecraft.core.BlockPos.CODEC.fieldOf("corner").forGetter(Castle::corner),
			net.minecraft.core.Direction.CODEC.fieldOf("facing").forGetter(Castle::facing),
			Codec.BOOL.fieldOf("stocked").forGetter(Castle::stocked)
		).apply(instance, Castle::new));

		public String key() {
			return this.dimension.identifier() + "@" + this.corner.toShortString();
		}
	}

	private record Snapshot(int rooms, Map<String, Instance> huts, Map<String, Castle> castles) {
		private static final Codec<Snapshot> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.INT.fieldOf("rooms").forGetter(Snapshot::rooms),
			Codec.unboundedMap(Codec.STRING, Instance.CODEC).fieldOf("huts").forGetter(Snapshot::huts),
			Codec.unboundedMap(Codec.STRING, Castle.CODEC).optionalFieldOf("castles", Map.of()).forGetter(Snapshot::castles)
		).apply(instance, Snapshot::new));
	}

	public static final Codec<LostRoomData> CODEC = Snapshot.CODEC.xmap(
		snapshot -> new LostRoomData(snapshot.rooms(), snapshot.huts(), snapshot.castles()),
		data -> new Snapshot(data.rooms, Map.copyOf(data.huts), Map.copyOf(data.castles))
	);
	public static final SavedDataType<LostRoomData> TYPE = new SavedDataType<>(
		StellaTower.id("lost_rooms"), LostRoomData::new, CODEC, DataFixTypes.LEVEL
	);

	private int rooms;
	private final Map<String, Instance> huts = new HashMap<>();
	private final Map<Integer, Instance> byRoom = new HashMap<>();
	private final Map<String, Castle> castles = new HashMap<>();

	public LostRoomData() {
	}

	private LostRoomData(final int rooms, final Map<String, Instance> huts, final Map<String, Castle> castles) {
		this.rooms = rooms;
		huts.values().forEach(this::put);
		this.castles.putAll(castles);
	}

	/** Records a castle once; a castle already known keeps its record. */
	public void found(final Castle castle) {
		if (this.castles.putIfAbsent(castle.key(), castle) == null) {
			setDirty();
		}
	}

	public @Nullable Castle castle(final String key) {
		return this.castles.get(key);
	}

	/** The castles whose grounds no player has come near yet. */
	public java.util.List<Castle> unstocked() {
		return this.castles.values().stream().filter(castle -> !castle.stocked()).toList();
	}

	public void stocked(final Castle castle) {
		this.castles.put(castle.key(), new Castle(castle.dimension(), castle.corner(), castle.facing(), true));
		setDirty();
	}

	public static LostRoomData get(final MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(TYPE);
	}

	public @Nullable Instance hut(final String hut) {
		return this.huts.get(hut);
	}

	public @Nullable Instance room(final int room) {
		return this.byRoom.get(room);
	}

	/** A new instance in the next free room. */
	public Instance open(final String hut, final int level, final TowerTravel.Entry back) {
		this.rooms++;
		Instance made = new Instance(hut, -this.rooms, level, false, back);
		put(made);
		return made;
	}

	public void put(final Instance instance) {
		this.huts.put(instance.hut(), instance);
		this.byRoom.put(instance.room(), instance);
		setDirty();
	}

	/** Test seam: forgets a hut, so its next entry opens a new instance. */
	void forget(final String hut) {
		Instance gone = this.huts.remove(hut);
		if (gone != null) {
			this.byRoom.remove(gone.room());
			setDirty();
		}
	}
}
