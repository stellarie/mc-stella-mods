package com.stella.villages.mission;

import com.stella.mobs.behavior.Bosses;
import com.stella.mobs.behavior.ReactiveState;
import com.stella.mobs.scaling.SpawnScaling;
import com.stella.mobs.scaling.ThreatState;
import com.stella.villages.McStellaVillages;
import com.stella.villages.data.VillageWorldData;
import com.stella.villages.data.VillageWorldData.MissionKind;
import com.stella.villages.data.VillageWorldData.MissionRecord;
import com.stella.villages.data.VillageWorldData.VillageRecord;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The boss trial of a job. From level 40, and every 30 levels after, a player with a job can hunt one boss of the
 * trial level, 128 to 256 blocks from the village. Killing it calls the rpg mod's {@code grantTrial}, which pays the
 * stat reward once per trial level. The trainer offers the lowest trial the player has reached and not done.
 */
public final class JobTrials {
	public static final int MIN_DISTANCE = 128;
	public static final int MAX_DISTANCE = 256;
	public static final String NAME = "<Trial Boss> ";
	/** The land bosses the mobs mod can promote. */
	private static final List<Identifier> BOSS_TYPES = List.of(
		Identifier.parse("minecraft:skeleton"), Identifier.parse("minecraft:spider"), Identifier.parse("minecraft:evoker")
	);

	private JobTrials() {
	}

	/** The trial level the player can take now, or empty. */
	public static OptionalInt offer(final ServerPlayer player) {
		if (!RpgJobs.available() || RpgJobs.NONE.equals(RpgJobs.job(player))) {
			return OptionalInt.empty();
		}
		Set<Integer> done = RpgJobs.trialsDone(player);
		for (int level = RpgJobs.FIRST_TRIAL_LEVEL; level <= player.experienceLevel; level += RpgJobs.TRIAL_STEP) {
			if (!done.contains(level)) {
				return OptionalInt.of(level);
			}
		}
		return OptionalInt.empty();
	}

	/** Starts the trial the player can take. Returns false, and changes nothing, on a refusal. */
	public static boolean accept(final ServerPlayer player, final Mob trainer) {
		String reason = start(player, trainer);
		OptionalInt level = offer(player);
		JobTrainer.recordTrial(player, level.orElse(0), reason == null, reason == null ? "accepted" : reason);
		McStellaVillages.LOGGER.info("[BEHAVIOR] mod={} event=job_trial_choice owner={} accepted={} reason={}",
			McStellaVillages.MOD_ID, player.getUUID(), reason == null, reason == null ? "accepted" : reason);
		if (reason != null) {
			JobTrainer.say(player, "The trainer cannot send you out: " + JobTrainer.reasonText(reason));
			return false;
		}
		JobTrainer.say(player, "Your map marks a level " + level.getAsInt() + " boss. Hunt it down.");
		return true;
	}

	private static @Nullable String start(final ServerPlayer player, final Mob trainer) {
		OptionalInt trial = offer(player);
		if (trial.isEmpty()) {
			return "no_trial";
		}
		if (!(player.level() instanceof ServerLevel level)) {
			return "trainer_not_registered";
		}
		VillageRecord village = VillageWorldData.get(level).villageForMember(trainer.getUUID()).orElse(null);
		if (village == null) {
			return "trainer_not_registered";
		}
		// A trial whose boss is lost can be taken again, so a new one replaces the old.
		abandon(level, player.getUUID());
		Identifier type = BOSS_TYPES.get(level.getRandom().nextInt(BOSS_TYPES.size()));
		return MissionService.startQuest(level, player, village, type, MissionKind.TRIAL, trial.getAsInt(), MIN_DISTANCE, MAX_DISTANCE);
	}

	private static void abandon(final ServerLevel level, final UUID owner) {
		VillageWorldData data = VillageWorldData.get(level);
		for (MissionRecord mission : List.copyOf(data.missions())) {
			if (mission.kind() == MissionKind.TRIAL && mission.ownerId().equals(owner)) {
				data.removeMission(mission.id());
				for (UUID mobId : mission.mobIds()) {
					Entity boss = level.getEntity(mobId);
					if (boss != null) {
						boss.discard();
					}
				}
			}
		}
	}

