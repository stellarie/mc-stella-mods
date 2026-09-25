package com.stella.villages.data;

import com.mojang.serialization.Codec;
import com.stella.villages.McStellaVillages;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

public final class VillageWorldData extends SavedData {
	private static final int DATA_VERSION = 1;
	private static final String VILLAGES_TAG = "villages";
	public static final Codec<VillageWorldData> CODEC = CompoundTag.CODEC.xmap(
		VillageWorldData::loadState,
		VillageWorldData::serializeState
	);
	public static final SavedDataType<VillageWorldData> TYPE = new SavedDataType<>(
		Identifier.fromNamespaceAndPath(McStellaVillages.MOD_ID, "village_world"),
		VillageWorldData::new,
		CODEC,
		DataFixTypes.LEVEL
	);

	private final Map<UUID, VillageRecord> villages = new LinkedHashMap<>();
	private long lastAttemptedDay = -1L;

	public VillageWorldData() {
	}

	public static VillageWorldData empty() {
		return new VillageWorldData();
	}

	public static VillageWorldData get(final ServerLevel level) {
		return get(level.getServer());
	}

	public static VillageWorldData get(final MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(TYPE);
	}

	public VillageRecord findOrCreate(final ResourceKey<Level> dimension, final BlockPos position) {
		Optional<VillageRecord> existing = findNearest(dimension, position);
		if (existing.isPresent()) {
			return existing.get();
		}

		VillageRecord created = new VillageRecord(UUID.randomUUID(), dimension, position.immutable());
		this.villages.put(created.id(), created);
		setDirty();
		McStellaVillages.LOGGER.info(
			"[BEHAVIOR] mod={} event=village_registered village={} dimension={} anchor={}",
			McStellaVillages.MOD_ID, created.id(), dimension.identifier(), created.anchor()
		);
		return created;
	}

	public Optional<VillageRecord> findNearest(final ResourceKey<Level> dimension, final BlockPos position) {
		long maxDistance = PoiManager.MAX_VILLAGE_DISTANCE;
		return this.villages.values().stream()
			.filter(village -> village.dimension().equals(dimension))
			.map(village -> Map.entry(village, sectionDistance(position, village.anchor())))
			.filter(entry -> entry.getValue() <= maxDistance)
			.min(Comparator.<Map.Entry<VillageRecord, Long>>comparingLong(Map.Entry::getValue)
				.thenComparing(entry -> entry.getKey().id().toString()))
			.map(Map.Entry::getKey);
	}

	public List<VillageRecord> villages() {
		return List.copyOf(this.villages.values());
	}

	public Optional<VillageRecord> village(final UUID id) {
		return Optional.ofNullable(this.villages.get(id));
	}

	public void markRosterStarted(final VillageRecord village) {
		if (!village.rosterStarted) {
			village.rosterStarted = true;
			setDirty();
		}
	}

	public void addMember(
		final VillageRecord village,
		final UUID entityId,
		final String role,
		final BlockPos position
	) {
		village.members.put(entityId, new MemberRecord(entityId, role, position.immutable(), true));
		setDirty();
	}

	public void updateMemberPosition(final UUID entityId, final BlockPos position) {
		for (VillageRecord village : this.villages.values()) {
			MemberRecord member = village.members.get(entityId);
			if (member != null) {
				village.members.put(entityId, member.withPosition(position.immutable()));
				setDirty();
				return;
			}
		}
	}

	public void markMemberDead(final UUID entityId) {
		for (VillageRecord village : this.villages.values()) {
			MemberRecord member = village.members.get(entityId);
			if (member != null && member.alive()) {
				village.members.put(entityId, member.withAlive(false));
				if (village.members.values().stream().noneMatch(MemberRecord::alive)) {
					village.threats.clear();
				}
				setDirty();
				return;
			}
		}
	}

	public Optional<VillageRecord> villageForMember(final UUID entityId) {
		return this.villages.values().stream().filter(village -> village.members.containsKey(entityId)).findFirst();
	}

	public void startThreat(final VillageRecord village, final UUID attackerId) {
		if (!village.threats.containsKey(attackerId)) {
			village.threats.put(attackerId, new ThreatRecord(attackerId, 0));
			setDirty();
		}
	}

	public void updateThreat(final VillageRecord village, final UUID attackerId, final int outsideTicks) {
		village.threats.put(attackerId, new ThreatRecord(attackerId, outsideTicks));
		setDirty();
	}

	public void clearThreat(final VillageRecord village, final UUID attackerId) {
		if (village.threats.remove(attackerId) != null) {
			setDirty();
		}
	}

	public long lastAttemptedDay() {
		return this.lastAttemptedDay;
	}

	public void setLastAttemptedDay(final long day) {
		if (this.lastAttemptedDay != day) {
			this.lastAttemptedDay = day;
			setDirty();
		}
	}

	public CompoundTag serializeState() {
		CompoundTag root = new CompoundTag();
		root.putInt("data_version", DATA_VERSION);
		root.putLong("last_attempted_day", this.lastAttemptedDay);
		ListTag records = new ListTag();
		this.villages.values().stream()
			.sorted(Comparator.comparing(village -> village.id().toString()))
			.map(VillageWorldData::encodeVillage)
			.forEach(records::add);
		root.put(VILLAGES_TAG, records);
		return root;
	}

