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

	@Override
	public InteractionResult use(final Level level, final Player player, final InteractionHand hand) {
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		if (!(player instanceof ServerPlayer holder)) {
			return InteractionResult.PASS;
		}
		TowerWisps.Trail trail = TowerWisps.light(holder);
		if (trail == null) {
			holder.sendOverlayMessage(Component.translatable("message.mod-stella-tower.wisp.nowhere").withStyle(ChatFormatting.GRAY));
			return InteractionResult.FAIL;
		}
		holder.getItemInHand(hand).hurtAndBreak(1, holder, hand);
		level.playSound(null, holder.blockPosition(), SoundEvents.ALLAY_ITEM_TAKEN, SoundSource.PLAYERS, 1.0F, 1.2F);
		holder.sendOverlayMessage(Component.translatable("message.mod-stella-tower.wisp.lit", trail.path().size() - 1).withStyle(ChatFormatting.AQUA));
		return InteractionResult.SUCCESS;
	}
}
