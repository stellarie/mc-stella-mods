package com.stella.villages.mission;

import com.stella.mobs.behavior.Elite;
import com.stella.mobs.behavior.EliteState;
import com.stella.mobs.behavior.ReactiveState;
import com.stella.mobs.scaling.ThreatState;
import com.stella.villages.McStellaVillages;
import com.stella.villages.data.VillageWorldData;
import com.stella.villages.data.VillageWorldData.MissionKind;
import com.stella.villages.data.VillageWorldData.MissionRecord;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * The elite and kill contracts. An elite contract spawns one elite and two ordinary mobs of its type at the
 * target, and pays when the elite falls. A kill contract spawns a pack of one ordinary type and pays when the
 * last of them falls, whoever kills it. Every quest mob stays in the world and carries the quest name prefix.
 */
public final class QuestMissions {
	public static final String PREFIX = "[Quest] ";
	public static final int ELITE_ESCORTS = 2;
	public static final int KILL_MIN_COUNT = 5;
	public static final int KILL_MAX_COUNT = 10;
	public static final int SPREAD_RADIUS = 6;
	/** An elite or guard contract pays these emeralds, and the {@link QuestRewards} roll of its mob on top. */
	public static final int ELITE_REWARD_EMERALDS = 64;
	public static final int GUARD_REWARD_EMERALDS = 32;
	public static final int GUARD_MIN_COUNT = 3;
	public static final int GUARD_MAX_COUNT = 6;
	private static final int SPOT_ATTEMPTS = 12;
	private static final int SPOT_HEIGHT_TOLERANCE = 4;

	/** Test seam: the action bar text each kill shows its owner, and each reward and where it went. */
	public interface Recorder {
		default void progress(final ServerPlayer owner, final String text) {
		}

		default void paid(final UUID owner, final MissionKind kind, final String where) {
		}
	}

	private static final Recorder SILENT = new Recorder() {
	};
	private static volatile Recorder recorder = SILENT;
	private static final List<Mob> STRAGGLERS = new ArrayList<>();

	private QuestMissions() {
	}

	public static void setRecorder(final Recorder newRecorder) {
		recorder = newRecorder == null ? SILENT : newRecorder;
	}

	/** Puts the quest prefix on a mob's existing display name: its custom name when it has one, its type's name otherwise. */
	static void questName(final Mob mob) {
		Component base = mob.getCustomName() != null ? mob.getCustomName() : mob.getType().getDescription();
		mob.setCustomName(Component.literal(PREFIX).append(base));
	}

	static void spawn(final ServerLevel level, final MissionRecord mission) {
		if (mission.kind() == MissionKind.ELITE) {
			spawnElite(level, mission);
		} else if (mission.kind() == MissionKind.KILL || mission.kind() == MissionKind.GUARD) {
			spawnKill(level, mission);
		} else if (mission.kind() == MissionKind.SLAYER) {
			SlayerContract.spawnTarget(level, mission);
		} else if (mission.kind() == MissionKind.TRIAL) {
			JobTrials.spawnTarget(level, mission);
		}
	}

	private static void spawnElite(final ServerLevel level, final MissionRecord mission) {
		Mob elite = create(level, mission);
		if (elite == null || !Elite.canBeElite(elite)) {
			return;
		}
		place(elite, Vec3.atBottomCenterOf(mission.targetPosition()), mission);
		elite.addTag(McStellaVillages.NO_SPAWN_ROLL_TAG);
		ReactiveState.get(elite).setGearApplied(true);
		NemesisKit.applyElite(elite, Elite.pickTier(elite.getRandom()), NemesisKit.randomBuff(elite));
		elite.setCustomName(Component.literal("<Elite> " + elite.getType().getDescription().getString()));
		questName(elite);
		elite.setCustomNameVisible(true);
		if (!level.addFreshEntity(elite)) {
			McStellaVillages.LOGGER.warn("[BEHAVIOR] mod={} event=quest_rejected mission={} reason=entity_insertion", McStellaVillages.MOD_ID, mission.id());
			return;
		}
		List<UUID> escorts = new ArrayList<>();
		for (int index = 0; index < ELITE_ESCORTS; index++) {
			Mob escort = create(level, mission);
			if (escort == null) {
				continue;
			}
			place(escort, spot(level, mission.targetPosition(), 2, escort.getRandom()), mission);
			questName(escort);
			escort.setCustomNameVisible(true);
			if (level.addFreshEntity(escort)) {
				escorts.add(escort.getUUID());
			}
		}
		VillageWorldData.get(level).addMission(mission.spawned(Optional.of(elite.getUUID()), 0, escorts));
		McStellaVillages.LOGGER.info("[BEHAVIOR] mod={} event=elite_quest_spawned mission={} elite={} escorts={} owner={} type={}",
			McStellaVillages.MOD_ID, mission.id(), elite.getUUID(), escorts.size(), mission.ownerId(), mission.targetType());
	}

