package com.stella.villages.mission;

import com.stella.villages.McStellaVillages;
import com.stella.villages.data.VillageWorldData;
import com.stella.villages.data.VillageWorldData.MissionKind;
import com.stella.villages.data.VillageWorldData.MissionRecord;
import com.stella.villages.data.VillageWorldData.MissionStatus;
import com.stella.villages.data.VillageWorldData.VillageRecord;
import com.stella.villages.patrol.PatrolService;
import com.stella.villages.patrol.PatrolState;
import com.stella.mobs.behavior.Elite;
import com.stella.mobs.behavior.EliteState;
import com.stella.mobs.behavior.ReactiveState;
import com.stella.mobs.gear.GearTier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public final class MissionService {
	public static final int MISSION_MENU_RANGE = 8;
	public static final int MISSION_MIN_DISTANCE = 256;
	public static final int MISSION_MAX_DISTANCE = 384;
	public static final int MISSION_TARGET_SEARCH_LIMIT = 64;
	/** Health a Nemesis carries on top of its type's own. */
	public static final double NEMESIS_BONUS_HEALTH = 100.0;
	public static final int NEMESIS_ENRAGE_DURATION = 200;
	/** A Nemesis pays this many stacks of emeralds, and this many random potions. */
	public static final int REWARD_EMERALD_STACKS = 2;
	public static final int REWARD_POTIONS = 2;
	public static final int REWARD_BONUS_DIAMONDS = 32;
	/** Chance of the bonus diamonds, and separately of one piece of the Nemesis's gear. */
	public static final float REWARD_BONUS_CHANCE = 0.05F;
	/** Chance of one ancient debris, rolled apart from the diamonds and the gear. */
	public static final float REWARD_DEBRIS_CHANCE = 0.001F;
	/** The item tag of the spells mod's books; resolved by id so this mod has no link to it. */
	public static final Identifier SPELL_BOOK_TAG = Identifier.parse("mod-stella-spells:spellbooks");

	/** How many contracts of each kind a head offers at once, each of a different mob type. */
	public static final int OFFERS_PER_BOARD = 3;
	/** The whole board: this many contracts of every kind, Nemesis first, then elite, then kill. */
	public static final int BOARD_SIZE = OFFERS_PER_BOARD * MissionKind.values().length;
	/** Types unsuitable for a land-based hostile contract, including neutral monsters and bosses. */
	public static final Set<Identifier> NEVER_OFFERED = Set.of(
		Identifier.parse("minecraft:ender_dragon"), Identifier.parse("minecraft:wither"),
		Identifier.parse("minecraft:creeper"), Identifier.parse("minecraft:endermite"), Identifier.parse("minecraft:silverfish"),
		Identifier.parse("minecraft:blaze"), Identifier.parse("minecraft:phantom"), Identifier.parse("minecraft:ghast"),
		Identifier.parse("minecraft:guardian"), Identifier.parse("minecraft:elder_guardian"),
		Identifier.parse("minecraft:snow_golem"), Identifier.parse("minecraft:tadpole"),
		Identifier.parse("minecraft:creaking"), Identifier.parse("minecraft:enderman"),
		Identifier.parse("minecraft:piglin"), Identifier.parse("minecraft:zombified_piglin"),
		Identifier.parse("minecraft:zombie_horse"), Identifier.parse("minecraft:zombie_nautilus"),
		Identifier.parse("minecraft:camel_husk")
	);
	/** Mobs of these kinds live in water and die on land. */
	public static final Set<net.minecraft.world.entity.MobCategory> WATER_CATEGORIES = java.util.EnumSet.of(
		net.minecraft.world.entity.MobCategory.WATER_CREATURE, net.minecraft.world.entity.MobCategory.WATER_AMBIENT,
		net.minecraft.world.entity.MobCategory.UNDERGROUND_WATER_CREATURE, net.minecraft.world.entity.MobCategory.AXOLOTLS
	);
	/** Test seam: the kinds a board draws from; null draws from eligible spawn-egg kinds. */
	private static volatile @Nullable List<Identifier> offerPool;
	/** Test seam: the spell books a reward draws from; null reads the spells mod's item tag. */
	private static volatile @Nullable List<Item> spellBookPool;
	/** Test seam: where a bell's target search looks, instead of a random spot; null searches at random. */
	private static final Map<BlockPos, TargetCandidateSource> CANDIDATE_SOURCES = new ConcurrentHashMap<>();
	private static final Map<UUID, MissionBoard> BOARDS = new ConcurrentHashMap<>();
	private static final Map<UUID, Mob> ACTIVE_NEMESES = new ConcurrentHashMap<>();
	private static final Map<UUID, String> LAST_REJECTIONS = new ConcurrentHashMap<>();
	private static final Map<UUID, Integer> MISSING_COMPANION_TICKS = new ConcurrentHashMap<>();

	private MissionService() {
	}

	public static void register() {
		NemesisCompanionDeaths.register();
		NemesisMapItem.register();
		QuestMobState.register();
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> QuestMissions.payPending(handler.getPlayer()));
		UseEntityCallback.EVENT.register(MissionService::onUseEntity);
		ServerChunkEvents.CHUNK_LOAD.register((level, chunk, alreadyLoaded) -> onChunkLoad(level, chunk));
		ServerEntityEvents.ENTITY_LOAD.register(MissionService::onEntityLoad);
		ServerEntityEvents.ENTITY_UNLOAD.register(MissionService::onEntityUnload);
		ServerLivingEntityEvents.AFTER_DAMAGE.register(MissionService::onAfterDamage);
		ServerLivingEntityEvents.AFTER_DEATH.register(MissionService::onAfterDeath);
		ServerTickEvents.END_SERVER_TICK.register(server -> server.getAllLevels().forEach(MissionService::tick));
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
		Optional<BlockPos> targetPosition = findSafeTarget(level, PatrolService.bellOf(level, village), offer.targetType());
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
			Optional.empty(), MissionStatus.TARGET_PENDING, Optional.of(mapId), level.dimension(),
			offer.kind(), offer.kind() == MissionKind.KILL ? offer.count() : 0, List.of()
		);
		data.addMission(mission);
		deliverMap(level, player, map, mapId);
		BOARDS.put(player.getUUID(), new MissionBoard(headId, head, createOffers(level)));
		McStellaVillages.LOGGER.info(
			"[BEHAVIOR] mod={} event=mission_accepted mission={} owner={} head={} offer={} kind={} target_type={} target_position={} map_id={}",
			McStellaVillages.MOD_ID, mission.id(), mission.ownerId(), headId, offerId,
			mission.kind(), mission.targetType(), mission.targetPosition(), mapId
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
		TargetCandidateSource source = CANDIDATE_SOURCES.get(origin);
		EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(targetType).map(holder -> holder.value()).orElse(null);
		Entity entity = type == null ? null : type.create(level, EntitySpawnReason.COMMAND);
		if (!(entity instanceof Mob probe) || !canBeNemesis(probe)) {
			return Optional.empty();
		}
		for (int attempt = 0; attempt < MISSION_TARGET_SEARCH_LIMIT; attempt++) {
			BlockPos candidate = source != null ? source.candidate(origin, attempt, level.getRandom()) : targetCandidate(origin, level.getRandom());
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
		ItemStack held = serverPlayer.getItemInHand(hand);
		if (NemesisHead.isTradeableHead(held)) {
			NemesisHead.trade(level, serverPlayer, held);
			McStellaVillages.LOGGER.info("[BEHAVIOR] mod={} event=nemesis_head_traded player={} books=3",
				McStellaVillages.MOD_ID, serverPlayer.getUUID());
			return InteractionResult.SUCCESS;
		}
		List<MissionOffer> offers = createOffers(level);
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
				if (mission.kind() == MissionKind.NEMESIS) {
					spawnNemesis(level, mission);
				} else {
					QuestMissions.spawn(level, mission);
				}
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
		if (!(created instanceof Mob nemesis) || !canBeNemesis(nemesis)) {
			return;
		}
		nemesis.snapTo(Vec3.atBottomCenterOf(mission.targetPosition()), 0.0F, 0.0F);
		nemesis.setPersistenceRequired();
		nemesis.addTag(McStellaVillages.NO_SPAWN_ROLL_TAG);
		ReactiveState.get(nemesis).setGearApplied(true);
		EliteState.setSummoned(nemesis, true);
		int buffIndex = NemesisKit.randomBuff(nemesis);
		GearTier tier = nemesis.getRandom().nextBoolean() ? GearTier.DIAMOND : GearTier.NETHERITE;
		NemesisState.attach(nemesis, mission.id(), buffIndex);
		NemesisKit.applyElite(nemesis, tier, buffIndex);
		NemesisKit.armNemesis(nemesis, tier);
		String name = "<Nemesis> " + type.getDescription().getString();
		nemesis.setCustomName(net.minecraft.network.chat.Component.literal(name));
		QuestMissions.questName(nemesis);
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
		spawnCompanion(level, nemesis, buffIndex, tier);
		McStellaVillages.LOGGER.info(
			"[BEHAVIOR] mod={} event=nemesis_spawned mission={} entity={} owner={} target_position={} health={} type={}",
			McStellaVillages.MOD_ID, mission.id(), nemesis.getUUID(), mission.ownerId(), mission.targetPosition(),
			nemesis.getMaxHealth(), mission.targetType()
		);
	}

	private static void spawnCompanion(final ServerLevel level, final Mob nemesis, final int buffIndex, final GearTier tier) {
		Entity created = nemesis.getType().create(level, EntitySpawnReason.EVENT);
		if (!(created instanceof Mob companion)) {
			return;
		}
		companion.snapTo(nemesis.getX() + 2.0, nemesis.getY(), nemesis.getZ() + 2.0, 0.0F, 0.0F);
		companion.setPersistenceRequired();
		companion.addTag(McStellaVillages.NO_SPAWN_ROLL_TAG);
		NemesisCompanionDeaths.attach(companion, NemesisState.get(nemesis).missionId());
		ReactiveState.get(companion).setGearApplied(true);
		EliteState.setSummoned(companion, true);
		NemesisKit.applyElite(companion, tier, buffIndex);
		companion.setCustomName(net.minecraft.network.chat.Component.literal("<Elite Guard> "
			+ nemesis.getType().getDescription().getString()));
		QuestMissions.questName(companion);
		if (level.addFreshEntity(companion)) {
			NemesisState.setCompanion(nemesis, companion.getUUID());
			McStellaVillages.LOGGER.info("[BEHAVIOR] mod={} event=nemesis_companion_spawned nemesis={} companion={} buff={}",
				McStellaVillages.MOD_ID, nemesis.getUUID(), companion.getUUID(), buffIndex);
		}
	}

	private static void tick(final ServerLevel level) {
		for (Mob nemesis : List.copyOf(ACTIVE_NEMESES.values())) {
			if (nemesis.level() != level || !nemesis.isAlive() || nemesis.tickCount % 20 != 0) {
				continue;
			}
			NemesisState state = NemesisState.get(nemesis);
			if (state == null) {
				continue;
			}
			NemesisKit.ensureBuff(nemesis, state.buffIndex());
			Entity companion = state.companionId().map(level::getEntity).orElse(null);
			if (NemesisCompanionDeaths.isDefeated(level, state.missionId())) {
				MISSING_COMPANION_TICKS.remove(nemesis.getUUID());
			} else if (!(companion instanceof Mob guard) || !guard.isAlive()) {
				int missing = MISSING_COMPANION_TICKS.merge(nemesis.getUUID(), 20, Integer::sum);
				if (missing >= 100) {
					MISSING_COMPANION_TICKS.remove(nemesis.getUUID());
					spawnCompanion(level, nemesis, state.buffIndex(), Elite.kitTier(nemesis));
				}
			} else {
				MISSING_COMPANION_TICKS.remove(nemesis.getUUID());
				if (NemesisCompanionDeaths.missionOf(guard) == null) {
					NemesisCompanionDeaths.attach(guard, state.missionId());
				}
				NemesisKit.ensureBuff(guard, state.buffIndex());
				if (guard.distanceToSqr(nemesis) > 16.0 * 16.0) {
					teleportCompanionNear(level, guard, nemesis);
				} else if (guard.distanceToSqr(nemesis) > 4.0 * 4.0) {
					guard.getNavigation().moveTo(nemesis, 1.2);
				}
			}
			ServerPlayer owner = VillageWorldData.get(level).mission(state.missionId())
				.map(mission -> level.getServer().getPlayerList().getPlayer(mission.ownerId())).orElse(null);
			if (owner != null && owner.level() == level && owner.isAlive()) {
				if (nemesis.getType() == EntityTypes.IRON_GOLEM && nemesis.distanceToSqr(owner) <= 48.0 * 48.0) {
					nemesis.setTarget(owner);
				}
				if (nemesis.tickCount % 100 == 0) {
					tryTeleportTowards(level, nemesis, owner);
				}
			}
		}
	}

	private static void teleportCompanionNear(final ServerLevel level, final Mob guard, final Mob nemesis) {
		for (int attempt = 0; attempt < 12; attempt++) {
			double angle = guard.getRandom().nextDouble() * Math.PI * 2.0;
			BlockPos spot = BlockPos.containing(nemesis.getX() + Math.cos(angle) * 3.0,
				nemesis.getY(), nemesis.getZ() + Math.sin(angle) * 3.0);
			if (isSafeTeleportSpot(level, guard, spot)) {
				guard.teleportTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
				return;
			}
		}
		guard.getNavigation().moveTo(nemesis, 1.2);
	}

	public static boolean tryTeleportTowards(final ServerLevel level, final Mob nemesis, final ServerPlayer player) {
		if (player.level() != level || nemesis.distanceToSqr(player) < 12.0 * 12.0
			|| nemesis.distanceToSqr(player) > 48.0 * 48.0) {
			return false;
		}
		boolean ranged = nemesis.getItemBySlot(EquipmentSlot.MAINHAND).is(Items.BOW)
			|| nemesis.getItemBySlot(EquipmentSlot.MAINHAND).is(Items.CROSSBOW);
		double radius = ranged ? 10.0 : 3.0;
		for (int attempt = 0; attempt < 12; attempt++) {
			double angle = nemesis.getRandom().nextDouble() * Math.PI * 2.0;
			BlockPos destination = BlockPos.containing(
				player.getX() + Math.cos(angle) * radius, player.getY(), player.getZ() + Math.sin(angle) * radius
			);
			for (int deltaY = -2; deltaY <= 2; deltaY++) {
				BlockPos spot = destination.offset(0, deltaY, 0);
				if (isSafeTeleportSpot(level, nemesis, spot)) {
					nemesis.teleportTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
					McStellaVillages.LOGGER.info("[BEHAVIOR] mod={} event=nemesis_teleported entity={} ranged={} destination={}",
						McStellaVillages.MOD_ID, nemesis.getUUID(), ranged, spot);
					return true;
				}
			}
		}
		return false;
	}

	private static boolean isSafeTeleportSpot(final ServerLevel level, final Mob mob, final BlockPos spot) {
		return level.getBlockState(spot.below()).isFaceSturdy(level, spot.below(), Direction.UP)
			&& level.getBlockState(spot).getCollisionShape(level, spot).isEmpty()
			&& level.getBlockState(spot.above()).getCollisionShape(level, spot.above()).isEmpty()
			&& level.noCollision(mob, mob.getBoundingBox().move(
				spot.getX() + 0.5 - mob.getX(), spot.getY() - mob.getY(), spot.getZ() + 0.5 - mob.getZ()
			));
	}

	private static void onEntityLoad(final Entity entity, final ServerLevel level) {
		if (entity instanceof Mob quest) {
			QuestMissions.onLoad(quest, level);
		}
		if (entity instanceof Mob mob && NemesisState.get(mob) != null) {
			NemesisState state = NemesisState.get(mob);
			boolean alreadyElite = Elite.isElite(mob);
			boolean oldKit = NemesisKit.needsEquipmentUpgrade(mob);
			if (!alreadyElite || oldKit) {
				AttributeInstance health = mob.getAttribute(Attributes.MAX_HEALTH);
				if (health != null) {
					health.setBaseValue(Math.max(1.0, health.getBaseValue() - NEMESIS_BONUS_HEALTH));
				}
				ReactiveState.get(mob).setGearApplied(true);
				EliteState.setSummoned(mob, true);
				GearTier tier = alreadyElite ? Elite.kitTier(mob)
					: mob.getRandom().nextBoolean() ? GearTier.DIAMOND : GearTier.NETHERITE;
				if (tier != GearTier.DIAMOND && tier != GearTier.NETHERITE) {
					tier = GearTier.DIAMOND;
				}
				if (!alreadyElite) {
					NemesisKit.applyElite(mob, tier, state.buffIndex());
					NemesisKit.armNemesis(mob, tier);
				} else {
					NemesisKit.equipNemesis(mob, tier);
					mob.setHealth(mob.getMaxHealth());
				}
			}
			NemesisKit.ensureBuff(mob, state.buffIndex());
			mob.setPersistenceRequired();
			ACTIVE_NEMESES.put(mob.getUUID(), mob);
			state.companionId().map(level::getEntity).filter(loaded -> loaded instanceof Mob)
				.ifPresent(loaded -> NemesisCompanionDeaths.attach((Mob) loaded, state.missionId()));
		}
	}

	private static void onEntityUnload(final Entity entity, final ServerLevel level) {
		if (entity instanceof Mob mob && NemesisState.get(mob) != null) {
			ACTIVE_NEMESES.remove(mob.getUUID(), mob);
			MISSING_COMPANION_TICKS.remove(mob.getUUID());
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
		QuestMissions.onDeath(level, nemesis);
		UUID companionMission = NemesisCompanionDeaths.missionOf(nemesis);
		if (companionMission == null && nemesis.getCustomName() != null
			&& nemesis.getCustomName().getString().contains("<Elite Guard>")) {
			companionMission = ACTIVE_NEMESES.values().stream()
				.map(NemesisState::get)
				.filter(state -> state != null && state.companionId().filter(nemesis.getUUID()::equals).isPresent())
				.map(NemesisState::missionId).findFirst().orElse(null);
		}
		if (companionMission != null) {
			NemesisCompanionDeaths.recordDeath(level, companionMission, nemesis.getUUID());
		}
		NemesisState state = NemesisState.get(nemesis);
		if (state == null) {
			return;
		}
		ACTIVE_NEMESES.remove(nemesis.getUUID(), nemesis);
		MISSING_COMPANION_TICKS.remove(nemesis.getUUID());
		NemesisCompanionDeaths.clear(level, state.missionId());
		state.companionId().map(level::getEntity).ifPresent(Entity::discard);
		VillageWorldData data = VillageWorldData.get(level);
		MissionRecord mission = data.mission(state.missionId()).orElse(null);
		if (mission == null) {
			return;
		}
		// Only a player's kill pays: a Nemesis that falls to the sun, the water or a stray arrow pays nothing,
		// and its mission is over.
		if (!paysFor(nemesis) || !NemesisState.markRewardPaid(nemesis)) {
			data.removeMission(mission.id());
			McStellaVillages.LOGGER.info("[BEHAVIOR] mod={} event=nemesis_lost mission={} entity={} killer={}",
				McStellaVillages.MOD_ID, mission.id(), nemesis.getUUID(), nemesis.getKillCredit() == null ? "none" : nemesis.getKillCredit().getType());
			return;
		}
		McStellaVillages.LOGGER.info(
			"[BEHAVIOR] mod={} event=nemesis_killed mission={} entity={} killer={}",
			McStellaVillages.MOD_ID, mission.id(), nemesis.getUUID(), source.getEntity() == null ? "unknown" : source.getEntity().getUUID()
		);
		List<ItemStack> reward = rollReward(level, nemesis, nemesis.getRandom());
		reward.forEach(stack -> drop(level, nemesis, stack));
		data.removeMission(mission.id());
		McStellaVillages.LOGGER.info(
			"[BEHAVIOR] mod={} event=mission_reward mission={} owner={} emerald_stacks={} potions={} book={} diamonds={} ancient_debris={} items={}",
			McStellaVillages.MOD_ID, mission.id(), mission.ownerId(), REWARD_EMERALD_STACKS, REWARD_POTIONS,
			reward.get(0).getItem(), rewardCount(reward, Items.DIAMOND),
			rewardCount(reward, Items.ANCIENT_DEBRIS), reward.size()
		);
	}

	private static int rewardCount(final List<ItemStack> reward, final Item item) {
		return reward.stream().filter(stack -> stack.is(item)).mapToInt(ItemStack::getCount).sum();
	}

	/**
	 * What a Nemesis pays: one book first, then the emeralds and potions, then the rare rolls. Each rare
	 * roll is independent, so a Nemesis can pay any of them or none.
	 */
	public static List<ItemStack> rollReward(final ServerLevel level, final Mob nemesis, final RandomSource random) {
		List<ItemStack> reward = new ArrayList<>();
		reward.add(rewardBook(level, random, random.nextBoolean()));
		for (int stack = 0; stack < REWARD_EMERALD_STACKS; stack++) {
			reward.add(new ItemStack(Items.EMERALD, Items.EMERALD.getDefaultMaxStackSize()));
		}
		for (int potion = 0; potion < REWARD_POTIONS; potion++) {
			reward.add(rewardPotion(random));
		}
		reward.addAll(rollRareRewards(nemesis, random));
		return reward;
	}

	/** The three independent rare rolls: diamonds, one ancient debris, and one piece of the Nemesis's gear. */
	public static List<ItemStack> rollRareRewards(final Mob nemesis, final RandomSource random) {
		List<ItemStack> rare = new ArrayList<>();
		if (random.nextFloat() < REWARD_BONUS_CHANCE) {
			rare.add(new ItemStack(Items.DIAMOND, REWARD_BONUS_DIAMONDS));
		}
		if (random.nextFloat() < REWARD_DEBRIS_CHANCE) {
			rare.add(new ItemStack(Items.ANCIENT_DEBRIS));
		}
		ItemStack equipment = rollEquipmentReward(nemesis, random);
		if (!equipment.isEmpty()) {
			rare.add(equipment);
		}
		return rare;
	}

	/** A spell book when asked for one and the spells mod has any; an enchanted book otherwise. */
	public static ItemStack rewardBook(final ServerLevel level, final RandomSource random, final boolean spellBook) {
		if (spellBook) {
			List<Item> books = spellBooks(level);
			if (!books.isEmpty()) {
				return new ItemStack(books.get(random.nextInt(books.size())));
			}
		}
		return NemesisHead.randomEnchantedBook(level, random);
	}

	private static List<Item> spellBooks(final ServerLevel level) {
		List<Item> pool = spellBookPool;
		if (pool != null) {
			return pool;
		}
		return level.registryAccess().lookupOrThrow(Registries.ITEM).get(TagKey.create(Registries.ITEM, SPELL_BOOK_TAG))
			.map(tag -> tag.stream().map(holder -> holder.value()).toList()).orElse(List.of());
	}

	/** A drinkable potion that does something: water, awkward, mundane and thick are left out. */
	public static ItemStack rewardPotion(final RandomSource random) {
		List<Holder.Reference<Potion>> potions = BuiltInRegistries.POTION.listElements()
			.filter(holder -> !holder.value().getEffects().isEmpty()).toList();
		return PotionContents.createItemStack(Items.POTION, potions.get(random.nextInt(potions.size())));
	}

	public static void setSpellBookPool(final @Nullable List<Item> pool) {
		spellBookPool = pool == null ? null : List.copyOf(pool);
	}

	public static ItemStack rollEquipmentReward(final Mob nemesis, final RandomSource random) {
		if (random.nextFloat() >= REWARD_BONUS_CHANCE) {
			return ItemStack.EMPTY;
		}
		List<ItemStack> pieces = new ArrayList<>();
		for (EquipmentSlot slot : NemesisKit.REWARD_SLOTS) {
			ItemStack stack = nemesis.getItemBySlot(slot);
			if (!stack.isEmpty()) {
				pieces.add(stack);
			}
		}
		return pieces.isEmpty() ? ItemStack.EMPTY : pieces.get(random.nextInt(pieces.size())).copyWithCount(1);
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

	/** A hostile mob can be a Nemesis. The iron golem is the sole non-hostile exception. */
	public static boolean canBeNemesis(final Mob mob) {
		Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType());
		return !NEVER_OFFERED.contains(id)
			&& (mob instanceof Enemy || id.equals(Identifier.parse("minecraft:iron_golem")));
	}

	/** Every eligible land mob with a spawn egg can appear on a board. */
	public static List<Identifier> defaultOfferPool() {
		return BuiltInRegistries.ENTITY_TYPE.stream()
			.filter(type -> net.minecraft.world.item.SpawnEggItem.byId(type).isPresent() && !WATER_CATEGORIES.contains(type.getCategory()))
			.map(BuiltInRegistries.ENTITY_TYPE::getKey)
			.filter(id -> !NEVER_OFFERED.contains(id))
			.filter(id -> BuiltInRegistries.ENTITY_TYPE.getValue(id).getCategory()
				== net.minecraft.world.entity.MobCategory.MONSTER
				|| id.equals(Identifier.parse("minecraft:iron_golem")))
			.sorted()
			.toList();
	}

	/** Whether a fallen Nemesis pays its reward: only when a player has the kill. */
	public static boolean paysFor(final LivingEntity nemesis) {
		return nemesis.getKillCredit() instanceof net.minecraft.server.level.ServerPlayer;
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

	private static List<MissionOffer> createOffers(final ServerLevel level) {
		List<MissionOffer> result = new ArrayList<>(BOARD_SIZE);
		List<Identifier> nemeses = new ArrayList<>();
		List<Identifier> elites = new ArrayList<>();
		List<Identifier> packs = new ArrayList<>();
		for (Identifier id : offerPool()) {
			EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(id);
			Entity entity = type == null ? null : type.create(level, EntitySpawnReason.COMMAND);
			if (entity instanceof Mob mob && canBeNemesis(mob)) {
				nemeses.add(id);
				if (Elite.canBeElite(mob)) {
					elites.add(id);
				}
				if (mob instanceof Enemy) {
					packs.add(id);
				}
			}
		}
		RandomSource random = RandomSource.create();
		for (Identifier type : rollOffers(nemeses, random)) {
			result.add(new MissionOffer(UUID.randomUUID(), type, MissionKind.NEMESIS, 0));
		}
		for (Identifier type : rollOffers(elites, random)) {
			result.add(new MissionOffer(UUID.randomUUID(), type, MissionKind.ELITE, 0));
		}
		for (Identifier type : rollOffers(packs, random)) {
			int count = QuestMissions.KILL_MIN_COUNT + random.nextInt(QuestMissions.KILL_MAX_COUNT - QuestMissions.KILL_MIN_COUNT + 1);
			result.add(new MissionOffer(UUID.randomUUID(), type, MissionKind.KILL, count));
		}
		return List.copyOf(result);
	}

	/** A random spot 256 to 384 blocks from the bell: a uniform bearing and a uniform distance. */
	public static BlockPos targetCandidate(final BlockPos bell, final RandomSource random) {
		double bearing = random.nextDouble() * Math.PI * 2.0;
		double distance = MISSION_MIN_DISTANCE + random.nextDouble() * (MISSION_MAX_DISTANCE - MISSION_MIN_DISTANCE);
		return new BlockPos(
			bell.getX() + (int) Math.round(Math.cos(bearing) * distance), bell.getY(),
			bell.getZ() + (int) Math.round(Math.sin(bearing) * distance)
		);
	}

	/** Where a bell's target search tries next; a test sets one to steer or to watch the search. */
	public interface TargetCandidateSource {
		BlockPos candidate(BlockPos bell, int attempt, RandomSource random);
	}

	public static void setTargetCandidateSource(final BlockPos bell, final @Nullable TargetCandidateSource source) {
		if (source == null) {
			CANDIDATE_SOURCES.remove(bell.immutable());
		} else {
			CANDIDATE_SOURCES.put(bell.immutable(), source);
		}
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

	/** One contract on the board. A KILL contract carries how many mobs it asks for; the others carry 0. */
	public record MissionOffer(UUID id, Identifier targetType, MissionKind kind, int count) {
		public MissionOffer(final UUID id, final Identifier targetType) {
			this(id, targetType, MissionKind.NEMESIS, 0);
		}
	}

	private record MissionBoard(UUID headId, Mob head, List<MissionOffer> offers) {
	}
}
