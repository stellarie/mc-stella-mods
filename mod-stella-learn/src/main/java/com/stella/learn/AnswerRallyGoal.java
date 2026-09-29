package com.stella.learn;

import com.stella.mobs.behavior.ReactiveState;
import com.stella.mobs.config.CombatRole;
import java.util.EnumSet;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

/** Walks a mob that accepted a rally or a back-off to its slot, and lets go of it on arrival or when the request expires. */
public final class AnswerRallyGoal extends Goal {
	static final double ARRIVED = 2.0;
	private static final int REPATH_TICKS = 10;
	private static final double SPEED = 1.0;

	private final Mob mob;
	private final boolean melee;
	private int untilRepath;

	public AnswerRallyGoal(final Mob mob, final boolean melee) {
		this.mob = mob;
		this.melee = melee;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE));
	}

	@Override
	public boolean canUse() {
		return this.playsThisRole() && this.walking();
	}

	@Override
	public boolean canContinueToUse() {
		return this.walking();
	}

	@Override
	public void start() {
		this.untilRepath = 0;
		Vec3 slot = Rally.slotOf(this.mob);
		CalloutBoard.Active held = CalloutBoard.active(this.mob);
		if (slot != null && held != null) {
			LearnLog.info("learnRally", "WALK", LearnLog.who(this.mob) + " type=" + held.callout().type() + " slot=" + Rally.text(slot)
				+ " distance=" + LearnLog.num(this.mob.position().distanceTo(slot)));
		}
	}

	@Override
	public void stop() {
		this.mob.getNavigation().stop();
		Vec3 slot = Rally.slotOf(this.mob);
		if (slot != null) {
			boolean arrived = this.mob.position().distanceTo(slot) <= ARRIVED;
			LearnLog.info("learnRally", arrived ? "ARRIVE" : "EXPIRE", LearnLog.who(this.mob) + " slot=" + Rally.text(slot)
				+ " distance=" + LearnLog.num(this.mob.position().distanceTo(slot)));
		}
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void tick() {
		Vec3 slot = Rally.slotOf(this.mob);
		if (slot != null && --this.untilRepath <= 0) {
			this.untilRepath = REPATH_TICKS;
			this.mob.getNavigation().moveTo(slot.x, slot.y, slot.z, SPEED);
		}
	}

	private boolean walking() {
		CalloutBoard.Active held = CalloutBoard.active(this.mob);
		Vec3 slot = Rally.slotOf(this.mob);
		return held != null && (held.callout().type().equals(Rally.TYPE) || held.callout().type().equals(CalloutKits.BACK_OFF)) && slot != null && this.mob.position().distanceTo(slot) > ARRIVED;
	}

	private boolean playsThisRole() {
		return (ReactiveState.get(this.mob).getRole() == CombatRole.MELEE) == this.melee;
	}
}
