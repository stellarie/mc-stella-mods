package com.stella.villages.patrol;

import com.stella.villages.McStellaVillages;
import com.stella.villages.data.VillageWorldData;
import com.stella.villages.data.VillageWorldData.MemberRecord;
import com.stella.villages.data.VillageWorldData.ThreatRecord;
import com.stella.villages.data.VillageWorldData.VillageRecord;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.PoiTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.RangedAttackGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public final class PatrolService {
	public static final double PATROL_HEALTH_MULTIPLIER = 1.5;
	public static final int PATROL_DETECTION_RANGE = 48;
	public static final int PATROL_RADIUS = 64;
	public static final int PATROL_FOLLOW_DISTANCE = 16;
	public static final int PLAYER_THREAT_CLEAR_TICKS = 200;
	public static final int WITCH_SUPPORT_RANGE = 16;
	public static final double WITCH_HEAL_THRESHOLD = 0.75;
	public static final int WITCH_MIN_THROW_INTERVAL = 20;
	public static final int PATROL_MEMBER_COUNT = 10;
	/** W021: five crossbow Pillagers take the places the Evoker and the Witch held. */
	public static final int PILLAGER_COUNT = 5;
	private static final Map<UUID, Mob> ACTIVE_MEMBERS = new ConcurrentHashMap<>();
	private static final Map<ServerLevel, Long> LEVEL_TICKS = new WeakHashMap<>();
	private static final Set<Mob> LOADOUT_CHECKS = ConcurrentHashMap.newKeySet();
	/** W021: roles the roster no longer holds; a saved member of one leaves when its chunk loads. */
	private static final Set<PatrolState.Role> RETIRED_ROLES = EnumSet.of(PatrolState.Role.EVOKER, PatrolState.Role.WITCH);
	private static final Set<Mob> RETIREMENTS = ConcurrentHashMap.newKeySet();

	private PatrolService() {
	}

	public static void register() {
		ServerChunkEvents.CHUNK_LOAD.register((level, chunk, alreadyLoaded) -> onChunkLoad(level, chunk));
		ServerChunkEvents.CHUNK_UNLOAD.register(PatrolService::onChunkUnload);
		ServerEntityEvents.ENTITY_LOAD.register(PatrolService::onEntityLoad);
		ServerEntityEvents.ENTITY_UNLOAD.register(PatrolService::onEntityUnload);
		ServerLivingEntityEvents.AFTER_DAMAGE.register(PatrolService::onAfterDamage);
		ServerLivingEntityEvents.AFTER_DEATH.register(PatrolService::onAfterDeath);
		ServerTickEvents.END_LEVEL_TICK.register(PatrolService::onLevelTick);
	}

	public static List<UUID> onChunkLoad(final ServerLevel level, final LevelChunk chunk) {
		VillageWorldData data = VillageWorldData.get(level);
		List<BlockPos> positions = level.getPoiManager()
			.getInChunk(holder -> holder.is(PoiTypeTags.VILLAGE), chunk.getPos(), PoiManager.Occupancy.IS_OCCUPIED)
			.map(record -> record.getPos().immutable())
			.sorted(Comparator.comparingLong(BlockPos::asLong))
			.toList();
		List<UUID> villageIds = new ArrayList<>();

		for (BlockPos position : positions) {
			VillageRecord village = data.findOrCreate(level.dimension(), position);
			if (!villageIds.contains(village.id())) {
				villageIds.add(village.id());
			}

			if (!village.rosterStarted()) {
				spawnRoster(level, data, village);
			}
		}

		return List.copyOf(villageIds);
	}

	public static boolean hasThreat(final ServerLevel level, final UUID villageId, final UUID attackerId) {
		return VillageWorldData.get(level).village(villageId)
			.map(village -> village.threats().stream().anyMatch(threat -> threat.attackerId().equals(attackerId)))
			.orElse(false);
	}

	static @Nullable ServerPlayer threatenedPlayer(final Mob guard, final UUID villageId) {
		if (!(guard.level() instanceof ServerLevel level)) {
			return null;
		}

		long rangeSqr = (long) PATROL_DETECTION_RANGE * (long) PATROL_DETECTION_RANGE;
		return VillageWorldData.get(level).village(villageId)
			.stream()
			.flatMap(village -> village.threats().stream())
			.map(threat -> level.getServer().getPlayerList().getPlayer(threat.attackerId()))
			.filter(player -> player != null && player.level() == level && player.isAlive() && !player.isCreative() && !player.isSpectator())
			.filter(player -> guard.distanceToSqr(player) <= rangeSqr)
			.min(Comparator.comparingDouble((ServerPlayer player) -> guard.distanceTo(player))
				.thenComparing(player -> player.getUUID().toString()))
			.orElse(null);
	}

	static Mob headFor(final Level level, final UUID villageId) {
		VillageWorldData data = VillageWorldData.get((ServerLevel) level);
		return data.village(villageId)
			.flatMap(village -> village.members().stream()
				.filter(member -> member.role().equals(PatrolState.Role.HEAD.name()) && member.alive())
				.map(MemberRecord::id)
				.map(ACTIVE_MEMBERS::get)
				.filter(mob -> mob != null && mob.level() == level && mob.isAlive())
				.findFirst())
			.orElse(null);
	}

	static List<Mob> members(final Level level, final UUID villageId) {
		List<Mob> result = new ArrayList<>();
		for (Mob mob : ACTIVE_MEMBERS.values()) {
			if (mob.level() == level && mob.isAlive() && villageId.equals(PatrolState.villageId(mob))) {
				result.add(mob);
			}
		}
		return result;
	}

	private static void spawnRoster(
		final ServerLevel level,
		final VillageWorldData data,
		final VillageRecord village
	) {
		List<Mob> spawned = new ArrayList<>();
		spawnMissingMembers(level, data, village, EntityTypes.VINDICATOR, PatrolState.Role.HEAD, 1, spawned);
		spawnMissingMembers(level, data, village, EntityTypes.VINDICATOR, PatrolState.Role.VINDICATOR, 3, spawned);
		spawnMissingMembers(level, data, village, EntityTypes.PILLAGER, PatrolState.Role.PILLAGER, PILLAGER_COUNT, spawned);
		spawnMissingMembers(level, data, village, EntityTypes.RAVAGER, PatrolState.Role.RAVAGER, 1, spawned);

		if (hasCompleteRoster(village)) {
			data.markRosterStarted(village);
		}

		McStellaVillages.LOGGER.info(
			"[BEHAVIOR] mod={} event=patrol_spawned village={} anchor={} members={} roster_started={}",
			McStellaVillages.MOD_ID, village.id(), village.anchor(), spawned.size(), village.rosterStarted()
		);
	}

	private static void spawnMissingMembers(
		final ServerLevel level,
		final VillageWorldData data,
		final VillageRecord village,
		final EntityType<? extends Mob> type,
		final PatrolState.Role role,
		final int required,
		final List<Mob> spawned
	) {
		long existing = village.members().stream().filter(member -> member.role().equals(role.name())).count();
		for (long index = existing; index < required; index++) {
			Mob member = spawnMember(level, data, village, type, role);
			if (member != null) {
				spawned.add(member);
			}
		}
	}

	private static boolean hasCompleteRoster(final VillageRecord village) {
		return roleCount(village, PatrolState.Role.HEAD) >= 1
			&& roleCount(village, PatrolState.Role.VINDICATOR) >= 3
			&& roleCount(village, PatrolState.Role.PILLAGER) >= PILLAGER_COUNT
			&& roleCount(village, PatrolState.Role.RAVAGER) >= 1;
	}

	private static long roleCount(final VillageRecord village, final PatrolState.Role role) {
		return village.members().stream().filter(member -> member.role().equals(role.name())).count();
	}

	private static Mob spawnMember(
		final ServerLevel level,
		final VillageWorldData data,
		final VillageRecord village,
		final EntityType<? extends Mob> type,
		final PatrolState.Role role
	) {
		Mob mob = type.create(level, EntitySpawnReason.EVENT);
		if (mob == null) {
			return null;
		}

		@Nullable BlockPos position = findSpawnPosition(level, village.anchor(), mob);
		if (position == null) {
			McStellaVillages.LOGGER.info(
				"[BEHAVIOR] mod={} event=patrol_member_spawn_rejected village={} entity={} role={} reason=no_safe_loaded_position",
				McStellaVillages.MOD_ID, village.id(), mob.getUUID(), role
			);
			return null;
		}

		mob.snapTo(Vec3.atBottomCenterOf(position), 0.0F, 0.0F);
		mob.setPersistenceRequired();
		applyPatrolAttributes(mob);
		PatrolState.attach(mob, village.id(), role);
		((MobPatrolAccess) mob).setPatrolMarker(true);
		mob.addTag(McStellaVillages.NO_SPAWN_ROLL_TAG);
		if (role == PatrolState.Role.HEAD) {
			applyHeadKit(mob);
		} else if (role == PatrolState.Role.PILLAGER) {
			mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.CROSSBOW));
		}
		installGoals(mob, village);
		if (!level.addFreshEntity(mob)) {
			McStellaVillages.LOGGER.info(
				"[BEHAVIOR] mod={} event=patrol_member_spawn_rejected village={} entity={} role={} reason=entity_insertion",
				McStellaVillages.MOD_ID, village.id(), mob.getUUID(), role
			);
			return null;
		}

		data.addMember(village, mob.getUUID(), role.name(), position);
		ACTIVE_MEMBERS.put(mob.getUUID(), mob);
		logSpawn(village, mob, role);
		return mob;
	}

	private static @Nullable BlockPos findSpawnPosition(final ServerLevel level, final BlockPos anchor, final Mob mob) {
		int chunkMinX = SectionPos.blockToSectionCoord(anchor.getX()) * 16;
		int chunkMinZ = SectionPos.blockToSectionCoord(anchor.getZ()) * 16;
		int chunkMaxX = chunkMinX + 15;
		int chunkMaxZ = chunkMinZ + 15;

		for (int radius = 1; radius <= 8; radius++) {
			for (int dx = -radius; dx <= radius; dx++) {
				for (int dz = -radius; dz <= radius; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
						continue;
					}

					int x = Math.max(chunkMinX, Math.min(chunkMaxX, anchor.getX() + dx));
					int z = Math.max(chunkMinZ, Math.min(chunkMaxZ, anchor.getZ() + dz));
					BlockPos candidate = new BlockPos(x, anchor.getY(), z);
					BlockPos support = candidate.below();
					Vec3 destination = Vec3.atBottomCenterOf(candidate);
					AABB spawnBox = mob.getBoundingBox().move(destination.subtract(mob.position()));
					if (level.hasChunkAt(candidate) && level.getBlockState(candidate).isAir()
						&& level.getBlockState(support).isFaceSturdy(level, support, Direction.UP)
						&& level.noCollision(mob, spawnBox)) {
						return candidate;
					}
				}
			}
		}

		return null;
	}

	private static void applyPatrolAttributes(final Mob mob) {
		AttributeInstance maxHealth = mob.getAttribute(Attributes.MAX_HEALTH);
		if (maxHealth != null) {
			maxHealth.setBaseValue(maxHealth.getBaseValue() * PATROL_HEALTH_MULTIPLIER);
			mob.setHealth((float) maxHealth.getValue());
		}

		AttributeInstance followRange = mob.getAttribute(Attributes.FOLLOW_RANGE);
		if (followRange != null) {
			followRange.setBaseValue(PATROL_DETECTION_RANGE);
		}
	}

	private static void applyHeadKit(final Mob head) {
		head.setItemSlot(EquipmentSlot.HEAD, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND_HELMET));
		head.setItemSlot(EquipmentSlot.CHEST, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND_CHESTPLATE));
		head.setItemSlot(EquipmentSlot.LEGS, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND_LEGGINGS));
		head.setItemSlot(EquipmentSlot.FEET, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND_BOOTS));
		head.setItemSlot(EquipmentSlot.MAINHAND, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND_AXE));
		for (EquipmentSlot slot : List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.MAINHAND)) {
			head.setDropChance(slot, 1.0F);
		}
	}

	private static void installGoals(final Mob mob, final VillageRecord village) {
		MobPatrolAccess access = (MobPatrolAccess) mob;
		access.patrolTargetSelector().removeAllGoals(goal -> true);
		access.patrolTargetSelector().addGoal(1, new PatrolThreatTargetGoal(mob, village.id()));
		access.patrolTargetSelector().addGoal(
			2,
			new NearestAttackableTargetGoal<>(
				mob,
				Monster.class,
				10,
				true,
				false,
				(target, level) -> target instanceof Monster
					&& (!(target instanceof Mob targetMob) || !PatrolState.isMarked(targetMob))
			)
		);

		mob.getGoalSelector().removeAllGoals(goal -> goal instanceof PatrolFollowGoal
			|| goal instanceof VillageWanderGoal || goal instanceof PatrolWitchSupportGoal);

		if (PatrolState.role(mob) == PatrolState.Role.HEAD) {
			mob.getGoalSelector().addGoal(6, new VillageWanderGoal(mob, village.anchor(), 1.0));
		} else if (mob instanceof Witch witch) {
			mob.removeAllGoals(goal -> goal instanceof RangedAttackGoal);
			mob.getGoalSelector().addGoal(1, new PatrolWitchSupportGoal(witch, village.id()));
			mob.getGoalSelector().addGoal(5, new PatrolFollowGoal(mob, village.id(), 1.1));
		} else {
			mob.getGoalSelector().addGoal(5, new PatrolFollowGoal(mob, village.id(), 1.1));
		}
	}

	private static void logSpawn(final VillageRecord village, final Mob mob, final PatrolState.Role role) {
		McStellaVillages.LOGGER.info(
			"[BEHAVIOR] mod={} event=patrol_member_spawned village={} entity={} role={} type={} health={} follow_range={}",
			McStellaVillages.MOD_ID, village.id(), mob.getUUID(), role,
			BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()), mob.getMaxHealth(), PATROL_DETECTION_RANGE
		);
	}

	private static void onChunkUnload(final ServerLevel level, final LevelChunk chunk) {
		for (Mob mob : new ArrayList<>(ACTIVE_MEMBERS.values())) {
			if (mob.level() == level && mob.chunkPosition().equals(chunk.getPos())) {
				saveMemberPosition(level, mob);
				ACTIVE_MEMBERS.remove(mob.getUUID(), mob);
			}
		}
	}

	private static void onEntityLoad(final Entity entity, final ServerLevel level) {
		if (!(entity instanceof Mob mob) || !PatrolState.isMarked(mob)) {
			return;
		}

		PatrolState state = PatrolState.get(mob);
		if (state == null) {
			return;
		}

		if (RETIRED_ROLES.contains(state.role())) {
			// Removing an entity inside its own load event is unsafe, so it leaves on the next level tick.
			RETIREMENTS.add(mob);
			return;
		}

		ACTIVE_MEMBERS.put(mob.getUUID(), mob);
		((MobPatrolAccess) mob).setPatrolMarker(true);
		if (state.role() == PatrolState.Role.PILLAGER) {
			// Another mod's load listener may re-roll the hand after this one, so the crossbow is checked a tick later.
			LOADOUT_CHECKS.add(mob);
		}
		VillageWorldData data = VillageWorldData.get(level);
		data.village(state.villageId()).ifPresent(village -> {
			data.updateMemberPosition(mob.getUUID(), mob.blockPosition());
			installGoals(mob, village);
		});
	}

	private static void onEntityUnload(final Entity entity, final ServerLevel level) {
		if (entity instanceof Mob mob && PatrolState.isMarked(mob)) {
			saveMemberPosition(level, mob);
			ACTIVE_MEMBERS.remove(mob.getUUID(), mob);
		}
	}

	private static void saveMemberPosition(final ServerLevel level, final Mob mob) {
		PatrolState.updatePosition(mob);
		VillageWorldData data = VillageWorldData.get(level);
		data.updateMemberPosition(mob.getUUID(), mob.blockPosition());
	}

	private static void onAfterDamage(
		final LivingEntity victim,
		final DamageSource source,
		final float baseDamage,
		final float damage,
		final boolean blocked
	) {
		if (!(victim.level() instanceof ServerLevel level) || damage <= 0.0F) {
			return;
		}

		Entity sourceEntity = source.getEntity();
		if (!(sourceEntity instanceof ServerPlayer attacker) || attacker.isSpectator() || attacker.isCreative()) {
			return;
		}

		VillageWorldData data = VillageWorldData.get(level);
		VillageRecord village = null;
		if (victim instanceof Mob mob && PatrolState.isMarked(mob)) {
			village = data.villageForMember(mob.getUUID()).orElse(null);
		} else {
			String id = BuiltInRegistries.ENTITY_TYPE.getKey(victim.getType()).toString();
			if (id.equals("minecraft:villager") || id.equals("minecraft:iron_golem")) {
				village = data.findNearest(level.dimension(), victim.blockPosition()).orElse(null);
			}
		}

		if (village == null) {
			return;
		}

		boolean started = village.threats().stream().noneMatch(threat -> threat.attackerId().equals(attacker.getUUID()));
		data.startThreat(village, attacker.getUUID());
		if (started) {
			McStellaVillages.LOGGER.info(
				"[BEHAVIOR] mod={} event=threat_started village={} attacker={} victim={}",
				McStellaVillages.MOD_ID, village.id(), attacker.getUUID(), victim.getUUID()
			);
		}

	}

	private static void onAfterDeath(final LivingEntity entity, final DamageSource source) {
		if (!(entity instanceof Mob mob) || !PatrolState.isMarked(mob) || !(entity.level() instanceof ServerLevel level)) {
			return;
		}

		PatrolState.markDead(mob);
		VillageWorldData.get(level).markMemberDead(mob.getUUID());
		ACTIVE_MEMBERS.remove(mob.getUUID(), mob);
		McStellaVillages.LOGGER.info(
			"[BEHAVIOR] mod={} event=patrol_member_died entity={} village={}",
			McStellaVillages.MOD_ID, mob.getUUID(), PatrolState.villageId(mob)
		);
	}

	private static void onLevelTick(final ServerLevel level) {
		long tick = LEVEL_TICKS.merge(level, 1L, Long::sum);
		restoreCrossbows(level);
		retireStaleMembers(level);
		VillageWorldData data = VillageWorldData.get(level);
		for (VillageRecord village : data.villages()) {
			if (!village.dimension().equals(level.dimension())) {
				continue;
			}

			if (tick % 5L == 0L) {
				long active = village.members().stream()
					.map(MemberRecord::id)
					.map(ACTIVE_MEMBERS::get)
					.filter(mob -> mob != null && mob.isAlive())
					.count();
				if (active > 0) {
					McStellaVillages.LOGGER.info(
						"[BEHAVIOR] mod={} event=heartbeat village={} active={} tick={}",
						McStellaVillages.MOD_ID, village.id(), active, tick
					);
				}
			}

			updateThreats(level, data, village);
		}
	}

	/** W021: a saved Evoker or Witch member dismounts, loses its record, and leaves the level. */
	private static void retireStaleMembers(final ServerLevel level) {
		VillageWorldData data = VillageWorldData.get(level);
		for (Mob mob : List.copyOf(RETIREMENTS)) {
			if (mob.level() != level) {
				continue;
			}

			RETIREMENTS.remove(mob);
			mob.stopRiding();
			mob.ejectPassengers();
			boolean recordRemoved = data.removeMember(mob.getUUID());
			mob.discard();
			McStellaVillages.LOGGER.info(
				"[BEHAVIOR] mod={} event=patrol_member_retired entity={} role={} record_removed={}",
				McStellaVillages.MOD_ID, mob.getUUID(), PatrolState.role(mob), recordRemoved
			);
		}
	}

	/** W021: a patrol Pillager that entered this level without a crossbow in hand gets one back. */
	private static void restoreCrossbows(final ServerLevel level) {
		for (Mob mob : List.copyOf(LOADOUT_CHECKS)) {
			if (mob.level() != level) {
				continue;
			}

			LOADOUT_CHECKS.remove(mob);
			if (mob.isAlive() && !mob.getMainHandItem().is(Items.CROSSBOW)) {
				McStellaVillages.LOGGER.info(
					"[BEHAVIOR] mod={} event=patrol_crossbow_restored entity={} replaced={}",
					McStellaVillages.MOD_ID, mob.getUUID(), mob.getMainHandItem().getItem()
				);
				mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.CROSSBOW));
			}
		}
	}

	private static void updateThreats(
		final ServerLevel level,
		final VillageWorldData data,
		final VillageRecord village
	) {
		for (ThreatRecord threat : village.threats()) {
			ServerPlayer attacker = level.getServer().getPlayerList().getPlayer(threat.attackerId());
			if (attacker == null || !attacker.isAlive() || !hasLivingMember(village)) {
				clearThreat(data, village, threat.attackerId(), "attacker_or_roster_gone");
				continue;
			}

			boolean inRange = isAttackerInRange(level, village, attacker);
			if (inRange) {
				if (threat.outsideTicks() > 0) {
					data.updateThreat(village, attacker.getUUID(), 0);
				}
			} else {
				int outsideTicks = threat.outsideTicks() + 1;
				if (outsideTicks >= PLAYER_THREAT_CLEAR_TICKS) {
					clearThreat(data, village, attacker.getUUID(), "range_timeout");
				} else {
					data.updateThreat(village, attacker.getUUID(), outsideTicks);
				}
			}
		}
	}

	private static boolean hasLivingMember(final VillageRecord village) {
		return village.members().stream().anyMatch(MemberRecord::alive);
	}

	private static boolean isAttackerInRange(
		final ServerLevel level,
		final VillageRecord village,
		final ServerPlayer attacker
	) {
		if (attacker.level() != level) {
			return false;
		}

		long rangeSqr = (long) PATROL_DETECTION_RANGE * (long) PATROL_DETECTION_RANGE;
		for (MemberRecord member : village.members()) {
			if (!member.alive()) {
				continue;
			}

			Mob mob = ACTIVE_MEMBERS.get(member.id());
			if (mob != null && mob.level() == level && mob.isAlive()) {
				if (mob.distanceToSqr(attacker) <= rangeSqr) {
					return true;
				}
			} else if (Vec3.atCenterOf(member.lastPosition()).distanceToSqr(attacker.position()) <= rangeSqr) {
				return true;
			}
		}

		return false;
	}

	private static void clearThreat(
		final VillageWorldData data,
		final VillageRecord village,
		final UUID attackerId,
		final String reason
	) {
		data.clearThreat(village, attackerId);
		for (Mob mob : new ArrayList<>(ACTIVE_MEMBERS.values())) {
			if (village.id().equals(PatrolState.villageId(mob)) && mob.getTarget() != null
				&& mob.getTarget().getUUID().equals(attackerId)) {
				mob.setTarget(null);
			}
		}
		McStellaVillages.LOGGER.info(
			"[BEHAVIOR] mod={} event=threat_cleared village={} attacker={} reason={}",
			McStellaVillages.MOD_ID, village.id(), attackerId, reason
		);
	}
}