	public static VillageWorldData loadState(final CompoundTag root) {
		VillageWorldData data = new VillageWorldData();
		data.lastAttemptedDay = root.getLongOr("last_attempted_day", -1L);
		for (Tag value : root.getListOrEmpty(VILLAGES_TAG)) {
			if (value instanceof CompoundTag villageTag) {
				try {
					VillageRecord village = decodeVillage(villageTag);
					data.villages.put(village.id(), village);
				} catch (IllegalArgumentException error) {
					McStellaVillages.LOGGER.warn("[BEHAVIOR] mod={} event=saved_village_skipped detail={}", McStellaVillages.MOD_ID, error.toString());
				}
			}
		}

		return data;
	}

	private static long sectionDistance(final BlockPos first, final BlockPos second) {
		long dx = SectionPos.blockToSectionCoord(first.getX()) - SectionPos.blockToSectionCoord(second.getX());
		long dy = SectionPos.blockToSectionCoord(first.getY()) - SectionPos.blockToSectionCoord(second.getY());
		long dz = SectionPos.blockToSectionCoord(first.getZ()) - SectionPos.blockToSectionCoord(second.getZ());
		return Math.max(Math.max(Math.abs(dx), Math.abs(dy)), Math.abs(dz));
	}

	private static CompoundTag encodeVillage(final VillageRecord village) {
		CompoundTag tag = new CompoundTag();
		tag.putString("id", village.id().toString());
		tag.putString("dimension", village.dimension().identifier().toString());
		tag.put("anchor", BlockPos.CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, village.anchor()).getOrThrow());
		tag.putBoolean("roster_started", village.rosterStarted);

		ListTag members = new ListTag();
		village.members.values().stream()
			.sorted(Comparator.comparing(member -> member.id().toString()))
			.map(VillageWorldData::encodeMember)
			.forEach(members::add);
		tag.put("members", members);

		ListTag threats = new ListTag();
		village.threats.values().stream()
			.sorted(Comparator.comparing(threat -> threat.attackerId().toString()))
			.map(VillageWorldData::encodeThreat)
			.forEach(threats::add);
		tag.put("threats", threats);
		return tag;
	}

	private static VillageRecord decodeVillage(final CompoundTag tag) {
		UUID id = UUID.fromString(tag.getStringOr("id", ""));
		ResourceKey<Level> dimension = ResourceKey.create(
			Registries.DIMENSION,
			Identifier.parse(tag.getStringOr("dimension", "minecraft:overworld"))
		);
		BlockPos anchor = tag.read("anchor", BlockPos.CODEC).orElse(BlockPos.ZERO);
		VillageRecord village = new VillageRecord(id, dimension, anchor);
		village.rosterStarted = tag.getBooleanOr("roster_started", false);

		for (Tag memberTag : tag.getListOrEmpty("members")) {
			if (memberTag instanceof CompoundTag member) {
				MemberRecord decoded = decodeMember(member);
				village.members.put(decoded.id(), decoded);
			}
		}

		for (Tag threatTag : tag.getListOrEmpty("threats")) {
			if (threatTag instanceof CompoundTag threat) {
				ThreatRecord decoded = decodeThreat(threat);
				village.threats.put(decoded.attackerId(), decoded);
			}
		}

		return village;
	}

	private static CompoundTag encodeMember(final MemberRecord member) {
		CompoundTag tag = new CompoundTag();
		tag.putString("id", member.id().toString());
		tag.putString("role", member.role());
		tag.put("position", BlockPos.CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, member.lastPosition()).getOrThrow());
		tag.putBoolean("alive", member.alive());
		return tag;
	}

	private static MemberRecord decodeMember(final CompoundTag tag) {
		return new MemberRecord(
			UUID.fromString(tag.getStringOr("id", "")),
			tag.getStringOr("role", "member"),
			tag.read("position", BlockPos.CODEC).orElse(BlockPos.ZERO),
			tag.getBooleanOr("alive", true)
		);
	}

	private static CompoundTag encodeThreat(final ThreatRecord threat) {
		CompoundTag tag = new CompoundTag();
		tag.putString("attacker", threat.attackerId().toString());
		tag.putInt("outside_ticks", threat.outsideTicks());
		return tag;
	}

	private static ThreatRecord decodeThreat(final CompoundTag tag) {
		return new ThreatRecord(UUID.fromString(tag.getStringOr("attacker", "")), Math.max(0, tag.getIntOr("outside_ticks", 0)));
	}

	public static final class VillageRecord {
		private final UUID id;
		private final ResourceKey<Level> dimension;
		private final BlockPos anchor;
		private final Map<UUID, MemberRecord> members = new LinkedHashMap<>();
		private final Map<UUID, ThreatRecord> threats = new LinkedHashMap<>();
		private boolean rosterStarted;

		private VillageRecord(final UUID id, final ResourceKey<Level> dimension, final BlockPos anchor) {
			this.id = id;
			this.dimension = dimension;
			this.anchor = anchor.immutable();
		}

		public UUID id() {
			return this.id;
		}

		public ResourceKey<Level> dimension() {
			return this.dimension;
		}

		public BlockPos anchor() {
			return this.anchor;
		}

		public boolean rosterStarted() {
			return this.rosterStarted;
		}

		public List<MemberRecord> members() {
			return List.copyOf(this.members.values());
		}

		public List<ThreatRecord> threats() {
			return List.copyOf(this.threats.values());
		}
	}

	public record MemberRecord(UUID id, String role, BlockPos lastPosition, boolean alive) {
		public MemberRecord withPosition(final BlockPos position) {
			return new MemberRecord(this.id, this.role, position.immutable(), this.alive);
		}

		public MemberRecord withAlive(final boolean value) {
			return new MemberRecord(this.id, this.role, this.lastPosition, value);
		}
	}

	public record ThreatRecord(UUID attackerId, int outsideTicks) {
	}
}
