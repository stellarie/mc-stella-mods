package com.stella.end;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A mob that has just been hurt blinks away from the blow, then waits {@link #COOLDOWN} ticks on the End clock before it
 * will blink again. The goal reads the hurt timer of the mob, which counts down from the moment of the blow.
 */
public final class HurtTeleportGoal extends Goal {
	public static final int COOLDOWN = 600;
	public static final double MIN_DISTANCE = 8.0;
	public static final double MAX_DISTANCE = 16.0;
	private static final int RETRY = 20;

	private final Mob mob;
	private final List<TeleportChaseGoal.Hop> hops = new ArrayList<>();
	private long nextAt;
	private @Nullable Vec3 spot;

	public HurtTeleportGoal(final Mob mob) {
		this.mob = mob;
	}

	public List<TeleportChaseGoal.Hop> hops() {
		return hops;
	}

	@Override
	public boolean canUse() {
		if (!(mob.level() instanceof ServerLevel level) || mob.hurtTime <= 0) {
			return false;
		}
		long now = EndClock.now(level);
		if (now < nextAt) {
			return false;
		}
		spot = CastleTeleport.away(level, mob.getRootVehicle(), mob.getLastHurtByMob(), MIN_DISTANCE, MAX_DISTANCE);
		if (spot == null) {
			nextAt = now + RETRY;
		}
		return spot != null;
	}

	@Override
	public boolean canContinueToUse() {
		return false;
	}

	@Override
	public void start() {
		ServerLevel level = (ServerLevel) mob.level();
		Vec3 from = mob.getRootVehicle().position();
		CastleTeleport.jump(level, mob.getRootVehicle(), spot);
		long now = EndClock.now(level);
		nextAt = now + COOLDOWN;
		hops.add(new TeleportChaseGoal.Hop(now, from, spot));
		BehaviorLog.info("[END] hurtTeleport mob={} clock={} from={} to={}", mob.getType().getDescriptionId(), now, from, spot);
	}
}
