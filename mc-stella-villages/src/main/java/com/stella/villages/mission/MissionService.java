package com.stella.villages.mission;

import com.stella.villages.McStellaVillages;
import com.stella.villages.data.VillageWorldData;
import com.stella.villages.data.VillageWorldData.MissionRecord;
import com.stella.villages.data.VillageWorldData.MissionStatus;
import com.stella.villages.data.VillageWorldData.VillageRecord;
import com.stella.villages.patrol.PatrolState;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public final class MissionService {
	public static final int MISSION_MENU_RANGE = 8;
	public static final int MISSION_MIN_DISTANCE = 256;
	public static final int MISSION_MAX_DISTANCE = 512;
	public static final int MISSION_TARGET_SEARCH_LIMIT = 64;
	/** Health a Nemesis carries on top of its type's own. */
	public static final double NEMESIS_BONUS_HEALTH = 100.0;
	public static final int NEMESIS_ENRAGE_DURATION = 200;

	/** How many contracts a head offers at once, each a different kind. */
	public static final int OFFERS_PER_BOARD = 3;
	/** A dragon out of its own End fight has no fight to fly, so it is the one kind never offered. */
	public static final Set<Identifier> NEVER_OFFERED = Set.of(Identifier.parse("minecraft:ender_dragon"));
	/** Test seam: the kinds a board draws from; null draws from every kind with a spawn egg. */
	private static volatile @Nullable List<Identifier> offerPool;
	private static final Map<UUID, MissionBoard> BOARDS = new ConcurrentHashMap<>();
	private static final Map<UUID, Mob> ACTIVE_NEMESES = new ConcurrentHashMap<>();
	private static final Map<UUID, String> LAST_REJECTIONS = new ConcurrentHashMap<>();

	private MissionService() {
	}

	public static void register() {
		NemesisMapItem.register();
		UseEntityCallback.EVENT.register(MissionService::onUseEntity);
		ServerChunkEvents.CHUNK_LOAD.register((level, chunk, alreadyLoaded) -> onChunkLoad(level, chunk));
		ServerEntityEvents.ENTITY_LOAD.register(MissionService::onEntityLoad);
		ServerEntityEvents.ENTITY_UNLOAD.register(MissionService::onEntityUnload);
		ServerLivingEntityEvents.AFTER_DAMAGE.register(MissionService::onAfterDamage);
		ServerLivingEntityEvents.AFTER_DEATH.register(MissionService::onAfterDeath);
	}

	public static List<MissionOffer> offersFor(final UUID playerId) {
		MissionBoard board = BOARDS.get(playerId);
		return board == null ? List.of() : board.offers();
	}

	/** The reason the player's last offer was turned down, so the board can say why. */
	public static String lastRejection(final UUID playerId) {
		return LAST_REJECTIONS.getOrDefault(playerId, "unknown");
	}

	public static boolean acceptOffer(final ServerPlayer player, final UUID headId, final UUID offerId) {
		if (!(player.level() instanceof ServerLevel level)) {
			return false;
		}
		MissionBoard board = BOARDS.get(player.getUUID());
		if (board == null || !board.headId().equals(headId)) {
			return reject(player, headId, offerId, "stale_or_foreign_head");
		}
		Mob head = board.head();
		if (head.level() != level || !head.isAlive() || !isRegisteredHead(level, head)
			|| player.distanceToSqr(head) > (double) MISSION_MENU_RANGE * MISSION_MENU_RANGE) {
			return reject(player, headId, offerId, "head_missing_or_out_of_range");
		}
		MissionOffer offer = board.offers().stream().filter(candidate -> candidate.id().equals(offerId)).findFirst().orElse(null);
		if (offer == null) {
			return reject(player, headId, offerId, "stale_offer");
		}

		VillageWorldData data = VillageWorldData.get(level);
		VillageRecord village = data.villageForMember(headId).orElse(null);
		if (village == null || !village.dimension().equals(level.dimension())) {
			return reject(player, headId, offerId, "head_not_registered");
		}
		Optional<BlockPos> targetPosition = findSafeTarget(level, village.anchor(), offer.targetType());
		if (targetPosition.isEmpty()) {
			return reject(player, headId, offerId, "no_safe_target");
		}

		ItemStack map;
		try {
			map = NemesisMapItem.create(level, targetPosition.get());
		} catch (IllegalArgumentException | IllegalStateException error) {
			return reject(player, headId, offerId, "map_creation_failed");
		}
		net.minecraft.world.level.saveddata.maps.MapId mapId = NemesisMapItem.mapId(map);
		if (mapId == null) {
			return reject(player, headId, offerId, "map_id_missing");
		}
		MissionRecord mission = new MissionRecord(
			UUID.randomUUID(), player.getUUID(), offer.targetType(), targetPosition.get(),
			Optional.empty(), MissionStatus.TARGET_PENDING, Optional.of(mapId), level.dimension()
		);
		data.addMission(mission);
		deliverMap(level, player, map, mapId);
		BOARDS.put(player.getUUID(), new MissionBoard(headId, head, createOffers()));
		McStellaVillages.LOGGER.info(
			"[BEHAVIOR] mod={} event=mission_accepted mission={} owner={} head={} offer={} target_type={} target_position={} map_id={}",
			McStellaVillages.MOD_ID, mission.id(), mission.ownerId(), headId, offerId,
			mission.targetType(), mission.targetPosition(), mapId
		);

		LevelChunk loadedTarget = level.getChunkSource().getChunkNow(
			SectionPos.blockToSectionCoord(mission.targetPosition().getX()),
			SectionPos.blockToSectionCoord(mission.targetPosition().getZ())
		);
		if (loadedTarget != null) {
			spawnPendingMissions(level, loadedTarget);
		}
		return true;
	}

	public static Optional<BlockPos> findSafeTarget(
		final ServerLevel level,
		final BlockPos origin,
		final Identifier targetType
	) {
		EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(targetType).map(holder -> holder.value()).orElse(null);
		Entity entity = type == null ? null : type.create(level, EntitySpawnReason.COMMAND);
		if (!(entity instanceof Mob probe)) {
			return Optional.empty();
		}
		for (int attempt = 0; attempt < MISSION_TARGET_SEARCH_LIMIT; attempt++) {
			BlockPos candidate = targetCandidate(origin, attempt);
			int sectionX = SectionPos.blockToSectionCoord(candidate.getX());
			int sectionZ = SectionPos.blockToSectionCoord(candidate.getZ());
			LevelChunk chunk = level.getChunk(sectionX, sectionZ);
			int supportY = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, candidate.getX(), candidate.getZ());
			if (supportY < level.getMinY() || supportY + 3 >= level.getMaxY()) {
				continue;
			}
			BlockPos surface = new BlockPos(candidate.getX(), supportY + 1, candidate.getZ());
			double distanceSquared = horizontalDistanceSquared(origin, surface);
			if (distanceSquared < (double) MISSION_MIN_DISTANCE * MISSION_MIN_DISTANCE
				|| distanceSquared > (double) MISSION_MAX_DISTANCE * MISSION_MAX_DISTANCE
				|| !isSafeSurface(level, probe, surface)) {
				continue;
			}
			return Optional.of(surface);
		}
		return Optional.empty();
	}

	private static InteractionResult onUseEntity(
		final net.minecraft.world.entity.player.Player player,
		final Level world,
		final InteractionHand hand,
		final Entity entity,
		final net.minecraft.world.phys.EntityHitResult hitResult
	) {
		if (!(player instanceof ServerPlayer serverPlayer)
			|| !(world instanceof ServerLevel level)
			|| !(entity instanceof Mob head)
			|| !isRegisteredHead(level, head)) {
			return InteractionResult.PASS;
		}
		if (serverPlayer.distanceToSqr(head) > (double) MISSION_MENU_RANGE * MISSION_MENU_RANGE) {
			return InteractionResult.PASS;
		}
		List<MissionOffer> offers = createOffers();
		BOARDS.put(serverPlayer.getUUID(), new MissionBoard(head.getUUID(), head, offers));
		// V2: the board is a chest menu, so a player without the mod reads and accepts offers too.
		MissionBoardMenu.open(serverPlayer, head, offers);
		McStellaVillages.LOGGER.info(
			"[BEHAVIOR] mod={} event=mission_board_opened owner={} head={} offers={}",
			McStellaVillages.MOD_ID, serverPlayer.getUUID(), head.getUUID(), offers
		);
		return InteractionResult.SUCCESS;
	}

	private static void onChunkLoad(final ServerLevel level, final LevelChunk chunk) {
		spawnPendingMissions(level, chunk);
	}

	private static void spawnPendingMissions(final ServerLevel level, final LevelChunk chunk) {
		for (MissionRecord mission : VillageWorldData.get(level).missions()) {
			if (mission.dimension().equals(level.dimension()) && mission.status() == MissionStatus.TARGET_PENDING
				&& (mission.targetPosition().getX() >> 4) == chunk.getPos().x()
				&& (mission.targetPosition().getZ() >> 4) == chunk.getPos().z()) {
				spawnNemesis(level, mission);
			}
		}
	}

	private static void spawnNemesis(final ServerLevel level, final MissionRecord mission) {
		EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(mission.targetType())
			.map(holder -> holder.value()).orElse(null);
		if (type == null) {
			return;
		}
		Entity created = type.create(level, EntitySpawnReason.EVENT);
		if (!(created instanceof Mob nemesis)) {
			return;
		}
		nemesis.snapTo(Vec3.atBottomCenterOf(mission.targetPosition()), 0.0F, 0.0F);
		nemesis.setPersistenceRequired();
		nemesis.addTag(McStellaVillages.NO_SPAWN_ROLL_TAG);
		NemesisState.attach(nemesis, mission.id());
		applyNemesisProfile(nemesis);
		String name = "<Nemesis> " + type.getDescription().getString();
		nemesis.setCustomName(net.minecraft.network.chat.Component.literal(name));
		nemesis.setCustomNameVisible(true);
		if (!level.addFreshEntity(nemesis)) {
			McStellaVillages.LOGGER.warn(
				"[BEHAVIOR] mod={} event=nemesis_rejected mission={} reason=entity_insertion",
				McStellaVillages.MOD_ID, mission.id()
			);
			return;
		}
		VillageWorldData data = VillageWorldData.get(level);
		data.addMission(new MissionRecord(
			mission.id(), mission.ownerId(), mission.targetType(), mission.targetPosition(),
			Optional.of(nemesis.getUUID()), MissionStatus.ACTIVE, mission.mapId(), mission.dimension()
		));
		ACTIVE_NEMESES.put(nemesis.getUUID(), nemesis);
		McStellaVillages.LOGGER.info(
			"[BEHAVIOR] mod={} event=nemesis_spawned mission={} entity={} owner={} target_position={} health={} type={}",
			McStellaVillages.MOD_ID, mission.id(), nemesis.getUUID(), mission.ownerId(), mission.targetPosition(),
			nemesis.getMaxHealth(), mission.targetType()
		);
	}

	private static void applyNemesisProfile(final Mob nemesis) {
		AttributeInstance maxHealth = nemesis.getAttribute(Attributes.MAX_HEALTH);
		if (maxHealth != null) {
			maxHealth.setBaseValue(maxHealth.getBaseValue() + NEMESIS_BONUS_HEALTH);
			nemesis.setHealth(nemesis.getMaxHealth());
		}
		setEquipment(nemesis, EquipmentSlot.HEAD, Items.DIAMOND_HELMET);
		setEquipment(nemesis, EquipmentSlot.CHEST, Items.DIAMOND_CHESTPLATE);
		setEquipment(nemesis, EquipmentSlot.LEGS, Items.DIAMOND_LEGGINGS);
		setEquipment(nemesis, EquipmentSlot.FEET, Items.DIAMOND_BOOTS);
		setEquipment(nemesis, EquipmentSlot.MAINHAND, Items.DIAMOND_AXE);
		setEquipment(nemesis, EquipmentSlot.OFFHAND, Items.SHIELD);
	}

	private static void setEquipment(final Mob mob, final EquipmentSlot slot, final net.minecraft.world.item.Item item) {
		mob.setItemSlot(slot, new ItemStack(item));
		mob.setDropChance(slot, 1.0F);
	}

	private static void onEntityLoad(final Entity entity, final ServerLevel level) {
		if (entity instanceof Mob mob && NemesisState.get(mob) != null) {
			mob.setPersistenceRequired();
			ACTIVE_NEMESES.put(mob.getUUID(), mob);
		}
	}

	private static void onEntityUnload(final Entity entity, final ServerLevel level) {
		if (entity instanceof Mob mob && NemesisState.get(mob) != null) {
			ACTIVE_NEMESES.remove(mob.getUUID(), mob);
		}
	}

	private static void onAfterDamage(
		final LivingEntity victim,
		final net.minecraft.world.damagesource.DamageSource source,
		final float baseDamage,
		final float damage,
		final boolean blocked
	) {
		if (!(victim instanceof Mob nemesis) || !(victim.level() instanceof ServerLevel level) || damage <= 0.0F) {
			return;
		}
		NemesisState state = NemesisState.get(nemesis);
		if (state == null || state.enraged() || nemesis.getHealth() > nemesis.getMaxHealth() * 0.5F
			|| !NemesisState.markEnraged(nemesis)) {
			return;
		}
		nemesis.addEffect(new MobEffectInstance(MobEffects.STRENGTH, NEMESIS_ENRAGE_DURATION, 0));
		nemesis.addEffect(new MobEffectInstance(MobEffects.SPEED, NEMESIS_ENRAGE_DURATION, 0));
		McStellaVillages.LOGGER.info(
			"[BEHAVIOR] mod={} event=nemesis_enraged mission={} entity={} duration={}",
			McStellaVillages.MOD_ID, state.missionId(), nemesis.getUUID(), NEMESIS_ENRAGE_DURATION
		);
	}

	private static void onAfterDeath(final LivingEntity entity, final net.minecraft.world.damagesource.DamageSource source) {
		if (!(entity instanceof Mob nemesis) || !(entity.level() instanceof ServerLevel level)) {
			return;
		}
		NemesisState state = NemesisState.get(nemesis);
		if (state == null || !NemesisState.markRewardPaid(nemesis)) {
			return;
		}
		VillageWorldData data = VillageWorldData.get(level);
		MissionRecord mission = data.mission(state.missionId()).orElse(null);
		if (mission == null) {
			return;
		}
		McStellaVillages.LOGGER.info(
			"[BEHAVIOR] mod={} event=nemesis_killed mission={} entity={} killer={}",
			McStellaVillages.MOD_ID, mission.id(), nemesis.getUUID(), source.getEntity() == null ? "unknown" : source.getEntity().getUUID()
		);
		drop(level, nemesis, new ItemStack(Items.EMERALD, 64));
		drop(level, nemesis, new ItemStack(Items.EMERALD, 64));
		drop(level, nemesis, new ItemStack(Items.DIAMOND, 4));
		drop(level, nemesis, new ItemStack(Items.ANCIENT_DEBRIS, 1));
		data.removeMission(mission.id());
		McStellaVillages.LOGGER.info(
			"[BEHAVIOR] mod={} event=mission_reward mission={} owner={} emeralds=128 diamonds=4 ancient_debris=1",
			McStellaVillages.MOD_ID, mission.id(), mission.ownerId()
		);
	}

	private static void deliverMap(
		final ServerLevel level,
		final ServerPlayer player,
		final ItemStack map,
		final net.minecraft.world.level.saveddata.maps.MapId mapId
	) {
		if (player.getInventory().add(map)) {
			McStellaVillages.LOGGER.info(
				"[BEHAVIOR] mod={} event=mission_map_delivered owner={} map_id={}",
				McStellaVillages.MOD_ID, player.getUUID(), mapId
			);
			return;
		}
		var dropped = new net.minecraft.world.entity.item.ItemEntity(
			level, player.getX(), player.getY(), player.getZ(), map
		);
		boolean inserted = level.addFreshEntity(dropped);
		McStellaVillages.LOGGER.info(
			"[BEHAVIOR] mod={} event=mission_map_dropped owner={} map_id={} inserted={}",
			McStellaVillages.MOD_ID, player.getUUID(), mapId, inserted
		);
	}

	private static void drop(final ServerLevel level, final Mob mob, final ItemStack stack) {
		level.addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(
			level, mob.getX(), mob.getY(), mob.getZ(), stack
		));
	}

	private static boolean reject(
		final ServerPlayer player,
		final UUID headId,
		final UUID offerId,
		final String reason
	) {
		LAST_REJECTIONS.put(player.getUUID(), reason);
		McStellaVillages.LOGGER.info(
			"[BEHAVIOR] mod={} event=mission_rejected owner={} head={} offer={} reason={}",
			McStellaVillages.MOD_ID, player.getUUID(), headId, offerId, reason
		);
		return false;
	}

	private static boolean isRegisteredHead(final ServerLevel level, final Mob head) {
		if (PatrolState.role(head) != PatrolState.Role.HEAD || PatrolState.villageId(head) == null) {
			return false;
		}
		VillageWorldData data = VillageWorldData.get(level);
		VillageRecord village = data.villageForMember(head.getUUID()).orElse(null);
		return village != null && village.dimension().equals(level.dimension())
			&& village.members().stream().anyMatch(member -> member.id().equals(head.getUUID())
				&& member.role().equals(PatrolState.Role.HEAD.name()) && member.alive());
	}

	/** The kinds a board draws from: the test seam's when one is set, and every spawn-egg kind otherwise. */
	public static List<Identifier> offerPool() {
		List<Identifier> pool = offerPool;
		return pool != null ? pool : defaultOfferPool();
	}

	/** Every kind of mob with a spawn egg, the dragon aside: a Nemesis can be any of them. */
	public static List<Identifier> defaultOfferPool() {
		return BuiltInRegistries.ENTITY_TYPE.stream()
			.filter(type -> net.minecraft.world.item.SpawnEggItem.byId(type).isPresent())
			.map(BuiltInRegistries.ENTITY_TYPE::getKey)
			.filter(id -> !NEVER_OFFERED.contains(id))
			.sorted()
			.toList();
	}

	public static void setOfferPool(final @Nullable List<Identifier> pool) {
		offerPool = pool == null ? null : List.copyOf(pool);
	}

	/** Draws a board of different kinds from the pool. */
	public static List<Identifier> rollOffers(final List<Identifier> pool, final net.minecraft.util.RandomSource random) {
		List<Identifier> left = new ArrayList<>(pool);
		List<Identifier> picked = new ArrayList<>(OFFERS_PER_BOARD);
		while (picked.size() < OFFERS_PER_BOARD && !left.isEmpty()) {
			picked.add(left.remove(random.nextInt(left.size())));
		}
		return List.copyOf(picked);
	}

	private static List<MissionOffer> createOffers() {
		List<MissionOffer> result = new ArrayList<>(OFFERS_PER_BOARD);
		for (Identifier type : rollOffers(offerPool(), net.minecraft.util.RandomSource.create())) {
			result.add(new MissionOffer(UUID.randomUUID(), type));
		}
		return List.copyOf(result);
	}

	private static BlockPos targetCandidate(final BlockPos origin, final int attempt) {
		int direction = attempt % 8;
		int ring = attempt / 8;
		int distance = MISSION_MIN_DISTANCE
			+ ring * (MISSION_MAX_DISTANCE - MISSION_MIN_DISTANCE) / 7;
		int[] dx = {1, 1, 0, -1, -1, -1, 0, 1};
		int[] dz = {0, 1, 1, 1, 0, -1, -1, -1};
		double normalizer = direction % 2 == 0 ? 1.0 : Math.sqrt(2.0);
		int x = origin.getX() + Mth.floor(dx[direction] * distance / normalizer);
		int z = origin.getZ() + Mth.floor(dz[direction] * distance / normalizer);
		return new BlockPos(x, origin.getY(), z);
	}

	private static double horizontalDistanceSquared(final BlockPos first, final BlockPos second) {
		double dx = first.getX() - second.getX();
		double dz = first.getZ() - second.getZ();
		return dx * dx + dz * dz;
	}

	private static boolean isSafeSurface(final ServerLevel level, final Mob probe, final BlockPos position) {
		BlockPos support = position.below();
		if (!level.getBlockState(support).isFaceSturdy(level, support, Direction.UP)
			|| !level.getBlockState(position).getCollisionShape(level, position).isEmpty()
			|| !level.getBlockState(position.above()).getCollisionShape(level, position.above()).isEmpty()
			|| !level.getBlockState(position.above(2)).getCollisionShape(level, position.above(2)).isEmpty()) {
			return false;
		}
		probe.snapTo(Vec3.atBottomCenterOf(position), 0.0F, 0.0F);
		return level.noCollision(probe, probe.getBoundingBox());
	}

	public record MissionOffer(UUID id, Identifier targetType) {
	}

	private record MissionBoard(UUID headId, Mob head, List<MissionOffer> offers) {
	}
}
