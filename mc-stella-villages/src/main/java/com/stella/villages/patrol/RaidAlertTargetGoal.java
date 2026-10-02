package com.stella.villages.patrol;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.entity.raid.Raider;

/**
 * Sends a guard after the nearest raider of a raid at its village. The goal needs no line of sight and sets the
 * target itself, because no vanilla target goal accepts a raider for an illager. It ends when the raid ends or
 * when no raider is left within range of the bell.
 */
final class RaidAlertTargetGoal extends TargetGoal {
	private final UUID villageId;

	RaidAlertTargetGoal(final Mob guard, final UUID villageId) {
		super(guard, false);
		this.villageId = villageId;
	}

	@Override
	public boolean canUse() {
		if (this.mob.tickCount % RaidAlert.CHECK_INTERVAL_TICKS != 0 || !(this.mob.level() instanceof ServerLevel level)) {
			return false;
		}

		BlockPos bell = PatrolService.bellFor(this.mob, this.villageId);
		Raid raid = RaidAlert.activeRaid(level, bell);
		RaidAlert.note(this.villageId, bell, raid);
		this.targetMob = raid == null ? null : RaidAlert.nearestRaider(this.mob, raid, bell);
		return this.targetMob != null;
	}

	@Override
	public boolean canContinueToUse() {
		if (!(this.mob.level() instanceof ServerLevel level)) {
			return false;
		}

		BlockPos bell = PatrolService.bellFor(this.mob, this.villageId);
		Raid raid = RaidAlert.activeRaid(level, bell);
		RaidAlert.note(this.villageId, bell, raid);
		if (raid == null) {
			return false;
		}

		LivingEntity current = this.mob.getTarget();
		if (current instanceof Raider raider && raider.isAlive() && RaidAlert.inRange(bell, raider)) {
			return true;
		}

		// Next raider at once: no idle gap.
		Raider next = RaidAlert.nearestRaider(this.mob, raid, bell);
		if (next == null) {
			return false;
		}

		this.targetMob = next;
		this.mob.setTarget(next);
		return true;
	}

	@Override
	public void start() {
		super.start();
		this.mob.setTarget(this.targetMob);
	}
}
