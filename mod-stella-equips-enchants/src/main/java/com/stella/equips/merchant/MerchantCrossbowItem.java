package com.stella.equips.merchant;

import com.stella.equips.tooltip.DescriptionLines;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * The Hexbolt. A loaded arrow becomes three bolts, which leave in a fan and burst on impact. The extra two are
 * intangible, so one shot costs one arrow and leaves nothing to pick up.
 */
public class MerchantCrossbowItem extends CrossbowItem implements MerchantWeapon {
	/** The bolts of the shot in flight, so the fan applies to a loaded shot of three and to nothing else. */
	private static final ThreadLocal<Integer> SHOT_BOLTS = new ThreadLocal<>();
	private final MerchantDef def;

	public MerchantCrossbowItem(final Item.Properties properties, final MerchantDef def) {
		super(properties);
		this.def = def;
	}

	@Override
	public MerchantDef def() {
		return this.def;
	}

	// This game version loads a crossbow in its use tick once the charge is whole. Every path that loads runs the same fix.
	@Override
	public void onUseTick(final Level level, final LivingEntity entity, final ItemStack weapon, final int remaining) {
		super.onUseTick(level, entity, weapon, remaining);
		MerchantWeapons.loadBolts(weapon);
	}

	@Override
	public boolean releaseUsing(final ItemStack weapon, final Level level, final LivingEntity entity, final int remaining) {
		boolean result = super.releaseUsing(weapon, level, entity, remaining);
		MerchantWeapons.loadBolts(weapon);
		return result;
	}

	@Override
	public void performShooting(
		final Level level, final LivingEntity shooter, final InteractionHand hand, final ItemStack weapon, final float power, final float uncertainty,
		final @Nullable LivingEntity target
	) {
		MerchantWeapons.loadBolts(weapon);
		SHOT_BOLTS.set(MerchantWeapons.boltCount(weapon));
		try {
			super.performShooting(level, shooter, hand, weapon, power, uncertainty, target);
		} finally {
			SHOT_BOLTS.remove();
		}
	}

	@Override
	protected Projectile createProjectile(final Level level, final LivingEntity shooter, final ItemStack weapon, final ItemStack projectile, final boolean isCrit) {
		Projectile created = super.createProjectile(level, shooter, weapon, projectile, isCrit);
		MerchantWeapons.shape(created, weapon);
		return created;
	}

	@Override
	protected void shootProjectile(
		final LivingEntity shooter, final Projectile projectileEntity, final int index, final float power, final float uncertainty, final float angle,
		final @Nullable LivingEntity targetOverride
	) {
		Integer bolts = SHOT_BOLTS.get();
		float spread = bolts != null && bolts == MerchantWeapons.BOLTS ? MerchantWeapons.fanAngle(index) : angle;
		super.shootProjectile(shooter, projectileEntity, index, power, uncertainty, spread, targetOverride);
	}

	@Override
	public void appendHoverText(final ItemStack stack, final Item.TooltipContext context, final TooltipDisplay display, final Consumer<Component> builder,
		final TooltipFlag flag) {
		super.appendHoverText(stack, context, display, builder, flag);
		MerchantWeapons.tooltip(stack).forEach(line -> DescriptionLines.add(builder, line));
	}
}
