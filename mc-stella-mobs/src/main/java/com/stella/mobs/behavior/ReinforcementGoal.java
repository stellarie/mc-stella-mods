package com.stella.mobs.behavior;

import com.stella.mobs.StellaMobs;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.world.entity.monster.zombie.Zombie;

/**
 * R21: the elite zombie brings ordinary zombies for as long as it lives, one every hundred ticks while
 * fewer than eight of its own are alive within sixteen blocks. The goal is flagless at rank 5 and lives
 * on the elite's own selector, so it stops when the elite dies. Every reinforcement takes the shipped
 * create-and-flag recipe, so none of them can roll elite and recurse, and each carries half movement
 * speed under one stable modifier id. The identities the stream spawned are recorded, so a clause reads
 * the set the ability wrote rather than a box.
 */
public class ReinforcementGoal extends Goal {
	public static final int PRIORITY = 5;
	/** Goal ticks: a goal without requiresUpdateEveryTick is ticked every other game tick. */
	public static final int CADENCE_GOAL_TICKS = 50;
	public static final int ALIVE_CAP = 8;
	public static final double NEARBY_RADIUS = 16.0;
	public static final double SLOW_AMOUNT = -0.5;
	public static final Identifier SLOW_ID = Identifier.fromNamespaceAndPath(StellaMobs.MOD_ID, "reinforcement_slow");
	private static final double SCATTER = 2.0;
	private static final int FOLLOW_PRIORITY = 5;
	private static final double FOLLOW_SPEED = 1.0;
	private static final float FOLLOW_STOP_DISTANCE = 6.0F;
	private static final float FOLLOW_AREA_SIZE = 16.0F;
	/**
	 * The identities this elite's stream produced, so a clause reads the set the ability wrote rather than
	 * a box. A follower that cruises at the box's edge sits in this set and outside the box, and a despawn
	 * cannot shrink what was recorded.
	 */
	private static final AttachmentType<List<UUID>> SPAWNED = AttachmentRegistry.createDefaulted(
		Identifier.fromNamespaceAndPath(StellaMobs.MOD_ID, "reinforcements"), ArrayList::new
	);

	private final Zombie elite;
	private int ticksToNext = CADENCE_GOAL_TICKS;

	public ReinforcementGoal(final Zombie elite) {
		this.elite = elite;
	}

	public static void register() {
		StellaMobs.LOGGER.debug("Registered the reinforcement record attachment {}", SPAWNED.identifier());
	}

	/** The identities this elite's stream spawned, in arrival order, as a copy. */
	public static List<UUID> spawnedBy(final Zombie elite) {
		return List.copyOf(((AttachmentTarget) elite).getAttachedOrCreate(SPAWNED));
	}

	@Override
	public boolean canUse() {
		return this.elite.isAlive()
			&& Elite.isElite(this.elite)
			&& !EliteState.isSummoned(this.elite)
			&& ReactiveState.get(this.elite).playerInRange(this.elite);
	}

	@Override
	public boolean canContinueToUse() {
		return this.canUse();
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return false;
	}

	@Override
	public void tick() {
		if (--this.ticksToNext > 0) {
			return;
		}

		this.ticksToNext = CADENCE_GOAL_TICKS;
		int alive = reinforcementsOf(this.elite).size();

		if (alive >= ALIVE_CAP) {
			return;
		}

		if (this.spawnOne() != null) {
			StellaMobs.LOGGER.info(
				"[BEHAVIOR] test=eliteReinforcements t={} REINFORCE alive={} cap={} cadenceTicks={}",
				this.elite.level().getGameTime(), alive + 1, ALIVE_CAP, CADENCE_GOAL_TICKS * 2
			);
		}
	}

	/**
	 * The reinforcements this elite brought, by their own marker and nothing else: a follow goal of the
	 * reinforcement type whose leader is this elite. The elite's escorts carry a plain FollowAllyGoal, so
	 * they are not counted here, which is what keeps the cap for the stream it is meant to bound.
	 */
	public static List<Mob> reinforcementsOf(final Zombie elite) {
		return elite.level().getEntitiesOfClass(
			Mob.class,
			elite.getBoundingBox().inflate(NEARBY_RADIUS),
			candidate -> candidate.getGoalSelector().getAvailableGoals().stream().anyMatch(
				wrapped -> wrapped.getGoal() instanceof ReinforcementFollowGoal marker && marker.leader() == elite
			)
		);
	}
	private Zombie spawnOne() {
		if (!(this.elite.level() instanceof ServerLevel level)) {
			return null;
		}

		Zombie summon = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.EVENT);
		if (summon == null) {
			return null;
		}

		RandomSource random = summon.getRandom();
		summon.snapTo(
			this.elite.getX() + (random.nextDouble() * 2.0 - 1.0) * SCATTER,
			this.elite.getY(),
			this.elite.getZ() + (random.nextDouble() * 2.0 - 1.0) * SCATTER,
			random.nextFloat() * 360.0F,
			0.0F
		);
		summon.finalizeSpawn(
			level, level.getCurrentDifficultyAt(summon.blockPosition()), EntitySpawnReason.EVENT, null
		);
		// Both flags must exist before the add: ENTITY_LOAD fires inside addFreshEntity.
		EliteState.setSummoned(summon, true);
		ReactiveState.get(summon).setGearApplied(true);
		summon.getAttribute(Attributes.MOVEMENT_SPEED).addOrReplacePermanentModifier(
			new AttributeModifier(SLOW_ID, SLOW_AMOUNT, AttributeModifier.Operation.ADD_MULTIPLIED_BASE)
		);
		summon.getGoalSelector().addGoal(
			FOLLOW_PRIORITY,
			new ReinforcementFollowGoal(summon, this.elite, FOLLOW_SPEED, FOLLOW_STOP_DISTANCE, FOLLOW_AREA_SIZE)
		);
		if (level.addFreshEntity(summon)) {
			// The record holds what was added, so a clause reads the set the ability produced.
			((AttachmentTarget) this.elite).getAttachedOrCreate(SPAWNED).add(summon.getUUID());
		}

		return summon;
	}

}
