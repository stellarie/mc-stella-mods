package com.stella.spells;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public final class SpellbookItem extends Item {
	public static final int BASE_COOLDOWN_TICKS = 60;
	private final SpellDefinition definition;

	public SpellbookItem(final Properties properties, final SpellDefinition definition) {
		super(properties);
		this.definition = definition;
	}

	public SpellDefinition definition() {
		return this.definition;
	}

	@Override
	public Component getName(final ItemStack stack) {
		Component base = super.getName(stack);
		return Awakening.isAwakened(stack) ? Component.translatable("item." + Spellbooks.MOD_ID + ".awakened", base) : base;
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
			case FIZZLE -> InteractionResult.SUCCESS;
		};
	}
}
