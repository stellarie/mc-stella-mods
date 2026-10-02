package com.stella.mobs.behavior;

import com.stella.mobs.config.CombatRole;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlocksAttacks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Raises the shield of a monster that holds one in its off hand.
 *
 * <p>A melee bearer guards against a survival or adventure player target within {@link #GUARD_RANGE}. While the
 * player and the mob look at each other, it keeps the shield raised and walks on. When the player looks away, it
 * lowers the shield and leaves the chase to the melee goal. In reach, it lowers the shield for its own swing and
 * raises it again on the next tick. A player hit that the shield blocks arms one riposte, an extra swing on a
 * cooldown. A hit from an axe disables the shield for the axe's time, as vanilla does for a player.
 *
 * <p>Every other threat keeps the older mode: hold the shield for 20 ticks, stand still, then rest for 40. That
 * covers a non-player target, a ranged bearer, and an arrow in flight. Every guard timer counts the mob's own ticks,
 * because the level clock barely moves in a game test.
 */
public class ShieldUseGoal<T extends PathfinderMob> extends Goal {
	public static final double GUARD_RANGE = 16.0;
	public static final double GUARD_SPEED = 1.0;
	public static final int GAZE_GRACE_TICKS = 10;
	public static final int ATTACK_INTERVAL_TICKS = 20;
	public static final int RIPOSTE_COOLDOWN_TICKS = 20;
	public static final int RIPOSTE_WINDOW_TICKS = 10;

	/** Test seam: reports what the guard did. */
	public interface Recorder {
		default void guardRaised(final Mob mob) {
		}

		default void riposte(final Mob mob, final LivingEntity target) {
		}

		default void disabled(final Mob mob, final int ticks) {
		}
	}

	private static volatile Recorder recorder = new Recorder() {
	};

	private static final int HOLD_TICKS = 20;
	private static final int COOLDOWN_TICKS = 40;
	private static final int ARROW_SCAN_INTERVAL_TICKS = 5;
	private static final int REPATH_INTERVAL_TICKS = 10;
	private static final double TRIGGER_RADIUS = 8.0;
	private static final double ARROW_BOX_RADIUS = 8.0;
	private static final float LOOK_ROTATION_SPEED = 30.0F;

	private final T mob;
	private @Nullable Entity threat;
	private int holdTicks;

	private boolean guarding;
	private @Nullable Player pendingGuard;
	private @Nullable Player guardTarget;
	private boolean raisedByGoal;
	private boolean raiseNext;
	private int lastMutualTick;
	private int nextRepathTick;
	private int attackReadyTick;
	private int riposteReadyTick;
	private int riposteUntilTick = -1;
	private int disabledUntilTick;

	public ShieldUseGoal(final T mob) {
		this.mob = mob;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
	}

	public static void setRecorder(final Recorder newRecorder) {
		recorder = newRecorder == null ? new Recorder() {
		} : newRecorder;
	}

	/**
	 * The block hook: a bearer that blocks a hit from a living attacker reports here.
	 * A mob without this goal is not driven by this mod, so nothing happens to it.
	 */
	public static void onBlocked(final Mob mob, final LivingEntity attacker) {
		for (WrappedGoal wrapped : mob.getGoalSelector().getAvailableGoals()) {
			if (wrapped.getGoal() instanceof ShieldUseGoal<?> goal) {
				goal.blocked(attacker);
				return;
			}
		}
	}

	@Override
	public boolean canUse() {
		ReactiveState state = ReactiveState.get(this.mob);
		if (!state.playerInRange(this.mob) || this.isDisabled()) {
			return false;
		}

		if (this.mob.isUsingItem() || !this.hasShield() || this.suppressed()) {
			return false;
		}

		// Only start reads the pending target.
		// A probe of canUse cannot disturb a run.
		Player player = this.guardPlayer();
		this.pendingGuard = null;
		if (player != null && (this.riposteArmed() || Gaze.mutual(this.mob, player))) {
			this.pendingGuard = player;
			return true;
		}

		if (this.mob.level().getGameTime() < state.getShieldReadyTick()) {
			return false;
		}

		Entity found = this.findThreat(state, player != null);
		if (found == null) {
			return false;
		}

		this.threat = found;
		return true;
	}

	@Override
	public boolean canContinueToUse() {
		if (this.guarding) {
			return this.guardHolds();
		}

		return this.holdTicks > 0
			&& this.threat != null
			&& this.threat.isAlive()
			&& !this.isDisabled()
			&& !this.suppressed();
	}

	@Override
	public void start() {
		Player player = this.pendingGuard;
		this.pendingGuard = null;
		if (player != null) {
			this.startGuard(player);
			return;
		}

		this.holdTicks = HOLD_TICKS;
		this.mob.getNavigation().stop();
		this.mob.startUsingItem(InteractionHand.OFF_HAND);
	}

	@Override
	public void stop() {
		if (this.guarding) {
			this.stopGuard();
			return;
		}

		this.mob.stopUsingItem();
		this.threat = null;
		this.holdTicks = 0;
		ReactiveState state = ReactiveState.get(this.mob);
		state.setShieldReadyTick(this.mob.level().getGameTime() + COOLDOWN_TICKS);
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return this.guarding || this.holdTicks > 0;
	}

	@Override
	public void tick() {
		if (this.guarding) {
			this.tickGuard();
			return;
		}

		Entity currentThreat = this.threat;
		if (currentThreat != null) {
			this.mob.lookAt(currentThreat, LOOK_ROTATION_SPEED, LOOK_ROTATION_SPEED);
			this.mob.getLookControl().setLookAt(currentThreat, LOOK_ROTATION_SPEED, LOOK_ROTATION_SPEED);
		}

		this.holdTicks--;
	}

	private void startGuard(final Player player) {
		this.guarding = true;
		this.guardTarget = player;
		this.lastMutualTick = this.mob.tickCount;
		this.nextRepathTick = this.mob.tickCount + REPATH_INTERVAL_TICKS;
		this.raiseNext = false;
		this.raise();
		this.mob.getNavigation().moveTo(player, GUARD_SPEED);
	}

	private void stopGuard() {
		// Lower only a shield that this goal raised.
		if (this.raisedByGoal && this.mob.isUsingItem() && this.mob.getUsedItemHand() == InteractionHand.OFF_HAND) {
			this.mob.stopUsingItem();
		}

		this.guarding = false;
		this.guardTarget = null;
		this.raisedByGoal = false;
		this.raiseNext = false;
	}

	private boolean guardHolds() {
		Player player = this.guardTarget;
		if (player == null
			|| this.mob.getTarget() != player
			|| !player.isAlive()
			|| player.isCreative()
			|| player.isSpectator()
			|| this.isDisabled()
			|| !this.hasShield()
			|| this.suppressed()
			|| this.mob.distanceToSqr(player) > GUARD_RANGE * GUARD_RANGE) {
			return false;
		}

		int now = this.mob.tickCount;
		if (this.riposteArmed() || Gaze.mutual(this.mob, player)) {
			this.lastMutualTick = now;
		}

		return now - this.lastMutualTick <= GAZE_GRACE_TICKS;
	}

	private void tickGuard() {
		Player player = this.guardTarget;
		if (player == null) {
			return;
		}

		int now = this.mob.tickCount;
		this.mob.getLookControl().setLookAt(player, LOOK_ROTATION_SPEED, LOOK_ROTATION_SPEED);
		if (this.raiseNext) {
			this.raiseNext = false;
			this.raise();
		}

		if (now >= this.nextRepathTick) {
			this.nextRepathTick = now + REPATH_INTERVAL_TICKS;
			this.mob.getNavigation().moveTo(player, GUARD_SPEED);
		}

		if (!this.mob.isWithinMeleeAttackRange(player) || !this.mob.getSensing().hasLineOfSight(player)) {
			return;
		}

		if (this.riposteArmed() && now >= this.riposteReadyTick) {
			this.riposteReadyTick = now + RIPOSTE_COOLDOWN_TICKS;
			this.riposteUntilTick = -1;
			this.strike(player);
			recorder.riposte(this.mob, player);
		} else if (now >= this.attackReadyTick) {
			this.attackReadyTick = now + ATTACK_INTERVAL_TICKS;
			this.strike(player);
		}
	}

	// Lower for the swing, raise on the next tick.
	// The gap is the player's counter window.
	private void strike(final Player player) {
		this.mob.stopUsingItem();
		this.mob.swingForAttack(InteractionHand.MAIN_HAND);
		if (this.mob.level() instanceof ServerLevel level) {
			this.mob.doHurtTarget(level, player);
		}

		this.raiseNext = true;
	}

	private void raise() {
		if (this.mob.isUsingItem() || this.isDisabled() || !this.hasShield()) {
			return;
		}

		this.mob.startUsingItem(InteractionHand.OFF_HAND);
		if (this.mob.isUsingItem()) {
			this.raisedByGoal = true;
			recorder.guardRaised(this.mob);
		}
	}

	private void blocked(final LivingEntity attacker) {
		ItemStack shield = this.mob.getItemBlockingWith();
		BlocksAttacks blocks = shield == null ? null : shield.get(DataComponents.BLOCKS_ATTACKS);
		float seconds = attacker.getSecondsToDisableBlocking();
		if (seconds > 0.0F && blocks != null && this.mob.level() instanceof ServerLevel level) {
			blocks.disable(level, this.mob, seconds, shield);
			// Rounds as the private disableBlockingForTicks.
			int ticks = Math.round(seconds * blocks.disableCooldownScale() * 20.0F);
			if (ticks > 0) {
				this.disabledUntilTick = this.mob.tickCount + ticks;
				this.riposteUntilTick = -1;
				recorder.disabled(this.mob, ticks);
			}

			return;
		}

		if (attacker instanceof Player && attacker == this.mob.getTarget() && this.mob.tickCount >= this.riposteReadyTick) {
			this.riposteUntilTick = this.mob.tickCount + RIPOSTE_WINDOW_TICKS;
		}
	}

	private @Nullable Player guardPlayer() {
		if (ReactiveState.get(this.mob).getRole() != CombatRole.MELEE) {
			return null;
		}

		if (!(this.mob.getTarget() instanceof Player player) || !player.isAlive() || player.isCreative() || player.isSpectator()) {
			return null;
		}

		return this.mob.distanceToSqr(player) <= GUARD_RANGE * GUARD_RANGE ? player : null;
	}

	private boolean hasShield() {
		return this.mob.getOffhandItem().has(DataComponents.BLOCKS_ATTACKS);
	}

	private boolean suppressed() {
		return Stance.suppresses(this.mob);
	}

	private boolean isDisabled() {
		return this.mob.tickCount < this.disabledUntilTick;
	}

	private boolean riposteArmed() {
		return this.mob.tickCount <= this.riposteUntilTick;
	}

	private @Nullable Entity findThreat(final ReactiveState state, final boolean guarded) {
		LivingEntity target = this.mob.getTarget();
		// The guard owns a guarded player target.
		// Proximity alone would raise the shield unwatched.
		if (!guarded
			&& target != null
			&& target.isAlive()
			&& this.mob.distanceToSqr(target) <= TRIGGER_RADIUS * TRIGGER_RADIUS
			&& this.mob.getSensing().hasLineOfSight(target)) {
			return target;
		}

		long now = this.mob.level().getGameTime();
		if (now < state.getNextArrowScanTick()) {
			return null;
		}

		state.setNextArrowScanTick(now + ARROW_SCAN_INTERVAL_TICKS);
		AABB box = this.mob.getBoundingBox().inflate(ARROW_BOX_RADIUS);
		List<AbstractArrow> arrows = this.mob.level().getEntitiesOfClass(AbstractArrow.class, box, this::isIncomingArrow);

		for (AbstractArrow arrow : arrows) {
			return arrow;
		}

		return null;
	}

	private boolean isIncomingArrow(final AbstractArrow arrow) {
		if (!arrow.isAlive() || arrow.getOwner() == this.mob) {
			return false;
		}

		Vec3 toMob = this.mob.position().subtract(arrow.position());
		return toMob.lengthSqr() > 0.0 && arrow.getDeltaMovement().dot(toMob) > 0.0;
	}
}
