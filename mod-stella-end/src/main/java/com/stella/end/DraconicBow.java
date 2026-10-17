package com.stella.end;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * The draconic bow: its arrows hit twice as hard as vanilla arrows, and a shot costs it no wear. It keeps a durability so
 * the equips mod's refine, which only takes gear that can wear down, accepts it.
 */
public class DraconicBow extends BowItem {
	/** The base damage of a vanilla arrow. */
	public static final double VANILLA_ARROW_DAMAGE = 2.0;

	public DraconicBow(final Properties properties) {
		super(properties);
	}

	@Override
	protected Projectile createProjectile(final Level level, final LivingEntity shooter, final ItemStack weapon, final ItemStack projectile, final boolean isCrit) {
		Projectile arrow = super.createProjectile(level, shooter, weapon, projectile, isCrit);
		if (arrow instanceof AbstractArrow shot) {
			shot.setBaseDamage(VANILLA_ARROW_DAMAGE * DraconicGear.ARROW_FACTOR);
		}
		return arrow;
	}

	@Override
	public boolean releaseUsing(final ItemStack bow, final Level level, final LivingEntity shooter, final int remainingTime) {
		int wear = bow.getDamageValue();
		boolean shot = super.releaseUsing(bow, level, shooter, remainingTime);
		if (shot && bow.getDamageValue() > wear) {
			bow.setDamageValue(wear);
		}
		return shot;
	}
}
