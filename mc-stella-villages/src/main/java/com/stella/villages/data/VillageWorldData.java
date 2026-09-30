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
import net.minecraft.world.level.saveddata.maps.MapId;

public final class VillageWorldData extends SavedData {
	private static final int DATA_VERSION = 1;
	/** Version 3 is a head, a witch and two melee Pillagers. */
	public static final int ROSTER_VERSION = 3;
	private static final int LEGACY_ROSTER_VERSION = 1;
	/** Marks a village whose shop has seen no midnight yet. */
	public static final long NO_MIDNIGHT = Long.MIN_VALUE;
	private static final String VILLAGES_TAG = "villages";
	private static final String MISSIONS_TAG = "missions";
	private static final String PENDING_KILL_REWARDS_TAG = "pending_kill_rewards";
	private static final ResourceKey<Level> DEFAULT_MISSION_DIMENSION = ResourceKey.create(
		Registries.DIMENSION, Identifier.parse("minecraft:overworld")
	);
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
	private final Map<UUID, MissionRecord> missionRecords = new LinkedHashMap<>();
	private final Map<UUID, Integer> pendingKillRewards = new LinkedHashMap<>();
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

	/** Replaces a village's witch-shop offers. */
	public void setShopOffers(final VillageRecord village, final List<ShopOffer> offers) {
		village.shopOffers = List.copyOf(offers);
		setDirty();
	}

	/** Records the midnight a village's shop last saw, so a reload or a clock jump rerolls it once at most. */
	public void setShopMidnight(final VillageRecord village, final long midnight) {
		if (village.shopMidnight != midnight) {
			village.shopMidnight = midnight;
			setDirty();
		}
	}

	public void markRosterStarted(final VillageRecord village) {
		if (!village.rosterStarted || village.rosterVersion != ROSTER_VERSION) {
			village.rosterStarted = true;
			village.rosterVersion = ROSTER_VERSION;
			setDirty();
		}
	}

