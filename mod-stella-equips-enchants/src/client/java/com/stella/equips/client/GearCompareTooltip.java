package com.stella.equips.client;

import com.stella.equips.compare.GearCompare;
import java.util.List;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** Adds the roll difference against the equipped item to a tooltip while Shift is held. */
final class GearCompareTooltip {
	private static final String KEY = "tooltip.mod-stella-equips-enchants.compare.";

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

			if (diff.isEmpty()) {
				return;
			}

			if (!minecraft.hasShiftDown()) {
				lines.add(Component.translatable(KEY + "hint").withStyle(ChatFormatting.DARK_GRAY));
				return;
			}

			lines.add(Component.translatable(KEY + "header").withStyle(ChatFormatting.GRAY));

			for (GearCompare.Line line : diff) {
				ChatFormatting color = line.delta() > 0 ? ChatFormatting.GREEN : ChatFormatting.RED;
				lines.add(Component.translatable(KEY + "line", GearCompare.amount(line), Component.translatable(KEY + line.key()))
					.withStyle(color));
			}
		});
	}
}
