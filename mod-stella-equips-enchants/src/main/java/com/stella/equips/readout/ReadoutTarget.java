package com.stella.equips.readout;

import java.util.Collection;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

/**
 * The one mob under the crosshair. Vanilla `Entity.pick` returns blocks only, so the
 * target is the nearest candidate inside the entity interaction range whose direction
 * sits within {@link #TARGET_COS} of the look vector.
 */
public final class ReadoutTarget {
	private static final double TARGET_COS = 0.9;

	private ReadoutTarget() {
	}

	public static Mob of(final ServerPlayer player, final Collection<Mob> candidates) {
		Vec3 eyes = player.getEyePosition();
		Vec3 look = player.getViewVector(1.0F);
		double reach = player.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE);
		Mob nearest = null;
		double nearestDistance = Double.MAX_VALUE;

		for (Mob mob : candidates) {
			Vec3 toMob = mob.getBoundingBox().getCenter().subtract(eyes);
			double distance = toMob.length();

			if (distance <= 0.0 || distance > reach) {
				continue;
			}

			if (look.dot(toMob.scale(1.0 / distance)) < TARGET_COS) {
				continue;
			}

			if (distance < nearestDistance) {
				nearestDistance = distance;
				nearest = mob;
			}
		}

		return nearest;
	}
}
