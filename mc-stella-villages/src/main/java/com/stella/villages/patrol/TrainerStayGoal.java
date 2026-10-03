package com.stella.villages.patrol;

import java.util.EnumSet;
import java.util.UUID;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

/**
 * The trainer keeps beside its village's duke. It walks to the duke when it stands further than {@link #LEASH}
 * blocks away, and to the bell while the duke is not loaded. It never wanders and never fights.
 */
final class TrainerStayGoal extends Goal {
	/** The trainer starts walking past this many blocks from the duke. */
	static final double LEASH = 2.0;
	/** The trainer stops walking this many blocks from the duke. */
	static final double ARRIVAL = 1.5;
	private static final int REPATH_TICKS = 10;
	private final Mob mob;
	private final UUID villageId;
	private int repath;

	TrainerStayGoal(final Mob mob, final UUID villageId) {
		this.mob = mob;
		this.villageId = villageId;
		setFlags(EnumSet.of(Flag.MOVE));
	}

	private Vec3 anchor() {
		Mob head = PatrolService.headFor(this.mob.level(), this.villageId);
		return head != null ? head.position() : Vec3.atBottomCenterOf(PatrolService.bellFor(this.mob, this.villageId));
	}

	@Override
	public boolean canUse() {
		return !this.mob.isPassenger() && this.mob.position().distanceToSqr(anchor()) > LEASH * LEASH;
	}

	@Override
	public boolean canContinueToUse() {
		return !this.mob.isPassenger() && this.mob.position().distanceToSqr(anchor()) > ARRIVAL * ARRIVAL;
	}

	@Override
	public void start() {
		this.repath = 0;
	}

	@Override
	public void tick() {
		if (this.repath-- > 0) {
			return;
		}
		this.repath = REPATH_TICKS;
		Vec3 anchor = anchor();
		this.mob.getNavigation().moveTo(anchor.x, anchor.y, anchor.z, 1.2);
	}

	@Override
	public void stop() {
		this.mob.getNavigation().stop();
	}
}
