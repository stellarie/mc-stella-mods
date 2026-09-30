package com.stella.equips.readout;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Picks the mob a player looks at, nearest first, with line of sight. */
public final class ReadoutLook {
	private static final double SEARCH_MARGIN = 1.0;

	private ReadoutLook() {
	}

	/** Returns the nearest live mob on the view ray inside `range` blocks, or null. */
	public static Mob find(final ServerPlayer player, final double range) {
		Vec3 from = player.getEyePosition();
		Vec3 ray = player.getViewVector(1.0F).scale(range);
		Vec3 reach = from.add(ray);
		HitResult block = player.level().clip(
			new ClipContext(from, reach, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player)
		);
		Vec3 end = block.getType() == HitResult.Type.MISS ? reach : block.getLocation();
		AABB search = player.getBoundingBox().expandTowards(ray).inflate(SEARCH_MARGIN);
		EntityHitResult hit = ProjectileUtil.getEntityHitResult(
			player, from, end, search, ReadoutLook::isTarget, range * range
		);
		return hit != null && hit.getEntity() instanceof Mob mob ? mob : null;
	}

	private static boolean isTarget(final Entity entity) {
		return entity instanceof Mob mob && mob.isAlive() && !mob.isRemoved() && !mob.isDeadOrDying()
			&& !mob.isSpectator();
	}
}
