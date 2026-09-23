package com.stella.mobs.behavior;

import java.util.EnumSet;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;

/**
 * R21: the marker that says "this mob is that elite's reinforcement". It delegates every step to the
 * shared follow goal, so the movement is identical, and only the TYPE carries the identity: an escort
 * counts through FollowAllyGoal, a reinforcement counts through this, and neither can be mistaken for
 * the other. That is what keeps the reinforcement cap off the elite's own escort squad.
 */
public class ReinforcementFollowGoal extends Goal {
	private final FollowAllyGoal follow;

	public ReinforcementFollowGoal(
		final Mob follower, final Mob elite, final double speedModifier, final float stopDistance, final float areaSize
	) {
		this.follow = new FollowAllyGoal(follower, elite, speedModifier, stopDistance, areaSize);
		this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
	}

	public Mob leader() {
		return this.follow.leader();
	}

	@Override
	public boolean canUse() {
		return this.follow.canUse();
	}

	@Override
	public boolean canContinueToUse() {
		return this.follow.canContinueToUse();
	}

	@Override
	public void start() {
		this.follow.start();
	}

	@Override
	public void stop() {
		this.follow.stop();
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return this.follow.requiresUpdateEveryTick();
	}

	@Override
	public void tick() {
		this.follow.tick();
	}
}
