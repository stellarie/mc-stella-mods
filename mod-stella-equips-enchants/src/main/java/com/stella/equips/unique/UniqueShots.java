package com.stella.equips.unique;

import com.stella.equips.refine.Refine;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;

/** The two steps of a shot from a unique bow or crossbow: mark the projectile, then call the shot hook. */
final class UniqueShots {
	private UniqueShots() {
	}

	static void mark(final LivingEntity shooter, final ItemStack weapon, final Projectile projectile) {
		if (!(shooter instanceof ServerPlayer) || !(projectile instanceof AbstractArrow arrow)) {
			return;
		}
		UniqueDef def = Uniques.def(weapon);
		if (def != null) {
			UniqueArrows.mark(arrow, def.id(), Refine.level(weapon));
		}
	}

	static void shot(final LivingEntity shooter, final Projectile projectile, final float power) {
		if (!(shooter instanceof ServerPlayer holder) || !(projectile instanceof AbstractArrow arrow)) {
			return;
		}
		UniqueArrows.Mark mark = UniqueArrows.of(arrow);
		UniqueDef def = mark == null ? null : Uniques.byId(mark.id());
		if (def != null) {
			def.effect().onShot(new UniqueContext(holder, weaponOf(def, arrow), mark.refine()), arrow, power);
		}
	}

	/** The stack that loosed an arrow, or a fresh stack of the unique when the arrow forgot it. */
	static ItemStack weaponOf(final UniqueDef def, final AbstractArrow arrow) {
		ItemStack weapon = arrow.getWeaponItem();
		return weapon == null || weapon.isEmpty() ? new ItemStack(Uniques.item(def)) : weapon;
	}
}
