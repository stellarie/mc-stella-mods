package com.stella.equips.client;

import com.stella.equips.compare.GearCompare;
import java.util.List;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

/** Adds the roll difference against the equipped item to a tooltip while Shift is held. */
final class GearCompareTooltip {
	private GearCompareTooltip() {
	}

	static void register() {
		ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> {
			Minecraft minecraft = Minecraft.getInstance();

			if (minecraft.player == null) {
				return;
			}

			ItemStack equipped = minecraft.player.getItemBySlot(GearCompare.slotOf(stack));
			List<GearCompare.Line> diff = GearCompare.compare(stack, equipped);
			lines.addAll(GearCompare.tooltipLines(diff, minecraft.hasShiftDown()));
		});
	}
}
