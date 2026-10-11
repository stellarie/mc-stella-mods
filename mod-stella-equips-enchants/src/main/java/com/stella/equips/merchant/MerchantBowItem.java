package com.stella.equips.merchant;

import java.util.List;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

/** The Wraithstring. The vanilla draw curve is fixed at 20 ticks, so the release is rebuilt here on the own draw time of the bow. */
public class MerchantBowItem extends BowItem implements MerchantWeapon {
	private final MerchantDef def;

	public MerchantBowItem(final Item.Properties properties, final MerchantDef def) {
		super(properties);
		this.def = def;
	}

	@Override
	public MerchantDef def() {
		return this.def;
	}

	@Override
	public boolean releaseUsing(final ItemStack weapon, final Level level, final LivingEntity entity, final int remaining) {
		if (!(entity instanceof Player player)) {
			return false;
		}
		ItemStack ammo = player.getProjectile(weapon);
		if (ammo.isEmpty()) {
			return false;
		}
		float power = MerchantWeapons.bowPower(weapon, this.getUseDuration(weapon, entity) - remaining);
		if (power < 0.1F) {
			return false;
		}
		List<ItemStack> drawn = draw(weapon, ammo, player);
		if (level instanceof ServerLevel server && !drawn.isEmpty()) {
			this.shoot(server, player, player.getUsedItemHand(), weapon, drawn, power * 3.0F, 1.0F, power == 1.0F, null);
		}
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARROW_SHOOT, SoundSource.PLAYERS, 1.0F,
			1.0F / (level.getRandom().nextFloat() * 0.4F + 1.2F) + power * 0.5F);
		player.awardStat(Stats.ITEM_USED.get(this));
		return true;
	}

	@Override
	protected Projectile createProjectile(final Level level, final LivingEntity shooter, final ItemStack weapon, final ItemStack projectile, final boolean isCrit) {
		Projectile created = super.createProjectile(level, shooter, weapon, projectile, isCrit);
		MerchantWeapons.shape(created, weapon);
		return created;
	}

	@Override
	public void appendHoverText(final ItemStack stack, final Item.TooltipContext context, final TooltipDisplay display, final Consumer<Component> builder,
		final TooltipFlag flag) {
		super.appendHoverText(stack, context, display, builder, flag);
		MerchantWeapons.tooltip(stack).forEach(builder);
	}
}
