package com.stella.spells;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

public final class SpellbookItem extends Item {
	public static final int BASE_COOLDOWN_TICKS = 60;

	public SpellbookItem(final Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(final Level level, final Player player, final InteractionHand hand) {
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}

		if (!(player instanceof ServerPlayer serverPlayer)) {
			return InteractionResult.PASS;
		}

		return switch (SpellCasting.cast(serverPlayer, this, hand)) {
			case CAST -> InteractionResult.SUCCESS;
			case COOLDOWN -> InteractionResult.FAIL;
			case NO_TARGET -> InteractionResult.PASS;
		};
	}
}
