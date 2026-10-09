package com.stella.equips.bows;

import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/** A crafted crossbow. Its charge time comes from {@code CraftedCrossbowMixin}, which scales the vanilla charge duration. */
public class CraftedCrossbowItem extends CrossbowItem implements CraftedWeapon {
	private final CraftedBowDef def;

	public CraftedCrossbowItem(final Item.Properties properties, final CraftedBowDef def) {
		super(properties);
		this.def = def;
	}

	@Override
	public CraftedBowDef def() {
		return this.def;
	}

	@Override
	protected Projectile createProjectile(final Level level, final LivingEntity shooter, final ItemStack weapon, final ItemStack projectile, final boolean isCrit) {
		Projectile created = super.createProjectile(level, shooter, weapon, projectile, isCrit);
		CraftedBows.shape(created, weapon);
		return created;
	}

	@Override
	protected void shootProjectile(
		final LivingEntity shooter, final Projectile projectileEntity, final int index, final float power, final float uncertainty, final float angle,
		final @Nullable LivingEntity targetOverride
	) {
		super.shootProjectile(shooter, projectileEntity, index, power * (float) this.def.speed(), uncertainty, angle, targetOverride);
	}

	@Override
	public void appendHoverText(final ItemStack stack, final Item.TooltipContext context, final TooltipDisplay display, final Consumer<Component> builder,
		final TooltipFlag flag) {
		super.appendHoverText(stack, context, display, builder, flag);
		builder.accept(CraftedBows.effectText(stack));
	}
}
