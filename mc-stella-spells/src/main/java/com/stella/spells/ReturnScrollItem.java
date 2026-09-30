package com.stella.spells;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.TeleportTransition;

/**
 * A Scroll of Return: read it and it carries you home, to your respawn point in the Overworld. That is
 * your bed when you have one there, and the world spawn otherwise, from whatever dimension you read it
 * in. A respawn anchor is never charged by it. It crumbles when read and rests before the next.
 */
public final class ReturnScrollItem extends Item {
	public static final int COOLDOWN_TICKS = 100;

	public ReturnScrollItem(final Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(final Level level, final Player player, final InteractionHand hand) {
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		if (!(player instanceof ServerPlayer reader)) {
			return InteractionResult.PASS;
		}

		ItemStack scroll = reader.getItemInHand(hand);
		// Vanilla's own respawn search, without spending an anchor's charge; home is always the Overworld.
		TeleportTransition home = reader.findRespawnPositionAndUseSpawnBlock(false, TeleportTransition.DO_NOTHING);
		if (home.newLevel().dimension() != Level.OVERWORLD) {
			home = TeleportTransition.createDefault(reader, TeleportTransition.DO_NOTHING);
		}

		level.playSound(null, reader.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8F, 1.2F);
		reader.getCooldowns().addCooldown(scroll, COOLDOWN_TICKS);
		scroll.consume(1, reader);
		reader.teleport(home);
		home.newLevel().playSound(null, reader.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8F, 1.2F);
		reader.sendSystemMessage(Component.translatable("spellbook." + Spellbooks.MOD_ID + ".returned").withStyle(ChatFormatting.AQUA), true);
		Spellbooks.LOGGER.info("[SPELL] return_scroll player={} to={} at={}", reader.getUUID(), home.newLevel().dimension().identifier(), reader.blockPosition());
		return InteractionResult.SUCCESS;
	}
}
