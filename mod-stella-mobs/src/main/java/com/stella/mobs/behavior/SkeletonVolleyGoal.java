package com.stella.mobs.behavior;

import com.stella.mobs.StellaMobs;
import java.util.EnumSet;
import java.util.Locale;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.RangedBowAttackGoal;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * A skeleton archer's bow, in place of the vanilla bow goal at rank 4.
 *
 * <p>The ordinary shot is one arrow. It slows what it hits for two seconds, harder from an elite or a boss.
 * When its burst is ready, the skeleton looses a burst instead. A burst is separate arrows, one every half
 * second: three from a plain skeleton, five from an elite and seven from a boss.
 *
 * <p>Each burst arrow flies at the spot where the target stands as the arrow leaves the bow, a few degrees off
 * at random. No skeleton arrow burns, and none leaves a cloud.
 *
 * <p>The next burst starts ten seconds after the last arrow of the one before. The slowing shot keeps its own
 * pace in between. The first release of a fight is a burst. A burst ends when its target is lost or dead.
 *
 * <p>Each release follows a bow draw. The cadence counts the skeleton's own ticks, so it holds wherever the
 * game clock stands.
 */
public class SkeletonVolleyGoal extends Goal {
	public static final int ATTACK_PRIORITY = 4;
	public static final float RANGE = 24.0F;
	/** Target goals drop a target past this range. */
	public static final double MIN_FOLLOW_RANGE = 32.0;
	public static final int ARROWS_PER_BURST = 3;
	public static final int ELITE_ARROWS_PER_BURST = 5;
	public static final int BOSS_ARROWS_PER_BURST = 7;
	/** Equals vanilla's hit immunity, so all land. */
	public static final int BURST_SHOT_INTERVAL_TICKS = 10;
	public static final double BURST_SPREAD_DEGREES = 3.0;
	/** Counted from the last arrow of a burst. */
	public static final int BURST_COOLDOWN_TICKS = 200;
	/** From one release to the next. */
	public static final int SHOT_INTERVAL_TICKS = 40;
	/** The vanilla bow's own draw, so each release is seen coming. */
	public static final int DRAW_TICKS = 20;
	private static final Identifier FOLLOW_RANGE_ID = Identifier.fromNamespaceAndPath(StellaMobs.MOD_ID, "skeleton_burst_range");
	private static final float POWER = 1.6F;
	private static final float UNCERTAINTY = 0.0F;
	private static final double BALLISTIC_LIFT = 0.2;
	private static final double TARGET_AIM_HEIGHT = 0.3333333333333333;
	private static final double CHASE_SPEED = 1.0;

	private final Mob mob;
	private int attackTime = DRAW_TICKS;
	private int burstSize;
	private int burstLeft;
	// Mob age, so a lost target keeps its cooldown.
	private int lastBurstArrow = -BURST_COOLDOWN_TICKS;

	public SkeletonVolleyGoal(final Mob mob) {
		this.mob = mob;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
	}

	/**
	 * Replaces the vanilla bow goal at its own rank, from the installer and never from a goal callback. A
	 * weapon swap re-adds the vanilla goal at this rank, and the earlier insertion wins the tie.
	 */
	public static void install(final Mob mob) {
		if (mob.getType() != EntityTypes.SKELETON || BossAbilities.copiesOf(mob, SkeletonVolleyGoal.class) > 0) {
			return;
		}

		widenFollowRange(mob);
		mob.getGoalSelector().removeAllGoals(goal -> goal instanceof RangedBowAttackGoal);
		mob.getGoalSelector().addGoal(ATTACK_PRIORITY, new SkeletonVolleyGoal(mob));
	}

	/** One modifier per mob, even across reloads. */
	private static void widenFollowRange(final Mob mob) {
		AttributeInstance range = mob.getAttribute(Attributes.FOLLOW_RANGE);
		if (range != null && range.getModifier(FOLLOW_RANGE_ID) == null && range.getValue() < MIN_FOLLOW_RANGE) {
			range.addPermanentModifier(
				new AttributeModifier(FOLLOW_RANGE_ID, MIN_FOLLOW_RANGE - range.getValue(), AttributeModifier.Operation.ADD_VALUE)
			);
		}
	}

