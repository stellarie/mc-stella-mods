package com.stella.tower;

import com.stella.tower.mixin.AbstractArrowInvoker;
import net.minecraft.core.Direction;
import net.minecraft.core.Position;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.SpectralArrow;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/** A spectral arrow that goes on through {@value #PIERCE} bodies after the first. */
public class PhantomArrowItem extends ArrowItem {
	public static final byte PIERCE = 2;

	public PhantomArrowItem(final Properties properties) {
		super(properties);
	}

	@Override
	public AbstractArrow createArrow(final Level level, final ItemStack itemStack, final LivingEntity owner, final @Nullable ItemStack firedFromWeapon) {
		SpectralArrow arrow = new SpectralArrow(level, owner, itemStack.copyWithCount(1), firedFromWeapon);
		((AbstractArrowInvoker) arrow).stellaTower$setPierceLevel(PIERCE);
		return arrow;
	}

	@Override
	public Projectile asProjectile(final Level level, final Position position, final ItemStack itemStack, final Direction direction) {
		SpectralArrow arrow = new SpectralArrow(level, position.x(), position.y(), position.z(), itemStack.copyWithCount(1), null);
		arrow.pickup = AbstractArrow.Pickup.ALLOWED;
		((AbstractArrowInvoker) arrow).stellaTower$setPierceLevel(PIERCE);
		return arrow;
	}
}
