package com.stella.tower;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/** A wisp: used on a floor of the tower, it lights the way to the portal. Twenty-four uses. */
public class WispItem extends Item {
	public WispItem(final Properties properties) {
		super(properties);
	}

	// Its uses show as a bar, as wear would, though they are its own count and nothing mends them.
	@Override
	public boolean isBarVisible(final net.minecraft.world.item.ItemStack stack) {
		return TowerWisps.usesOf(stack) < TowerWisps.USES;
	}

	@Override
	public int getBarWidth(final net.minecraft.world.item.ItemStack stack) {
		return Math.round(13.0F * TowerWisps.usesOf(stack) / TowerWisps.USES);
	}

	@Override
	public int getBarColor(final net.minecraft.world.item.ItemStack stack) {
		return 0x7FE5FF;
	}

	@Override
	public InteractionResult use(final Level level, final Player player, final InteractionHand hand) {
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		if (!(player instanceof ServerPlayer holder)) {
			return InteractionResult.PASS;
		}
		net.minecraft.world.item.ItemStack wisp = holder.getItemInHand(hand);
		if (holder.getCooldowns().isOnCooldown(wisp) || TowerWisps.usesOf(wisp) <= 0) {
			return InteractionResult.FAIL;
		}
		TowerWisps.Trail trail = TowerWisps.light(holder);
		if (trail == null) {
			holder.sendOverlayMessage(Component.translatable("message.mod-stella-tower.wisp.nowhere").withStyle(ChatFormatting.GRAY));
			return InteractionResult.FAIL;
		}
		holder.getCooldowns().addCooldown(wisp, TowerWisps.COOLDOWN_TICKS);
		if (!holder.hasInfiniteMaterials()) {
			int left = TowerWisps.usesOf(wisp) - 1;
			if (left <= 0) {
				wisp.shrink(1);
			} else {
				TowerWisps.setUses(wisp, left);
			}
		}
		level.playSound(null, holder.blockPosition(), SoundEvents.ALLAY_ITEM_TAKEN, SoundSource.PLAYERS, 1.0F, 1.2F);
		holder.sendOverlayMessage(Component.translatable("message.mod-stella-tower.wisp.lit", trail.path().size() - 1).withStyle(ChatFormatting.AQUA));
		return InteractionResult.SUCCESS;
	}
}
