package com.stella.nether;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.projectile.hurtingprojectile.SmallFireball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * The Sovereign's normal attack: a burst of {@link #BURST_SIZE} blue fireballs, {@link #SHOT_GAP_TICKS} apart,
 * once every {@link #BURST_EVERY_TICKS}. It replaces both vanilla's blaze attack and the mobs mod's beat,
 * and holds its fire while a special runs.
 */
public final class SovereignBurst extends Goal {
	public static final int BURST_SIZE = 5;
	public static final int BURST_EVERY_TICKS = 5 * 20;
	public static final int SHOT_GAP_TICKS = 4;
	/** Ahead of the mobs mod's attack goal, which it replaces. */
	static final int PRIORITY = 2;

	/** The goal's own tick of every shot each Sovereign fired, by its identity; read by tests. */
	private static final Map<UUID, List<Integer>> SHOTS = new ConcurrentHashMap<>();

	private final Blaze blaze;
	private int age;
	/** Ticks to the next burst; the first comes a second after it takes aim. */
	private int cooldown = 20;
	/** Shots left in the burst being fired, and ticks to the next of them. */
	private int left;
	private int gap;

	private SovereignBurst(final Blaze blaze) {
		this.blaze = blaze;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
	}

	/**
	 * Puts the burst in place of any other attack. Idempotent, and run from the server tick, never inside a
	 * goal selector's own tick, where removing a goal crashes the server.
	 */
	static void install(final Blaze blaze) {
		boolean has = blaze.getGoalSelector().getAvailableGoals().stream().anyMatch(wrapped -> wrapped.getGoal() instanceof SovereignBurst);
		boolean rival = blaze.getGoalSelector().getAvailableGoals().stream().anyMatch(wrapped -> rival(wrapped.getGoal()));
		if (has && !rival) {
			return;
		}
		blaze.getGoalSelector().removeAllGoals(SovereignBurst::rival);
		if (!has) {
			blaze.getGoalSelector().addGoal(PRIORITY, new SovereignBurst(blaze));
		}
	}

	private static boolean rival(final Goal goal) {
		return goal.getClass().getEnclosingClass() == Blaze.class || goal instanceof com.stella.mobs.behavior.BlazeAttackGoal;
	}

	public static List<Integer> shotsOf(final UUID sovereign) {
		return SHOTS.getOrDefault(sovereign, List.of());
	}

	@Override
	public boolean canUse() {
		LivingEntity target = this.blaze.getTarget();
		return target != null && target.isAlive() && this.blaze.canAttack(target);
	}

	@Override
	public void stop() {
		this.blaze.getNavigation().stop();
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void tick() {
		this.age++;
		LivingEntity target = this.blaze.getTarget();
		if (target == null || !target.isAlive()) {
			return;
		}
		this.blaze.getLookControl().setLookAt(target, 10.0F, 10.0F);
		boolean sees = this.blaze.getSensing().hasLineOfSight(target);
		double range = this.blaze.getAttributeValue(Attributes.FOLLOW_RANGE);
		if (!sees || this.blaze.distanceToSqr(target) > range * range) {
			this.blaze.getNavigation().moveTo(target, 1.0);
		} else {
			this.blaze.getNavigation().stop();
		}
		// Bursts start a fixed beat apart, whatever the length of the burst itself.
		if (this.cooldown > 0) {
			this.cooldown--;
		}
		if (this.left > 0 && !SovereignCharge.running(this.blaze) && !SovereignSpecials.shielded(this.blaze)) {
			if (--this.gap <= 0) {
				this.fire(target);
				this.left--;
				this.gap = SHOT_GAP_TICKS;
			}
			return;
		}
		// A special holds the Sovereign's fire; the beat resumes when it ends.
		if (SovereignSpecials.casting(this.blaze) || SovereignCharge.running(this.blaze) || SovereignSpecials.shielded(this.blaze) || this.cooldown > 0 || !sees) {
			return;
		}
		this.cooldown = BURST_EVERY_TICKS;
		this.left = BURST_SIZE;
		this.gap = 0;
		this.fire(target);
		this.left--;
		this.gap = SHOT_GAP_TICKS;
	}

	private void fire(final LivingEntity target) {
		if (!(this.blaze.level() instanceof ServerLevel level)) {
			return;
		}
		Vec3 aim = new Vec3(target.getX() - this.blaze.getX(), target.getY(0.5) - this.blaze.getY(0.5), target.getZ() - this.blaze.getZ());
		if (aim.lengthSqr() < 1.0E-4) {
			return;
		}
		Vec3 heading = aim.normalize();
		SmallFireball ball = new SmallFireball(level, this.blaze, heading);
		ball.setItem(new ItemStack(NetherItems.SOUL_FIRE_CHARGE));
		ball.setPos(this.blaze.getX() + heading.x * this.blaze.getBbWidth(), this.blaze.getY(0.5), this.blaze.getZ() + heading.z * this.blaze.getBbWidth());
		if (level.addFreshEntity(ball)) {
			SHOTS.computeIfAbsent(this.blaze.getUUID(), key -> new CopyOnWriteArrayList<>()).add(this.age);
			level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, ball.getX(), ball.getY(), ball.getZ(), 4, 0.1, 0.1, 0.1, 0.02);
		}
	}
}
