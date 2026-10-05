package com.stella.spells;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.client.Minecraft;

/** Swaps a spell tooltip's static numbers for the ones the local player's own cast would produce now. */
public final class SpellTooltipClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> {
			SpellTooltip.apply(Minecraft.getInstance().player, stack, lines);
			lines.addAll(SpellTooltip.awakeningLines(stack));
		});
	}
}