	private static void spawnKill(final ServerLevel level, final MissionRecord mission) {
		int count = Math.max(1, mission.remaining());
		List<UUID> pack = new ArrayList<>();
		for (int index = 0; index < count; index++) {
			Mob mob = create(level, mission);
			if (mob == null) {
				break;
			}
			place(mob, spot(level, mission.targetPosition(), SPREAD_RADIUS, mob.getRandom()), mission);
			questName(mob);
			mob.setCustomNameVisible(true);
			if (level.addFreshEntity(mob)) {
				pack.add(mob.getUUID());
			}
		}
		if (pack.isEmpty()) {
			return;
		}
		VillageWorldData.get(level).addMission(mission.spawned(Optional.empty(), pack.size(), pack));
		McStellaVillages.LOGGER.info("[BEHAVIOR] mod={} event=kill_quest_spawned mission={} mobs={} owner={} type={}",
			McStellaVillages.MOD_ID, mission.id(), pack.size(), mission.ownerId(), mission.targetType());
	}

	static Mob create(final ServerLevel level, final MissionRecord mission) {
		EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(mission.targetType()).map(holder -> holder.value()).orElse(null);
		Entity created = type == null ? null : type.create(level, EntitySpawnReason.EVENT);
		return created instanceof Mob mob ? mob : null;
	}

	// A summoned mob takes no elite roll of its own, and the mobs mod's other spawn rolls are left to it.
	static void place(final Mob mob, final Vec3 position, final MissionRecord mission) {
		mob.snapTo(position, 0.0F, 0.0F);
		mob.setPersistenceRequired();
		EliteState.setSummoned(mob, true);
		QuestMobState.attach(mob, mission.id());
	}

	/** A free standing spot within a radius of the target, or the target itself when the ground offers none. */
	private static Vec3 spot(final ServerLevel level, final BlockPos target, final int radius, final RandomSource random) {
		for (int attempt = 0; attempt < SPOT_ATTEMPTS; attempt++) {
			double angle = random.nextDouble() * Math.PI * 2.0;
			double distance = Math.sqrt(random.nextDouble()) * radius;
			int x = target.getX() + (int) Math.round(Math.cos(angle) * distance);
			int z = target.getZ() + (int) Math.round(Math.sin(angle) * distance);
			if (!level.hasChunkAt(new BlockPos(x, target.getY(), z))) {
				continue;
			}
			int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
			BlockPos feet = new BlockPos(x, y, z);
			if (Math.abs(y - target.getY()) <= SPOT_HEIGHT_TOLERANCE
				&& level.getBlockState(feet.below()).isFaceSturdy(level, feet.below(), Direction.UP)
				&& level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
				&& level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty()) {
				return Vec3.atBottomCenterOf(feet);
			}
		}
		return Vec3.atBottomCenterOf(target);
	}

	/**
	 * A quest mob loaded for a mission that no longer exists is cleared, so a finished quest leaves nothing behind.
	 * The clearing waits for the end of the tick: a mob is loaded while its chunk walks its entities, and a mob
	 * removed then breaks that walk.
	 */
	static void onLoad(final Mob mob, final ServerLevel level) {
		UUID missionId = QuestMobState.missionOf(mob);
		if (missionId == null) {
			return;
		}
		MissionRecord mission = VillageWorldData.get(level).mission(missionId).orElse(null);
		if (mission == null) {
			STRAGGLERS.add(mob);
		} else if (mission.kind() == MissionKind.SLAYER && mob.getAttributeBaseValue(Attributes.MAX_HEALTH) != SlayerContract.HEALTH) {
			SlayerContract.restoreHealth(mob);
		}
	}