	/** Spawns the boss at the mission target and marks the mission active. Null when it cannot spawn. */
	static @Nullable Mob spawnTarget(final ServerLevel level, final MissionRecord mission) {
		Mob boss = QuestMissions.create(level, mission);
		if (boss == null || !Bosses.canBeBoss(boss)) {
			return null;
		}
		int trial = mission.remaining();
		QuestMissions.place(boss, Vec3.atBottomCenterOf(mission.targetPosition()), mission);
		// The tag keeps the mobs mod's spawn rolls off the boss, so its level and scaling are set here.
		boss.addTag(McStellaVillages.NO_SPAWN_ROLL_TAG);
		((AttachmentTarget) boss).setAttached(ThreatState.TYPE, new ThreatState(trial, 0, 0, 0));
		ReactiveState.get(boss).setGearApplied(true);
		Bosses.blessAsBoss(boss, level, 1.0);
		scale(boss, trial);
		boss.setCustomName(Component.literal(NAME + boss.getType().getDescription().getString()));
		QuestMissions.questName(boss);
		boss.setCustomNameVisible(true);
		if (!level.addFreshEntity(boss)) {
			McStellaVillages.LOGGER.warn("[BEHAVIOR] mod={} event=quest_rejected mission={} reason=entity_insertion",
				McStellaVillages.MOD_ID, mission.id());
			return null;
		}
		VillageWorldData.get(level).addMission(mission.spawned(Optional.of(boss.getUUID()), trial, List.of(boss.getUUID())));
		McStellaVillages.LOGGER.info("[BEHAVIOR] mod={} event=trial_boss_spawned mission={} entity={} owner={} level={}",
			McStellaVillages.MOD_ID, mission.id(), boss.getUUID(), mission.ownerId(), trial);
		return boss;
	}

	// The spawn roll is off, so the threat level's health and damage are applied here.
	private static void scale(final Mob boss, final int level) {
		AttributeInstance health = boss.getAttribute(Attributes.MAX_HEALTH);
		if (health != null) {
			health.removeModifier(SpawnScaling.HEALTH_ID);
			health.addPermanentModifier(new AttributeModifier(SpawnScaling.HEALTH_ID,
				SpawnScaling.healthMultiplier(level) - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
		}
		AttributeInstance damage = boss.getAttribute(Attributes.ATTACK_DAMAGE);
		if (damage != null) {
			damage.removeModifier(SpawnScaling.DAMAGE_ID);
			damage.addPermanentModifier(new AttributeModifier(SpawnScaling.DAMAGE_ID,
				SpawnScaling.damageMultiplier(level) - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
		}
		boss.setHealth(boss.getMaxHealth());
	}

	/** A player's kill pays the trial to its owner. A boss lost to the sun or a fall pays nothing, and the trial can be retaken. */
	static void onBossDeath(final ServerLevel level, final VillageWorldData data, final MissionRecord mission, final Mob boss) {
		data.removeMission(mission.id());
		ServerPlayer owner = level.getServer().getPlayerList().getPlayer(mission.ownerId());
		if (!MissionService.paysFor(boss) || owner == null) {
			McStellaVillages.LOGGER.info("[BEHAVIOR] mod={} event=trial_boss_lost mission={} entity={} owner_online={}",
				McStellaVillages.MOD_ID, mission.id(), boss.getUUID(), owner != null);
			return;
		}
		boolean granted = RpgJobs.grantTrial(owner, mission.remaining());
		McStellaVillages.LOGGER.info("[BEHAVIOR] mod={} event=trial_boss_killed mission={} owner={} level={} granted={}",
			McStellaVillages.MOD_ID, mission.id(), mission.ownerId(), mission.remaining(), granted);
		JobTrainer.say(owner, granted
			? "Trial complete. You gain +5 to your main stat and +1 to the others."
			: "The boss fell, but the trial pays nothing now.");
	}
}