	/** Drops a member record outright, for a retired role that must neither count nor respawn. */
	public boolean removeMember(final UUID entityId) {
		for (VillageRecord village : this.villages.values()) {
			if (village.members.remove(entityId) != null) {
				setDirty();
				return true;
			}
		}

		return false;
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

	public void addMission(final MissionRecord mission) {
		this.missionRecords.put(mission.id(), mission);
		setDirty();
	}

	public Optional<MissionRecord> mission(final UUID missionId) {
		return Optional.ofNullable(this.missionRecords.get(missionId));
	}

	public List<MissionRecord> missions() {
		return List.copyOf(this.missionRecords.values());
	}

	/** The ELITE or KILL mission that spawned a mob, by the mob's id, or empty for any other mob. */
	public Optional<MissionRecord> questMissionOf(final UUID mobId) {
		if (this.missionRecords.isEmpty()) {
			return Optional.empty();
		}
		return this.missionRecords.values().stream()
			.filter(mission -> mission.kind() != MissionKind.NEMESIS
				&& (mission.entityId().filter(mobId::equals).isPresent() || mission.mobIds().contains(mobId)))
			.findFirst();
	}

	/** A kill-quest reward the owner was not online to receive; paid at their next login. */
	public void addPendingKillReward(final UUID ownerId) {
		this.pendingKillRewards.merge(ownerId, 1, Integer::sum);
		setDirty();
	}

	/** Takes every kill-quest reward waiting for the owner, and returns how many there were. */
	public int takePendingKillRewards(final UUID ownerId) {
		Integer count = this.pendingKillRewards.remove(ownerId);
		if (count == null) {
			return 0;
		}
		setDirty();
		return count;
	}

	public int pendingKillRewards(final UUID ownerId) {
		return this.pendingKillRewards.getOrDefault(ownerId, 0);
	}

	public boolean removeMission(final UUID missionId) {
		if (this.missionRecords.remove(missionId) != null) {
			setDirty();
			return true;
		}
		return false;
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
		ListTag missionTags = new ListTag();
		this.missionRecords.values().stream()
			.sorted(Comparator.comparing(mission -> mission.id().toString()))
			.map(VillageWorldData::encodeMission)
			.forEach(missionTags::add);
		root.put(MISSIONS_TAG, missionTags);
		ListTag pending = new ListTag();
		this.pendingKillRewards.forEach((owner, count) -> {
			CompoundTag entry = new CompoundTag();
			entry.putString("owner", owner.toString());
			entry.putInt("count", count);
			pending.add(entry);
		});
		root.put(PENDING_KILL_REWARDS_TAG, pending);
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
		for (Tag value : root.getListOrEmpty(MISSIONS_TAG)) {
			if (value instanceof CompoundTag missionTag) {
				try {
					MissionRecord mission = decodeMission(missionTag);
					data.missionRecords.put(mission.id(), mission);
				} catch (IllegalArgumentException error) {
					McStellaVillages.LOGGER.warn("[BEHAVIOR] mod={} event=saved_mission_skipped detail={}", McStellaVillages.MOD_ID, error.toString());
				}
			}
		}
		for (Tag value : root.getListOrEmpty(PENDING_KILL_REWARDS_TAG)) {
			if (value instanceof CompoundTag entry) {
				try {
					int count = entry.getIntOr("count", 0);
					if (count > 0) {
						data.pendingKillRewards.put(UUID.fromString(entry.getStringOr("owner", "")), count);
					}
				} catch (IllegalArgumentException error) {
					McStellaVillages.LOGGER.warn("[BEHAVIOR] mod={} event=saved_reward_skipped detail={}", McStellaVillages.MOD_ID, error.toString());
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
		tag.putInt("roster_version", village.rosterVersion);

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
		ListTag shop = new ListTag();
		village.shopOffers.stream().map(VillageWorldData::encodeShopOffer).forEach(shop::add);
		tag.put("shop_offers", shop);
		if (village.shopMidnight != NO_MIDNIGHT) {
			tag.putLong("shop_midnight", village.shopMidnight);
		}
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
		village.rosterVersion = tag.getIntOr("roster_version", LEGACY_ROSTER_VERSION);
		if (village.rosterVersion < ROSTER_VERSION && village.rosterStarted) {
			// A roster sealed under the old layout reopens, so the next natural load adds only its missing roles.
			village.rosterStarted = false;
			McStellaVillages.LOGGER.info(
				"[BEHAVIOR] mod={} event=roster_migration_reopened village={} from_version={} to_version={}",
				McStellaVillages.MOD_ID, village.id(), village.rosterVersion, ROSTER_VERSION
			);
		}

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

		List<ShopOffer> offers = new ArrayList<>();
		for (Tag offerTag : tag.getListOrEmpty("shop_offers")) {
			if (offerTag instanceof CompoundTag offer) {
				decodeShopOffer(offer).ifPresent(offers::add);
			}
		}
		village.shopOffers = List.copyOf(offers);
		village.shopMidnight = tag.getLongOr("shop_midnight", NO_MIDNIGHT);
		return village;
	}

	private static CompoundTag encodeShopOffer(final ShopOffer offer) {
		CompoundTag tag = new CompoundTag();
		tag.putString("enchantment", offer.enchantment().toString());
		tag.putInt("level", offer.level());
		tag.putInt("price", offer.price());
		return tag;
	}

	private static Optional<ShopOffer> decodeShopOffer(final CompoundTag tag) {
		Identifier enchantment = Identifier.tryParse(tag.getStringOr("enchantment", ""));
		int level = tag.getIntOr("level", 0);
		int price = tag.getIntOr("price", 0);
		return enchantment == null || level < 1 || price < 1 ? Optional.empty() : Optional.of(new ShopOffer(enchantment, level, price));
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

	private static CompoundTag encodeMission(final MissionRecord mission) {
		CompoundTag tag = new CompoundTag();
		tag.putString("id", mission.id().toString());
		tag.putString("owner", mission.ownerId().toString());
		tag.putString("target_type", mission.targetType().toString());
		tag.put("target_position", BlockPos.CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, mission.targetPosition()).getOrThrow());
		mission.entityId().ifPresent(entityId -> tag.putString("entity_id", entityId.toString()));
		mission.mapId().ifPresent(mapId -> tag.put("map_id", MapId.CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, mapId).getOrThrow()));
		tag.putString("status", mission.status().name());
		tag.putString("dimension", mission.dimension().identifier().toString());
		tag.putString("kind", mission.kind().name());
		tag.putInt("remaining", mission.remaining());
		ListTag mobIds = new ListTag();
		mission.mobIds().forEach(mobId -> mobIds.add(net.minecraft.nbt.StringTag.valueOf(mobId.toString())));
		tag.put("mob_ids", mobIds);
		return tag;
	}

	private static MissionRecord decodeMission(final CompoundTag tag) {
		String entityId = tag.getStringOr("entity_id", "");
		Optional<UUID> optionalEntityId = entityId.isEmpty()
			? Optional.empty()
			: Optional.of(UUID.fromString(entityId));
		Optional<MapId> optionalMapId = tag.read("map_id", MapId.CODEC);
		ResourceKey<Level> missionDimension = ResourceKey.create(
			Registries.DIMENSION, Identifier.parse(tag.getStringOr("dimension", "minecraft:overworld"))
		);
		return new MissionRecord(
			UUID.fromString(tag.getStringOr("id", "")),
			UUID.fromString(tag.getStringOr("owner", "")),
			Identifier.parse(tag.getStringOr("target_type", "")),
			tag.read("target_position", BlockPos.CODEC).orElseThrow(() -> new IllegalArgumentException("invalid mission target position")),
			optionalEntityId,
			MissionStatus.valueOf(tag.getStringOr("status", "TARGET_PENDING")),
			optionalMapId,
			missionDimension,
			decodeKind(tag.getStringOr("kind", "")),
			Math.max(0, tag.getIntOr("remaining", 0)),
			decodeMobIds(tag)
		);
	}

	// A record saved before quest kinds existed has none of these fields, and it is a Nemesis.
	private static MissionKind decodeKind(final String name) {
		try {
			return MissionKind.valueOf(name);
		} catch (IllegalArgumentException error) {
			return MissionKind.NEMESIS;
		}
	}

	private static List<UUID> decodeMobIds(final CompoundTag tag) {
		List<UUID> ids = new ArrayList<>();
		for (Tag value : tag.getListOrEmpty("mob_ids")) {
			value.asString().ifPresent(text -> ids.add(UUID.fromString(text)));
		}
		return List.copyOf(ids);
	}

	public static final class VillageRecord {
		private final UUID id;
		private final ResourceKey<Level> dimension;
		private final BlockPos anchor;
		private final Map<UUID, MemberRecord> members = new LinkedHashMap<>();
		private final Map<UUID, ThreatRecord> threats = new LinkedHashMap<>();
		private boolean rosterStarted;
		private int rosterVersion = ROSTER_VERSION;
		private List<ShopOffer> shopOffers = List.of();
		private long shopMidnight = NO_MIDNIGHT;

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

		public int rosterVersion() {
			return this.rosterVersion;
		}

		/** The enchanted books the village witch sells; empty until a player first opens the shop. */
		public List<ShopOffer> shopOffers() {
			return this.shopOffers;
		}

		/** The last midnight this village's shop saw, or {@link #NO_MIDNIGHT} when it has seen none. */
		public long shopMidnight() {
			return this.shopMidnight;
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

	/** One enchanted book for sale: the enchantment's id, its level, and the price in emeralds. */
	public record ShopOffer(Identifier enchantment, int level, int price) {
	}

	public record ThreatRecord(UUID attackerId, int outsideTicks) {
	}

	public record MissionRecord(
		UUID id,
		UUID ownerId,
		Identifier targetType,
		BlockPos targetPosition,
		Optional<UUID> entityId,
		MissionStatus status,
		Optional<MapId> mapId,
		ResourceKey<Level> dimension,
		MissionKind kind,
		int remaining,
		List<UUID> mobIds
	) {
		public MissionRecord(
			final UUID id,
			final UUID ownerId,
			final Identifier targetType,
			final BlockPos targetPosition,
			final Optional<UUID> entityId,
			final MissionStatus status,
			final Optional<MapId> mapId,
			final ResourceKey<Level> dimension
		) {
			this(id, ownerId, targetType, targetPosition, entityId, status, mapId, dimension, MissionKind.NEMESIS, 0, List.of());
		}

		public MissionRecord(
			final UUID id,
			final UUID ownerId,
			final Identifier targetType,
			final BlockPos targetPosition,
			final Optional<UUID> entityId,
			final MissionStatus status
		) {
			this(id, ownerId, targetType, targetPosition, entityId, status, Optional.empty(), DEFAULT_MISSION_DIMENSION);
		}

		public MissionRecord(
			final UUID id,
			final UUID ownerId,
			final Identifier targetType,
			final BlockPos targetPosition,
			final Optional<UUID> entityId,
			final MissionStatus status,
			final Optional<MapId> mapId
		) {
			this(id, ownerId, targetType, targetPosition, entityId, status, mapId, DEFAULT_MISSION_DIMENSION);
		}

		public MissionRecord(
			final UUID id,
			final UUID ownerId,
			final Identifier targetType,
			final BlockPos targetPosition,
			final Optional<UUID> entityId,
			final MissionStatus status,
			final ResourceKey<Level> dimension
		) {
			this(id, ownerId, targetType, targetPosition, entityId, status, Optional.empty(), dimension);
		}

		public MissionRecord {
			targetPosition = targetPosition.immutable();
			mapId = mapId == null ? Optional.empty() : mapId;
			dimension = dimension == null ? DEFAULT_MISSION_DIMENSION : dimension;
			kind = kind == null ? MissionKind.NEMESIS : kind;
			mobIds = mobIds == null ? List.of() : List.copyOf(mobIds);
		}

		/** The same record with a different status, entity and spawned mobs. */
		public MissionRecord spawned(final Optional<UUID> newEntityId, final int newRemaining, final List<UUID> newMobIds) {
			return new MissionRecord(this.id, this.ownerId, this.targetType, this.targetPosition, newEntityId, MissionStatus.ACTIVE,
				this.mapId, this.dimension, this.kind, newRemaining, newMobIds);
		}

		public MissionRecord withRemaining(final int newRemaining) {
			return new MissionRecord(this.id, this.ownerId, this.targetType, this.targetPosition, this.entityId, this.status,
				this.mapId, this.dimension, this.kind, newRemaining, this.mobIds);
		}
	}

	/** What a contract asks: hunt a Nemesis, slay an elite with two escorts, or kill a pack of ordinary mobs. */
	public enum MissionKind {
		NEMESIS,
		ELITE,
		KILL
	}

	public enum MissionStatus {
		TARGET_PENDING,
		ACTIVE
	}
}