	static void clearStragglers() {
		if (STRAGGLERS.isEmpty()) {
			return;
		}
		List<Mob> due = List.copyOf(STRAGGLERS);
		STRAGGLERS.clear();
		due.forEach(Mob::discard);
	}

	static void onDeath(final ServerLevel level, final Mob mob) {
		VillageWorldData data = VillageWorldData.get(level);
		MissionRecord mission = data.questMissionOf(mob.getUUID()).orElse(null);
		if (mission == null) {
			return;
		}
		if (mission.kind() == MissionKind.ELITE && mission.entityId().filter(mob.getUUID()::equals).isPresent()) {
			eliteDeath(level, data, mission, mob);
		} else if (mission.kind() == MissionKind.KILL || mission.kind() == MissionKind.GUARD) {
			killDeath(level, data, mission, mob);
		} else if (mission.kind() == MissionKind.SLAYER && mission.entityId().filter(mob.getUUID()::equals).isPresent()) {
			slayerDeath(level, data, mission, mob);
		} else if (mission.kind() == MissionKind.TRIAL && mission.entityId().filter(mob.getUUID()::equals).isPresent()) {
			JobTrials.onBossDeath(level, data, mission, mob);
		}
	}

	private static void slayerDeath(final ServerLevel level, final VillageWorldData data, final MissionRecord mission, final Mob slime) {
		data.removeMission(mission.id());
		if (!MissionService.paysFor(slime)) {
			McStellaVillages.LOGGER.info("[BEHAVIOR] mod={} event=slayer_quest_lost mission={} entity={}", McStellaVillages.MOD_ID, mission.id(), slime.getUUID());
			return;
		}
		level.addFreshEntity(new ItemEntity(level, slime.getX(), slime.getY(), slime.getZ(),
			new ItemStack(com.stella.villages.item.ApprenticeChestplate.ITEM)));
		recorder.paid(mission.ownerId(), MissionKind.SLAYER, "dropped");
		McStellaVillages.LOGGER.info("[BEHAVIOR] mod={} event=slayer_quest_reward mission={} owner={}",
			McStellaVillages.MOD_ID, mission.id(), mission.ownerId());
	}

	private static void eliteDeath(final ServerLevel level, final VillageWorldData data, final MissionRecord mission, final Mob elite) {
		data.removeMission(mission.id());
		for (UUID escortId : mission.mobIds()) {
			Entity escort = level.getEntity(escortId);
			if (escort != null) {
				escort.discard();
			}
		}
		if (!MissionService.paysFor(elite)) {
			McStellaVillages.LOGGER.info("[BEHAVIOR] mod={} event=elite_quest_lost mission={} entity={}", McStellaVillages.MOD_ID, mission.id(), elite.getUUID());
			return;
		}
		List<ItemStack> reward = new ArrayList<>();
		reward.add(new ItemStack(Items.EMERALD, ELITE_REWARD_EMERALDS));
		reward.addAll(QuestRewards.roll(level, elite.getType(), true, ThreatState.level(elite), elite.getRandom()));
		reward.forEach(stack -> level.addFreshEntity(new ItemEntity(level, elite.getX(), elite.getY(), elite.getZ(), stack)));
		recorder.paid(mission.ownerId(), MissionKind.ELITE, "dropped");
		McStellaVillages.LOGGER.info("[BEHAVIOR] mod={} event=elite_quest_reward mission={} owner={} tier={} items={}",
			McStellaVillages.MOD_ID, mission.id(), mission.ownerId(), QuestRewards.tierOf(elite.getType()), reward.size());
	}

