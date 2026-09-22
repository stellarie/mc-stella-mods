package com.stella.mobs.behavior;

import java.util.EnumSet;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.phys.AABB;

public class DefendAllyGoal<T extends PathfinderMob> extends Goal {
	private static final double ALLY_RADIUS = 16.0;
	private static final double ALLY_BOX_HEIGHT = 8.0;
	private static final int SCAN_INTERVAL_TICKS = 20;

	private final T mob;
	private final TargetingConditions attackConditions = TargetingConditions.forCombat().range(ALLY_RADIUS);

	public DefendAllyGoal(final T mob) {
		this.mob = mob;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE));
	}

	@Override
	public boolean canUse() {
		ReactiveState state = ReactiveState.get(this.mob);
		if (!state.playerInRange(this.mob)) {
			return false;
		}

		long now = this.mob.level().getGameTime();
		if (now < state.getNextAllyScanTick()) {
			return false;
		}

		state.setNextAllyScanTick(now + SCAN_INTERVAL_TICKS);
		AABB box = this.mob.getBoundingBox().inflate(ALLY_RADIUS, ALLY_BOX_HEIGHT, ALLY_RADIUS);
		List<PathfinderMob> allies = this.mob.level().getEntitiesOfClass(PathfinderMob.class, box, this::isAlly);
		ServerLevel level = getServerLevel(this.mob);

		for (PathfinderMob ally : allies) {
			LivingEntity attacker = ally.getLastHurtByMob();
			if (attacker == null || !attacker.isAlive()) {
				continue;
			}

			if (ally.getLastHurtByMobTimestamp() <= state.getLastDefendedHurtTimestamp()) {
				continue;
			}

			if (!this.attackConditions.test(level, this.mob, attacker)) {
				continue;
			}

			state.setLastDefendedHurtTimestamp(ally.getLastHurtByMobTimestamp());
			this.mob.setTarget(attacker);
			return true;
		}

		return false;
	}

	@Override
	public boolean canContinueToUse() {
		return false;
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return false;
	}

	private boolean isAlly(final PathfinderMob candidate) {
		return candidate != this.mob
			&& candidate.isAlive()
			&& !candidate.isSpectator()
			&& (this.mob.isAlliedTo(candidate) || candidate.getClass() == this.mob.getClass());
	}
}
