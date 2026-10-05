package com.stella.equips.unique.mp;

import com.stella.equips.unique.UniqueContext;
import com.stella.equips.unique.Uniques;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** The wards a priest unique lays on the players around its wearer. */
public final class PriestWards {
	private PriestWards() {
	}

	/** The context of every player wearing the unique within the radius of a point. */
	public static List<UniqueContext> wearers(final ServerLevel level, final Vec3 center, final double radius, final String id) {
		List<UniqueContext> found = new ArrayList<>();
		double reach = radius * radius;
		for (ServerPlayer player : level.players()) {
			if (!player.isAlive() || player.isSpectator() || player.position().distanceToSqr(center) > reach) {
				continue;
			}
			for (Uniques.Active active : Uniques.active(player)) {
				if (id.equals(active.def().id())) {
					found.add(active.context());
				}
			}
		}
		return found;
	}

	/** True when at least one wearer of the unique stands within the radius of the point. */
	public static boolean warded(final ServerLevel level, final Vec3 center, final double radius, final String id, final int atLeast) {
		for (UniqueContext wearer : wearers(level, center, radius, id)) {
			if (wearer.atLeast(atLeast)) {
				return true;
			}
		}
		return false;
	}

	/** The living attacker of a melee blow, or null for a projectile, a spell, fire or any other damage. */
	public static LivingEntity meleeAttacker(final DamageSource source) {
		return source.getEntity() instanceof LivingEntity attacker && source.getDirectEntity() == attacker ? attacker : null;
	}
}
