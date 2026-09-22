package com.stella.equips;

import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Arrow with the protected ground flag exposed, so a probe can wait for the hit. */
class ProbeArrow extends Arrow {
	ProbeArrow(
		final Level level,
		final double x,
		final double y,
		final double z,
		final ItemStack pickupItemStack,
		final ItemStack firedFromWeapon
	) {
		super(level, x, y, z, pickupItemStack, firedFromWeapon);
	}

	boolean stuckInGround() {
		return this.isInGround();
	}
}
