package com.stella.spells;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/** A single-use scroll of one spell: it casts like the spell's book and crumbles when the spell lands. */
public final class SpellScrollItem extends Item {
	private final SpellDefinition definition;

	public SpellScrollItem(final Properties properties, final SpellDefinition definition) {
		super(properties);
		this.definition = definition;
	}

	public SpellDefinition definition() {
		return this.definition;
	}

	@Override
	public InteractionResult use(final Level level, final Player player, final InteractionHand hand) {
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}

		if (!(player instanceof ServerPlayer serverPlayer)) {
			return InteractionResult.PASS;
		}

		return switch (SpellCasting.castScroll(serverPlayer, this, hand)) {
			case CAST -> InteractionResult.SUCCESS;
			case COOLDOWN -> InteractionResult.FAIL;
			case NO_TARGET -> InteractionResult.PASS;
		};
	}
}
