package com.stella.equips.bows;

import com.stella.equips.tooltip.DescriptionLines;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/** A crafted bow. The vanilla draw curve is fixed at 20 ticks, so the release is rebuilt here on the bow's own draw time. */
public class CraftedBowItem extends BowItem implements CraftedWeapon {
	private final CraftedBowDef def;

	public CraftedBowItem(final Item.Properties properties, final CraftedBowDef def) {
		super(properties);
		this.def = def;
	}

	@Override
	public CraftedBowDef def() {
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
		float power = CraftedBows.power(weapon, this.getUseDuration(weapon, entity) - remaining);
		if (power < 0.1F) {
			return false;
		}
		boolean kept = !player.hasInfiniteMaterials() && CraftedBows.rollKeep(player, weapon);
		// A kept arrow is drawn from a copy, so the inventory stack is never touched.
		List<ItemStack> drawn = draw(weapon, kept ? ammo.copy() : ammo, player);
		if (kept) {
			drawn.forEach(arrow -> arrow.set(DataComponents.INTANGIBLE_PROJECTILE, Unit.INSTANCE));
		}
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
		DescriptionLines.addBlock(builder, Component.literal("bow in hand").withStyle(ChatFormatting.WHITE), CraftedBows.effectText(stack));
	}
}