	/** How many arrows this skeleton's burst holds. */
	public static int arrowsFor(final Mob mob) {
		if (Bosses.isBoss(mob)) {
			return BOSS_ARROWS_PER_BURST;
		}
		return Elite.isElite(mob) ? ELITE_ARROWS_PER_BURST : ARROWS_PER_BURST;
	}

	@Override
	public boolean canUse() {
		LivingEntity target = this.mob.getTarget();
		return target != null && target.isAlive() && this.mob.isHolding(Items.BOW);
	}

	@Override
	public boolean canContinueToUse() {
		return this.canUse();
	}

	@Override
	public void start() {
		super.start();
		this.mob.setAggressive(true);
	}

	@Override
	public void stop() {
		super.stop();
		this.mob.setAggressive(false);
		this.mob.getNavigation().stop();
		this.mob.stopUsingItem();
		this.attackTime = DRAW_TICKS;
		this.endBurst("stopped");
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void tick() {
		LivingEntity target = this.mob.getTarget();
		if (target == null) {
			return;
		}

		this.mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
		boolean inReach = this.mob.distanceToSqr(target) <= (double) (RANGE * RANGE)
			&& this.mob.getSensing().hasLineOfSight(target);
		if (!inReach) {
			this.mob.stopUsingItem();
			this.mob.getNavigation().moveTo(target, CHASE_SPEED);
			return;
		}

		this.mob.getNavigation().stop();
		--this.attackTime;
		if (!this.mob.isUsingItem() && this.attackTime <= DRAW_TICKS) {
			this.mob.startUsingItem(ProjectileUtil.getWeaponHoldingHand(this.mob, Items.BOW));
		}

		if (this.attackTime <= 0) {
			this.mob.stopUsingItem();
			this.release(target);
		}
	}

	/** The next burst arrow, or a single shot. */
	private void release(final LivingEntity target) {
		if (this.burstLeft == 0 && this.mob.tickCount - this.lastBurstArrow >= BURST_COOLDOWN_TICKS) {
			this.burstSize = arrowsFor(this.mob);
			this.burstLeft = this.burstSize;
			StellaMobs.LOGGER.info(
				"[BEHAVIOR] test=skeletonVolley BURST tick={} arrows={} target={}",
				this.mob.tickCount, this.burstSize, String.format(Locale.ROOT, "%.1f", Math.sqrt(this.mob.distanceToSqr(target)))
			);
		}

		boolean burst = this.burstLeft > 0;
		this.loose(target, burst);
		if (!burst) {
			this.attackTime = SHOT_INTERVAL_TICKS;
			return;
		}

		this.lastBurstArrow = this.mob.tickCount;
		--this.burstLeft;
		this.attackTime = this.burstLeft > 0 ? BURST_SHOT_INTERVAL_TICKS : SHOT_INTERVAL_TICKS;
		if (this.burstLeft == 0) {
			this.logBurstEnd("done");
		}
	}

	private void endBurst(final String reason) {
		if (this.burstLeft > 0) {
			this.logBurstEnd(reason);
		}

		this.burstLeft = 0;
	}

	private void logBurstEnd(final String reason) {
		StellaMobs.LOGGER.info(
			"[BEHAVIOR] test=skeletonVolley BURST_END tick={} loosed={} of={} reason={}",
			this.mob.tickCount, this.burstSize - this.burstLeft, this.burstSize, reason
		);
	}

	private void loose(final LivingEntity target, final boolean burst) {
		ArcherKit.release(this.mob, arrow -> {
			double xd = target.getX() - this.mob.getX();
			double zd = target.getZ() - this.mob.getZ();
			double lift = Math.sqrt(xd * xd + zd * zd) * BALLISTIC_LIFT;
			return new Vec3(xd, target.getY(TARGET_AIM_HEIGHT) - arrow.getY() + lift, zd);
		}, POWER, UNCERTAINTY, burst);
	}
}
