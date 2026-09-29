package com.stella.learn;

import com.stella.mobs.behavior.ReactiveState;
import com.stella.mobs.config.CombatRole;
import java.util.EnumSet;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;

/**
 * Against a player who has been seen turtling, a mob stands off while that player's shield is up: a melee mob
 * holds its swing and a ranged one holds its fire, until the shield drops or its patience runs out. It takes the
 * MOVE and LOOK flags, so the vanilla attack goals below its rank cannot run meanwhile.
 */
public final class WaitOutShieldGoal extends Goal {
	private static final double HOLD_RADIUS = 10.0;
	private static final float LOOK_SPEED = 30.0F;

	private final Mob mob;
	private final boolean melee;
	private int held;
	private int restUntilTick;

	public WaitOutShieldGoal(final Mob mob, final boolean melee) {
		this.mob = mob;
		this.melee = melee;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		if (!this.playsThisRole() || this.mob.tickCount < this.restUntilTick) {
			return false;
		}

		return this.mob.getTarget() instanceof ServerPlayer player
			&& player.isAlive()
			&& this.mob.distanceTo(player) <= HOLD_RADIUS
			&& shieldRaised(player)
			&& shieldPressure(player) >= LearnConfigStore.get().shieldThreshold();
	}

	@Override
	public boolean canContinueToUse() {
		return this.held < LearnConfigStore.get().shieldPatienceTicks()
			&& this.mob.getTarget() instanceof ServerPlayer player
			&& player.isAlive()
			&& shieldRaised(player)
			&& shieldPressure(player) >= LearnConfigStore.get().shieldThreshold();
	}

	@Override
	public void start() {
		this.held = 0;
		this.mob.getNavigation().stop();
	}

	@Override
	public void stop() {
		this.restUntilTick = this.mob.tickCount + LearnConfigStore.get().shieldRestTicks();
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void tick() {
		this.held++;
		this.mob.getNavigation().stop();
		if (this.mob.getTarget() != null) {
			this.mob.getLookControl().setLookAt(this.mob.getTarget(), LOOK_SPEED, LOOK_SPEED);
		}
	}

	/** A raised shield is one the player is holding up, whether or not its short raise delay has passed. */
	static boolean shieldRaised(final ServerPlayer player) {
		return player.isUsingItem() && player.getUseItem().has(DataComponents.BLOCKS_ATTACKS);
	}

	/** How much of a full turtle this player is, from 0 to 1. */
	static double shieldPressure(final ServerPlayer player) {
		return Mth.clamp(Profiles.value(player, Habit.BLOCK) / LearnConfigStore.get().shieldSaturation(), 0.0, 1.0);
	}

	private boolean playsThisRole() {
		return (ReactiveState.get(this.mob).getRole() == CombatRole.MELEE) == this.melee;
	}
}
