package com.stella.spells;

import java.util.List;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Holds any number of spell and skill books, imbued at an anvil like a weapon's spells. Use casts every stored
 * entry at once, each with its normal rules. The Compendium then rests; the stored books' own cooldowns neither
 * block this cast nor start.
 */
public final class CompendiumItem extends Item {
	public static final int COOLDOWN_TICKS = 600;

	public CompendiumItem(final Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(final Level level, final Player player, final InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (level.isClientSide()) {
			return SpellImbue.spellsOf(stack).isEmpty() ? InteractionResult.PASS : InteractionResult.SUCCESS;
		}
		if (!(player instanceof ServerPlayer caster) || player.getCooldowns().isOnCooldown(stack)) {
			return InteractionResult.PASS;
		}

		List<Identifier> stored = SpellImbue.spellsOf(stack);
		boolean cast = false;
		for (Identifier spell : stored) {
			SpellDefinition definition = SpellbookCatalog.find(spell);
			if (definition != null) {
				cast |= SpellCasting.castStored(caster, definition, stack);
			}
		}
		if (!cast) {
			return InteractionResult.PASS;
		}
		player.getCooldowns().addCooldown(stack, COOLDOWN_TICKS);
		return InteractionResult.SUCCESS;
	}
}
