package com.stella.mobs.behavior;

import java.util.List;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * M11: a witch that lets its target or a player come close blinks away, to a safe spot between six
 * and eight blocks from that threat, and keeps its target so the throws resume. A cooldown on the
 * goal's own ticks stops it blinking every tick. No flags.
 */
public class WitchBlinkGoal<T extends Witch> extends Goal {
	public static final double BLINK_TRIGGER_RANGE = 4.0;
	public static final double BLINK_MIN_DISTANCE = 6.0;
	// Inside the throw range with room to spare, so the witch keeps attacking from where it lands.
	public static final double BLINK_RANGE_MARGIN = 2.0;
	public static final double BLINK_MAX_DISTANCE = WitchOffensiveThrowGoal.THROW_RANGE - BLINK_RANGE_MARGIN;
	public static final int BLINK_COOLDOWN_TICKS = 100;
	private static final int ATTEMPTS = 24;
	private static final double HEIGHT_SEARCH = 4.0;

	private final T witch;
	private int cooldown;

	public WitchBlinkGoal(final T witch) {
		this.witch = witch;
	}

	@Override
	public boolean canUse() {
		if (this.cooldown > 0) {
			this.cooldown -= 2;
			return false;
		}

		return this.threat() != null;
	}

	@Override
	public boolean canContinueToUse() {
		return false;
	}

	@Override
	public void start() {
		LivingEntity threat = this.threat();
		if (threat == null) {
			return;
		}

		LivingEntity kept = this.witch.getTarget();
		Vec3 from = threat.position();
		for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
			double angle = this.witch.getRandom().nextDouble() * Math.PI * 2.0;
			double distance = BLINK_MIN_DISTANCE + this.witch.getRandom().nextDouble() * (BLINK_MAX_DISTANCE - BLINK_MIN_DISTANCE);
			double x = from.x + Math.cos(angle) * distance;
			double z = from.z + Math.sin(angle) * distance;
			double y = this.witch.getY() + (this.witch.getRandom().nextDouble() * 2.0 - 1.0) * HEIGHT_SEARCH;
			Vec3 before = this.witch.position();
			if (this.witch.randomTeleport(x, y, z, true, (BlockState forbidden) -> false)) {
				double landed = this.witch.position().distanceTo(threat.position());
				if (landed >= BLINK_MIN_DISTANCE && landed <= BLINK_MAX_DISTANCE) {
					this.witch.setTarget(kept);
					this.cooldown = BLINK_COOLDOWN_TICKS;
					return;
				}

				this.witch.teleportTo(before.x, before.y, before.z);
			}
		}
	}

	/** The witch's own target, or a player that can be hurt, inside the trigger range. */
	private @Nullable LivingEntity threat() {
		double range = BLINK_TRIGGER_RANGE * BLINK_TRIGGER_RANGE;
		LivingEntity target = this.witch.getTarget();
		if (target != null && target.isAlive() && this.witch.distanceToSqr(target) <= range) {
			return target;
		}

		List<Player> players = this.witch.level().getEntitiesOfClass(
			Player.class,
			this.witch.getBoundingBox().inflate(BLINK_TRIGGER_RANGE),
			player -> player.isAlive() && !player.isCreative() && !player.isSpectator() && this.witch.distanceToSqr(player) <= range
		);
		return players.isEmpty() ? null : players.get(0);
	}
}
