package com.stella.equips.unique;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/** A bow unique. It marks the arrows it looses and calls the shot hook of its effect. */
public class UniqueBowItem extends BowItem {
	public UniqueBowItem(final Item.Properties properties) {
		super(properties);
	}

	@Override
	protected Projectile createProjectile(final Level level, final LivingEntity shooter, final ItemStack weapon, final ItemStack projectile, final boolean isCrit) {
		Projectile created = super.createProjectile(level, shooter, weapon, projectile, isCrit);
		UniqueShots.mark(shooter, weapon, created);
		return created;
	}

	@Override
	protected void shootProjectile(
		final LivingEntity shooter, final Projectile projectileEntity, final int index, final float power, final float uncertainty, final float angle,
		final @Nullable LivingEntity targetOverride
	) {
		super.shootProjectile(shooter, projectileEntity, index, power, uncertainty, angle, targetOverride);
		UniqueShots.shot(shooter, projectileEntity, power);
	}
}