	private static void killDeath(final ServerLevel level, final VillageWorldData data, final MissionRecord mission, final Mob mob) {
		if (mission.remaining() <= 0 || !mission.mobIds().contains(mob.getUUID())) {
			return;
		}
		MissionRecord updated = mission.withRemaining(mission.remaining() - 1);
		ServerPlayer owner = level.getServer().getPlayerList().getPlayer(mission.ownerId());
		int total = mission.mobIds().size();
		if (owner != null) {
			String text = PREFIX + "Kill " + mob.getType().getDescription().getString() + " " + (total - updated.remaining()) + "/" + total;
			owner.sendSystemMessage(Component.literal(text), true);
			recorder.progress(owner, text);
		}
		if (updated.remaining() > 0) {
			data.addMission(updated);
			return;
		}
		data.removeMission(mission.id());
		boolean guard = mission.kind() == MissionKind.GUARD;
		int threat = ThreatState.level(mob);
		if (owner != null) {
			if (guard) {
				payGuardReward(level, owner, mob.getType(), threat, mob.getRandom());
			} else {
				payKillReward(level, owner, mob.getType(), threat, mob.getRandom());
			}
			recorder.paid(mission.ownerId(), mission.kind(), "inventory");
		} else {
			PendingQuestRewards.get(level).add(mission.ownerId(),
				new PendingQuestRewards.Entry(BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()), false, threat, guard));
			recorder.paid(mission.ownerId(), mission.kind(), "pending");
		}
		McStellaVillages.LOGGER.info("[BEHAVIOR] mod={} event=kill_quest_complete mission={} owner={} online={}",
			McStellaVillages.MOD_ID, mission.id(), mission.ownerId(), owner != null);
	}

	/** The roll of the pack mob, into the inventory, and at the feet of a player whose inventory is full. */
	static void payKillReward(final ServerLevel level, final ServerPlayer owner, final EntityType<?> type, final int threat, final RandomSource random) {
		give(level, owner, QuestRewards.roll(level, type, false, threat, random));
	}

	/** Half a stack of emeralds, and the roll of the pack mob. */
	static void payGuardReward(final ServerLevel level, final ServerPlayer owner, final EntityType<?> type, final int threat, final RandomSource random) {
		List<ItemStack> reward = new ArrayList<>();
		reward.add(new ItemStack(Items.EMERALD, GUARD_REWARD_EMERALDS));
		reward.addAll(QuestRewards.roll(level, type, false, threat, random));
		give(level, owner, reward);
	}

	private static void give(final ServerLevel level, final ServerPlayer owner, final List<ItemStack> reward) {
		for (ItemStack stack : reward) {
			owner.getInventory().add(stack);
			if (!stack.isEmpty()) {
				level.addFreshEntity(new ItemEntity(level, owner.getX(), owner.getY(), owner.getZ(), stack));
			}
		}
	}

	/** Pays every kill reward that was waiting for a player who has just logged in. */
	static void payPending(final ServerPlayer player) {
		if (!(player.level() instanceof ServerLevel level)) {
			return;
		}
		int paid = 0;
		for (PendingQuestRewards.Entry entry : PendingQuestRewards.get(level).take(player.getUUID())) {
			EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(entry.type()).orElse(EntityTypes.ZOMBIE);
			if (entry.guard()) {
				payGuardReward(level, player, type, entry.threat(), player.getRandom());
			} else {
				payKillReward(level, player, type, entry.threat(), player.getRandom());
			}
			recorder.paid(player.getUUID(), entry.guard() ? MissionKind.GUARD : MissionKind.KILL, "login");
			paid++;
		}
		// A save from before tiered rewards holds counts only; those pay as a zombie quest.
		VillageWorldData data = VillageWorldData.get(level);
		int guards = data.takePendingGuardRewards(player.getUUID());
		for (int index = 0; index < guards; index++) {
			payGuardReward(level, player, EntityTypes.ZOMBIE, 0, player.getRandom());
			recorder.paid(player.getUUID(), MissionKind.GUARD, "login");
		}
		int waiting = data.takePendingKillRewards(player.getUUID());
		for (int index = 0; index < waiting; index++) {
			payKillReward(level, player, EntityTypes.ZOMBIE, 0, player.getRandom());
			recorder.paid(player.getUUID(), MissionKind.KILL, "login");
		}
		paid += guards + waiting;
		if (paid > 0) {
			McStellaVillages.LOGGER.info("[BEHAVIOR] mod={} event=quest_reward_paid_on_login owner={} rewards={}", McStellaVillages.MOD_ID, player.getUUID(), paid);
		}
	}
}
