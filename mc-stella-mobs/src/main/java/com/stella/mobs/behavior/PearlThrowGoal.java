package com.stella.mobs.behavior;

import com.stella.mobs.StellaMobs;
import java.util.Locale;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * R25: every elite throws an ender pearl into the player's area when it needs to be there. The arrival
 * is vanilla, because ThrownEnderpearl.onHit already teleports a mob owner. The mod supplies the aim,
 * the trigger, and the throw budget. The goal claims no flags, so it can never tie a rank.
 */
public class PearlThrowGoal extends Goal {
	public static final int PEARL_COUNT = 3;
	public static final double MIN_DISTANCE = 8.0;
	public static final int COOLDOWN_TICKS = 100;
	private static final double MAX_DISTANCE = 64.0;
	private static final float POWER = 1.6F;
	private static final float UNCERTAINTY = 0.5F;
	private final Mob elite;
	private long nextThrowTick;

	public PearlThrowGoal(final Mob elite) {
		this.elite = elite;
	}

	@Override
	public boolean canUse() {
		if (!Elite.isElite(this.elite) || EliteState.pearlsLeft(this.elite) <= 0) {
			return false;
		}

		LivingEntity target = this.elite.getTarget();
		if (target == null || !target.isAlive()) {
			return false;
		}

		double distance = this.elite.distanceTo(target);
		return distance >= MIN_DISTANCE
			&& distance <= MAX_DISTANCE
			&& this.elite.level().getGameTime() >= this.nextThrowTick;
	}

	@Override
	public void start() {
		LivingEntity target = this.elite.getTarget();
		if (target == null || !(this.elite.level() instanceof ServerLevel level)) {
			return;
		}

		this.nextThrowTick = level.getGameTime() + COOLDOWN_TICKS;
		int left = EliteState.pearlsLeft(this.elite) - 1;
		EliteState.setPearlsLeft(this.elite, left);
		ItemStack pearlItem = new ItemStack(Items.ENDER_PEARL);
		ThrownEnderpearl pearl = new ThrownEnderpearl(level, this.elite, pearlItem);
		double xd = target.getX() - this.elite.getX();
		double yd = target.getEyeY() - pearl.getY();
		double zd = target.getZ() - this.elite.getZ();
		Projectile.spawnProjectileUsingShoot(pearl, level, pearlItem, xd, yd, zd, POWER, UNCERTAINTY);
		StellaMobs.LOGGER.info(
			"[BEHAVIOR] test=elitePearl t={} THROW elite={} pearlsLeft={} distance={} aim={},{},{}",
			level.getGameTime(),
			this.elite.getType(),
			left,
			String.format(Locale.ROOT, "%.2f", this.elite.distanceTo(target)),
			String.format(Locale.ROOT, "%.2f", xd),
			String.format(Locale.ROOT, "%.2f", yd),
			String.format(Locale.ROOT, "%.2f", zd)
		);
	}

	@Override
	public boolean canContinueToUse() {
		return false;
	}
}
