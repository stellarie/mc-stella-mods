package com.stella.end;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A mob that blinks next to a target more than {@link #MIN_DISTANCE} blocks away, then waits {@link #COOLDOWN} ticks.
 * The wait runs on the End clock, which a game test moves on. A mounted mob takes its mount along. Every blink is
 * recorded, so a test reads what happened and not what was meant to.
 */
public final class TeleportChaseGoal extends Goal {
	public static final double MIN_DISTANCE = 8.0;
	public static final int COOLDOWN = 160;
	/** Ticks to wait before looking for a landing again, after a search turned up none. */
	private static final int RETRY = 20;

	/** One blink: the End clock when it happened, where it began and where it ended. */
	public record Hop(long clock, Vec3 from, Vec3 to) {
	}

	private final Mob mob;
	private final double minDistance;
	private final int cooldown;
	private final List<Hop> hops = new ArrayList<>();
	private long nextAt;
	private @Nullable Vec3 spot;

	public TeleportChaseGoal(final Mob mob) {
		this(mob, MIN_DISTANCE, COOLDOWN);
	}

	public TeleportChaseGoal(final Mob mob, final double minDistance, final int cooldown) {
		this.mob = mob;
		this.minDistance = minDistance;
		this.cooldown = cooldown;
	}

	public List<Hop> hops() {
		return hops;
	}

	@Override
	public boolean canUse() {
		LivingEntity target = mob.getTarget();
		if (!(mob.level() instanceof ServerLevel level) || target == null || !target.isAlive() || mob.distanceTo(target) <= minDistance) {
			return false;
		}
		long now = EndClock.now(level);
		if (now < nextAt) {
			return false;
		}
		spot = CastleTeleport.near(level, mob.getRootVehicle(), target);
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
		nextAt = now + cooldown;
		hops.add(new Hop(now, from, spot));
		BehaviorLog.info("[END] teleportChase mob={} clock={} from={} to={} target={}", mob.getType().getDescriptionId(), now, from, spot, mob.getTarget());
	}
}
